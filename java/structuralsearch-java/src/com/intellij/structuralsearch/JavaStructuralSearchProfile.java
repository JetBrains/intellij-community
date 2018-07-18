// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.structuralsearch.impl.matcher.*;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.JavaCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.predicates.ExprTypePredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.FormalArgTypePredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ParameterInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementBuilder;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaStructuralSearchProfile extends StructuralSearchProfile {

  private static final Set<String> PRIMITIVE_TYPES = new THashSet<>(Arrays.asList(
    PsiKeyword.SHORT, PsiKeyword.BOOLEAN,
    PsiKeyword.DOUBLE, PsiKeyword.LONG,
    PsiKeyword.INT, PsiKeyword.FLOAT,
    PsiKeyword.CHAR, PsiKeyword.BYTE
  ));

  @Override
  public String getText(PsiElement match, int start, int end) {
    if (match instanceof PsiIdentifier) {
      final PsiElement parent = match.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement && !(parent instanceof PsiExpression)) {
        final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)parent;
        final String text = referenceElement.getText();
        if (end != -1) {
          return text.substring(start, end);
        }
        final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList != null) {
          // get text without type parameters
          return text.substring(start, parameterList.getStartOffsetInParent());
        }
        return text;
      }
    }
    final String matchText = match.getText();
    if (start == 0 && end == -1) return matchText;
    return matchText.substring(start, (end == -1) ? matchText.length() : end);
  }

  @Override
  @NotNull
  public String getTypedVarString(final PsiElement element) {
    String text;

    if (element instanceof PsiNamedElement) {
      text = ((PsiNamedElement)element).getName();
    }
    else if (element instanceof PsiAnnotation) {
      PsiJavaCodeReferenceElement referenceElement = ((PsiAnnotation)element).getNameReferenceElement();
      text = referenceElement == null ? null : referenceElement.getQualifiedName();
    }
    else if (element instanceof PsiNameValuePair) {
      text = ((PsiNameValuePair)element).getName();
    }
    else {
      text = element.getText();
      if (StringUtil.startsWithChar(text, '@')) {
        text = text.substring(1);
      }
      if (StringUtil.endsWithChar(text, ';')) text = text.substring(0, text.length() - 1);
      else if (element instanceof PsiExpressionStatement) {
        int i = text.indexOf(';');
        if (i != -1) text = text.substring(0, i);
      }
    }

    if (text==null) text = element.getText();

    return text;
  }

  @Override
  public String getMeaningfulText(PsiElement element) {
    if (element instanceof PsiReferenceExpression &&
        ((PsiReferenceExpression)element).getQualifierExpression() != null) {
      final PsiElement resolve = ((PsiReferenceExpression)element).resolve();
      if (resolve instanceof PsiClass) return element.getText();

      final PsiElement referencedElement = ((PsiReferenceExpression)element).getReferenceNameElement();
      String text = referencedElement != null ? referencedElement.getText() : "";

      if (resolve == null && text.length() > 0 && Character.isUpperCase(text.charAt(0))) {
        return element.getText();
      }
      return text;
    }
    return super.getMeaningfulText(element);
  }

  @Override
  public PsiElement updateCurrentNode(PsiElement targetNode) {
    if (targetNode instanceof PsiCodeBlock && ((PsiCodeBlock)targetNode).getStatementCount() == 1) {
      PsiElement targetNodeParent = targetNode.getParent();
      if (targetNodeParent instanceof PsiBlockStatement) {
        targetNodeParent = targetNodeParent.getParent();
      }

      if (targetNodeParent instanceof PsiIfStatement || targetNodeParent instanceof PsiLoopStatement) {
        targetNode = targetNodeParent;
      }
    }
    return targetNode;
  }

  @Override
  public PsiElement extendMatchedByDownUp(PsiElement targetNode) {
    if (targetNode instanceof PsiIdentifier) {
      targetNode = targetNode.getParent();
      final PsiElement parent = targetNode.getParent();
      if (parent instanceof PsiTypeElement || parent instanceof PsiStatement) targetNode = parent;
    }
    return targetNode;
  }

  @Override
  public PsiElement extendMatchOnePsiFile(PsiElement file) {
    if (file instanceof PsiIdentifier) {
      // Searching in previous results
      file = file.getParent();
    }
    return file;
  }

  @NotNull
  @Override
  public PsiElement getPresentableElement(PsiElement element) {
    element = super.getPresentableElement(element);
    if (element instanceof PsiReferenceExpression) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        return parent;
      }
    }
    else if (element instanceof PsiJavaCodeReferenceElement) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiTypeElement || parent instanceof PsiNewExpression || parent instanceof PsiAnnotation) {
        return parent;
      }
    }
    return element;
  }

  @Override
  public void compile(PsiElement[] elements, @NotNull GlobalCompilingVisitor globalVisitor) {
    new JavaCompilingVisitor(globalVisitor).compile(elements);
  }

  @Override
  @NotNull
  public PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor) {
    return new JavaMatchingVisitor(globalVisitor);
  }

  @NotNull
  @Override
  public NodeFilter getLexicalNodesFilter() {
    return element -> isLexicalNode(element);
  }

  private static boolean isLexicalNode(PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      return true;
    }
    else if (element instanceof PsiJavaToken) {
      // do not filter out type keyword of new primitive arrays (e.g. int in new int[10])
      return !(element instanceof PsiKeyword &&
               PRIMITIVE_TYPES.contains(element.getText()) &&
               element.getParent() instanceof PsiNewExpression);
    }
    return false;
  }

  @Override
  @NotNull
  public CompiledPattern createCompiledPattern() {
    return new JavaCompiledPattern();
  }

  @Override
  public List<MatchPredicate> getCustomPredicates(MatchVariableConstraint constraint, String name, MatchOptions options) {
    final List<MatchPredicate> result = new SmartList<>();

    if (!StringUtil.isEmptyOrSpaces(constraint.getNameOfExprType())) {
      final MatchPredicate predicate = new ExprTypePredicate(
        constraint.getNameOfExprType(),
        name,
        constraint.isExprTypeWithinHierarchy(),
        options.isCaseSensitiveMatch(),
        constraint.isPartOfSearchResults()
      );
      result.add(constraint.isInvertExprType() ? new NotPredicate(predicate) : predicate);
    }

    if (!StringUtil.isEmptyOrSpaces(constraint.getNameOfFormalArgType())) {
      final MatchPredicate predicate = new FormalArgTypePredicate(
        constraint.getNameOfFormalArgType(),
        name,
        constraint.isFormalArgTypeWithinHierarchy(),
        options.isCaseSensitiveMatch(),
        constraint.isPartOfSearchResults()
      );
      result.add(constraint.isInvertFormalType() ? new NotPredicate(predicate) : predicate);
    }
    return result;
  }

  @Override
  public boolean isMyLanguage(@NotNull Language language) {
    return language == JavaLanguage.INSTANCE;
  }

  @Override
  public StructuralReplaceHandler getReplaceHandler(@NotNull ReplacementContext context) {
    return new JavaReplaceHandler(context);
  }

  @NotNull
  @Override
  public PsiElement[] createPatternTree(@NotNull String text,
                                        @NotNull PatternTreeContext context,
                                        @NotNull FileType fileType,
                                        @Nullable Language language,
                                        String contextName, @Nullable String extension,
                                        @NotNull Project project,
                                        boolean physical) {
    if (physical) {
      throw new UnsupportedOperationException(getClass() + " cannot create physical PSI");
    }
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    if (context == PatternTreeContext.Block) {
      final PsiCodeBlock codeBlock = elementFactory.createCodeBlockFromText("{\n" + text + "\n}", null);
      PsiElement element = codeBlock.getFirstBodyElement();
      if (element == null) return PsiElement.EMPTY_ARRAY;
      final List<PsiElement> result = new SmartList<>();
      final PsiElement lastBodyElement = codeBlock.getLastBodyElement();
      while (element != null) {
        if (!(element instanceof PsiWhiteSpace)) result.add(element);
        if (element == lastBodyElement) break;
        element = element.getNextSibling();
      }
      if (result.isEmpty()) return PsiElement.EMPTY_ARRAY;

      if (shouldTryExpressionPattern(result)) {
        try {
          final PsiElement[] expressionPattern =
            createPatternTree(text, PatternTreeContext.Expression, fileType, language, contextName, extension, project, false);
          if (expressionPattern.length == 1) {
            return expressionPattern;
          }
        } catch (IncorrectOperationException ignore) {}
      }
      else if (shouldTryClassPattern(result)) {
        final PsiElement[] classPattern =
          createPatternTree(text, PatternTreeContext.Class, fileType, language, contextName, extension, project, false);
        if (classPattern.length == 1) {
          return classPattern;
        }
      }
      return result.toArray(PsiElement.EMPTY_ARRAY);
    }
    else if (context == PatternTreeContext.Class) {
      final PsiClass clazz = elementFactory.createClassFromText(text, null);
      PsiElement startChild = clazz.getLBrace();
      if (startChild != null) startChild = startChild.getNextSibling();

      PsiElement endChild = clazz.getRBrace();
      if (endChild != null) endChild = endChild.getPrevSibling();
      if (startChild == endChild) return PsiElement.EMPTY_ARRAY; // nothing produced

      assert startChild != null;
      final List<PsiElement> result = new SmartList<>();
      for (PsiElement element = startChild.getNextSibling(); element != endChild && element != null; element = element.getNextSibling()) {
        result.add(element);
      }

      return PsiUtilCore.toPsiElementArray(result);
    }
    else if (context == PatternTreeContext.Expression) {
      return new PsiElement[] {elementFactory.createExpressionFromText(text, null)};
    }
    else {
      return PsiFileFactory.getInstance(project).createFileFromText("__dummy.java", JavaFileType.INSTANCE, text).getChildren();
    }
  }

  private static boolean shouldTryExpressionPattern(List<PsiElement> elements) {
    if (elements.size() >= 1 && elements.size() <= 3) {
      final PsiElement firstElement = elements.get(0);
      if (firstElement instanceof PsiDeclarationStatement) {
        final PsiElement lastChild = firstElement.getLastChild();
        if (lastChild instanceof PsiErrorElement && PsiTreeUtil.prevLeaf(lastChild) instanceof PsiErrorElement) {
          // Because an identifier followed by < (less than) is parsed as the start of a declaration
          // in com.intellij.lang.java.parser.StatementParser.parseStatement() line 236
          // but it could just be a comparison
          return true;
        }
      }
    }
    return false;
  }

  private static boolean shouldTryClassPattern(List<PsiElement> elements) {
    if (elements.size() < 2) {
      return false;
    }
    final PsiElement firstElement = elements.get(0);
    final PsiElement secondElement = elements.get(1);
    final PsiElement lastElement = elements.get(elements.size() - 1);

    if (firstElement instanceof PsiDocComment) {
      // might be method with javadoc
      return true;
    }
    else if (firstElement instanceof PsiDeclarationStatement && PsiTreeUtil.lastChild(firstElement) instanceof PsiErrorElement) {
      // might be method or static initializer
      return true;
    }
    else if (firstElement instanceof PsiErrorElement &&
             secondElement instanceof PsiExpressionStatement &&
             PsiTreeUtil.lastChild(secondElement) instanceof PsiErrorElement) {
      // might be generic method
      return true;
    }
    else if (firstElement instanceof PsiExpressionStatement && firstElement.getFirstChild() instanceof PsiMethodCallExpression &&
      firstElement.getLastChild() instanceof PsiErrorElement && lastElement instanceof PsiBlockStatement) {
      // might be constructor
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  public Editor createEditor(@NotNull SearchContext searchContext,
                             @NotNull FileType fileType,
                             Language dialect,
                             String text,
                             boolean useLastConfiguration) {
    // provides autocompletion

    PsiElement element = searchContext.getFile();

    final Project project = searchContext.getProject();
    if (element != null && !useLastConfiguration) {
      final Editor selectedEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();

      if (selectedEditor != null) {
        int caretPosition = selectedEditor.getCaretModel().getOffset();
        PsiElement positionedElement = searchContext.getFile().findElementAt(caretPosition);

        if (positionedElement == null) {
          positionedElement = searchContext.getFile().findElementAt(caretPosition + 1);
        }

        if (positionedElement != null) {
          element = PsiTreeUtil.getParentOfType(
            positionedElement,
            PsiClass.class, PsiCodeBlock.class
          );
        }
      }
    }

    final PsiCodeFragment file = createCodeFragment(project, text, element);
    final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
    assert doc != null;
    DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file, false);
    return UIUtil.createEditor(doc, project, true, true, getTemplateContextType());
  }

  @NotNull
  @Override
  public Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return JavaCodeContextType.class;
  }

  @NotNull
  @Override
  public PsiCodeFragment createCodeFragment(Project project, String text, PsiElement context) {
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    return factory.createCodeBlockCodeFragment(text, context, true);
  }

  @Override
  public void checkSearchPattern(CompiledPattern pattern) {
    final ValidatingVisitor visitor = new ValidatingVisitor();
    final NodeIterator nodes = pattern.getNodes();
    if (pattern.getNodeCount() == 1 && (
      nodes.current() instanceof PsiExpressionStatement || nodes.current() instanceof PsiDeclarationStatement)) {
      visitor.setCurrent(nodes.current());
    }
    while (nodes.hasNext()) {
      nodes.current().accept(visitor);
      nodes.advance();
    }
    nodes.reset();
  }

  @Override
  public void checkReplacementPattern(Project project, ReplaceOptions options) {
    final MatchOptions matchOptions = options.getMatchOptions();
    final FileType fileType = matchOptions.getFileType();
    final PsiElement[] statements = createPatternTree(matchOptions.getSearchPattern(), PatternTreeContext.Block, fileType, project, false);
    final boolean searchIsExpression = statements.length == 1 && statements[0].getLastChild() instanceof PsiErrorElement;

    final PsiElement[] statements2 = createPatternTree(options.getReplacement(), PatternTreeContext.Block, fileType, project, false);
    final boolean replaceIsExpression = statements2.length == 1 && statements2[0].getLastChild() instanceof PsiErrorElement;

    final ValidatingVisitor visitor = new ValidatingVisitor();
    if (statements2.length == 1 && (statements2[0] instanceof PsiExpressionStatement || statements2[0] instanceof PsiDeclarationStatement)) {
      visitor.setCurrent(statements2[0]);
    }
    for (PsiElement statement : statements2) {
      statement.accept(visitor);
    }

    if (searchIsExpression && statements[0].getFirstChild() instanceof PsiModifierList && statements2.length == 0) {
      return;
    }
    boolean targetFound = false;
    for (final String name : matchOptions.getVariableConstraintNames()) {
      final MatchVariableConstraint constraint = matchOptions.getVariableConstraint(name);
      if (constraint.isPartOfSearchResults() && !Configuration.CONTEXT_VAR_NAME.equals(constraint.getName())) {
        targetFound = true;
        break;
      }
    }
    if (!targetFound && searchIsExpression != replaceIsExpression) {
      throw new UnsupportedPatternException(
        searchIsExpression ? SSRBundle.message("replacement.template.is.not.expression.error.message") :
        SSRBundle.message("search.template.is.not.expression.error.message")
      );
    }
  }

  static class ValidatingVisitor extends JavaRecursiveElementWalkingVisitor {
    private PsiElement myCurrent;

    @Override public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();

      if (nameReferenceElement == null ||
          !nameReferenceElement.getText().equals(MatchOptions.MODIFIER_ANNOTATION_NAME)) {
        return;
      }

      for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
        for (PsiAnnotationMemberValue v : AnnotationUtil.arrayAttributeValues(pair.getValue())) {
          checkModifier(StringUtil.unquoteString(v.getText()));
        }
      }
    }

    private static void checkModifier(final String name) {
      if (!MatchOptions.INSTANCE_MODIFIER_NAME.equals(name) &&
          !PsiModifier.PACKAGE_LOCAL.equals(name) &&
          ArrayUtil.find(JavaMatchingVisitor.MODIFIERS, name) < 0
        ) {
        throw new MalformedPatternException(SSRBundle.message("invalid.modifier.type",name));
      }
    }

    @Override
    public void visitErrorElement(PsiErrorElement element) {
      super.visitErrorElement(element);
      final PsiElement parent = element.getParent();
      final String errorDescription = element.getErrorDescription();
      if (parent instanceof PsiClass && "Identifier expected".equals(errorDescription)) {
        // other class content variable.
        return;
      }
      if (parent instanceof PsiTryStatement && "'catch' or 'finally' expected".equals(errorDescription)) {
        // searching for naked try allowed
        return;
      }
      if (parent == myCurrent) {
        // search for expression, type, annotation or symbol
        if ("';' expected".equals(errorDescription)  && element.getNextSibling() == null) {
          // expression
          return;
        }
        if ("Identifier or type expected".equals(errorDescription)) {
          // annotation
          return;
        }
        if ("Identifier expected".equals(errorDescription)) {
          // type
          return;
        }
      }
      throw new MalformedPatternException(errorDescription);
    }

    void setCurrent(PsiElement current) {
      myCurrent = current;
    }
  }

  @Override
  public LanguageFileType getDefaultFileType(LanguageFileType currentDefaultFileType) {
    return StdFileTypes.JAVA;
  }

  @Override
  public Configuration[] getPredefinedTemplates() {
    return JavaPredefinedConfigurations.createPredefinedTemplates();
  }

  @Override
  public void provideAdditionalReplaceOptions(@NotNull PsiElement node, final ReplaceOptions options, final ReplacementBuilder builder) {
    node.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }

      @Override
      public void visitParameter(PsiParameter parameter) {
        super.visitParameter(parameter);

        String name = parameter.getName();
        String type = parameter.getType().getCanonicalText();

        if (StructuralSearchUtil.isTypedVariable(name)) {
          name = Replacer.stripTypedVariableDecoration(name);

          if (StructuralSearchUtil.isTypedVariable(type)) {
            type = Replacer.stripTypedVariableDecoration(type);
          }
          ParameterInfo nameInfo = builder.findParameterization(name);
          ParameterInfo typeInfo = builder.findParameterization(type);

          final PsiElement scope = parameter.getDeclarationScope();
          if (nameInfo != null && typeInfo != null && !(scope instanceof PsiCatchSection) && !(scope instanceof PsiForeachStatement)) {
            nameInfo.setArgumentContext(false);
            typeInfo.setArgumentContext(false);
            typeInfo.setMethodParameterContext(true);
            nameInfo.setMethodParameterContext(true);
            typeInfo.setElement(parameter.getTypeElement());
          }
        }
      }
    });
  }

  @Override
  public int handleSubstitution(final ParameterInfo info,
                                MatchResult match,
                                StringBuilder result,
                                int offset,
                                ReplacementInfo replacementInfo) {
    if (info.getName().equals(match.getName())) {
      final String replacementString;
      boolean forceAddingNewLine = false;

      if (info.isMethodParameterContext()) {
        final StringBuilder buf = new StringBuilder();
        handleMethodParameter(buf, info, replacementInfo);
        replacementString = buf.toString();
      }
      else if (match.hasChildren() && !match.isScopeMatch()) {
        // compound matches
        final StringBuilder buf = new StringBuilder();

        MatchResult previous = null;
        boolean stripSemicolon = false;
        for (final MatchResult matchResult : match.getChildren()) {
          final PsiElement currentElement = matchResult.getMatch();
          stripSemicolon = !(currentElement instanceof PsiField);

          if (previous != null) {
            final PsiElement parent = currentElement.getParent();
            if (parent instanceof PsiVariable) {
              final PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(parent);
              if (PsiUtil.isJavaToken(prevSibling, JavaTokenType.COMMA)) {
                buf.append(',');
              }
            }
            else if (info.isStatementContext()) {
              final PsiElement prevSibling = currentElement.getPrevSibling();

              if (prevSibling instanceof PsiWhiteSpace && prevSibling.getPrevSibling() == previous.getMatch()) {
                // sequential statements matched so preserve whitespace
                buf.append(prevSibling.getText());
              }
              else {
                buf.append('\n');
              }
            }
            else if (info.isArgumentContext()) {
              buf.append(',');
            }
            else if (parent instanceof PsiClass) {
              final PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(currentElement);
              if (PsiUtil.isJavaToken(prevSibling, JavaTokenType.COMMA)) {
                buf.append(',');
              }
              else {
                buf.append('\n');
              }
            }
            else if (parent instanceof PsiReferenceList) {
              buf.append(',');
            }
            else if (parent instanceof PsiPolyadicExpression) {
              final PsiPolyadicExpression expression = (PsiPolyadicExpression)parent;
              final PsiJavaToken token = expression.getTokenBeforeOperand(expression.getOperands()[1]);
              if (token != null) {
                buf.append(token.getText());
              }
            }
            else {
              buf.append(' ');
            }
          }

          buf.append(matchResult.getMatchImage());
          forceAddingNewLine = currentElement instanceof PsiComment;
          previous = matchResult;
        }

        replacementString = stripSemicolon ? StringUtil.trimEnd(buf.toString(), ';') : buf.toString();
      } else {
        final PsiElement matchElement = match.getMatch();
        if (info.isStatementContext()) {
          forceAddingNewLine = matchElement instanceof PsiComment;
        }
        final String matchImage = match.getMatchImage();
        replacementString = !(matchElement instanceof PsiField) ? StringUtil.trimEnd(matchImage, ';') : matchImage;
      }

      offset = Replacer.insertSubstitution(result, offset, info, replacementString);
      offset = removeExtraSemicolon(info, offset, result, match);
      if (forceAddingNewLine && info.isStatementContext()) {
        result.insert(info.getStartIndex() + offset + 1, '\n');
        offset++;
      }
    }
    return offset;
  }

  @Override
  public int handleNoSubstitution(ParameterInfo info, int offset, StringBuilder result) {
    final PsiElement element = info.getElement();
    final PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(element);
    if (prevSibling instanceof PsiJavaToken && isRemovableToken(prevSibling)) {
      final int start = info.getBeforeDelimiterPos() + offset - (prevSibling.getTextLength() - 1);
      final int end = info.getStartIndex() + offset;
      result.delete(start, end);
      return offset - (end - start);
    }
    final PsiElement nextSibling = PsiTreeUtil.skipWhitespacesForward(element);
    if (isRemovableToken(nextSibling)) {
      final int start = info.getStartIndex() + offset;
      final int end = info.getAfterDelimiterPos() + nextSibling.getTextLength() + offset;
      result.delete(start, end);
      return offset - 1;
    }
    else if (element instanceof PsiTypeElement && nextSibling instanceof PsiIdentifier) {
      final int start = info.getStartIndex() + offset;
      final int end = info.getAfterDelimiterPos() + offset;
      result.delete(start, end);
      return offset - 1;
    }
    if (element == null || !(element.getParent() instanceof PsiForStatement)) {
      return removeExtraSemicolon(info, offset, result, null);
    }
    return offset;
  }

  private static boolean isRemovableToken(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiAnnotationParameterList || // ',' between annotation parameters
          parent instanceof PsiAssertStatement || // ':' before assertion message
          parent instanceof PsiExpressionList || // ',' between expressions
          parent instanceof PsiParameterList || // ',' between parameters
          parent instanceof PsiPolyadicExpression || // '+', '*', '&&' etcetera
          parent instanceof PsiReferenceList || // ','
          parent instanceof PsiReferenceParameterList || // ','
          parent instanceof PsiResourceList || // ';'
          parent instanceof PsiTypeParameterList || // ','
          parent instanceof PsiLocalVariable || parent instanceof PsiField)) { // '=' before initializer
      return false;
    }
    final String text = element.getText();
    if (text.length() != 1) {
      return true;
    }
    switch(text.charAt(0)) {
      case '<':
      case '>':
      case '(':
      case ')':
      case '{':
      case '}':
      case '[':
      case ']':
        return false;
      default:
        return true;
    }
  }

  @Override
  public boolean isIdentifier(@Nullable PsiElement element) {
    return element instanceof PsiIdentifier;
  }

  @NotNull
  @Override
  public Collection<String> getReservedWords() {
    return Collections.singleton(PsiModifier.PACKAGE_LOCAL);
  }

  @Override
  public boolean isDocCommentOwner(PsiElement match) {
    return match instanceof PsiMember;
  }

  private static void handleMethodParameter(StringBuilder buf, ParameterInfo info, ReplacementInfo replacementInfo) {
    if(!(info.getElement() instanceof PsiTypeElement)) {
      // no specific handling for name of method parameter since it is handled with type
      return;
    }

    String name = ((PsiParameter)info.getElement().getParent()).getName();
    name = StructuralSearchUtil.isTypedVariable(name) ? Replacer.stripTypedVariableDecoration(name):name;

    final MatchResult matchResult = replacementInfo.getNamedMatchResult(name);
    if (matchResult == null) return;

    if (matchResult.isMultipleMatch()) {
      for (MatchResult result : matchResult.getChildren()) {
        if (buf.length() > 0) {
          buf.append(',');
        }

        appendParameter(buf, result);
      }
    } else {
      appendParameter(buf, matchResult);
    }
  }

  private static void appendParameter(final StringBuilder buf, final MatchResult matchResult) {
    final List<MatchResult> sons = matchResult.getChildren();
    assert sons.size() == 1;
    buf.append(sons.get(0).getMatchImage()).append(' ').append(matchResult.getMatchImage());
  }

  private static int removeExtraSemicolon(ParameterInfo info, int offset, StringBuilder result, MatchResult match) {
    if (info.isStatementContext()) {
      final int index = offset + info.getStartIndex();
      final PsiElement matchElement = (match == null) ? null : match.getMatch();
      if (result.charAt(index) == ';' &&
          ( matchElement == null ||
            ( result.charAt(index-1)=='}' &&
              !(matchElement instanceof PsiDeclarationStatement) && // array init in dcl
              !(matchElement instanceof PsiNewExpression) && // array initializer
              !(matchElement instanceof PsiArrayInitializerExpression)
            ) ||
            ( match.isMultipleMatch()  // ; in comment
              ? match.getChildren().get(match.getChildren().size() - 1).getMatch() instanceof PsiComment
              : matchElement instanceof PsiComment
            )
          )
        ) {
        result.deleteCharAt(index);
        --offset;
      }
    }

    return offset;
  }

  @Override
  public boolean isApplicableConstraint(String constraintName, @Nullable PsiElement variableNode, boolean completePattern, boolean target) {
    switch (constraintName) {
      case UIUtil.TEXT: return !completePattern;
      case UIUtil.TEXT_HIERARCHY:
        if (variableNode != null) {
          final PsiElement parent = variableNode.getParent();
          if (parent instanceof PsiJavaCodeReferenceElement) {
            final PsiElement grandParent = parent.getParent();
            if (grandParent instanceof PsiTypeElement || grandParent instanceof PsiReferenceList ||
                grandParent instanceof PsiReferenceExpression || grandParent instanceof PsiNewExpression ||
                grandParent instanceof PsiAnonymousClass) return true;
          }
          else if (parent instanceof PsiClass) return true;
          else if (isMemberSurroundedByClass(parent)) return true;
        }
        return false;
      case UIUtil.EXPECTED_TYPE:
        if (variableNode != null) {
          final PsiElement grandParent = variableNode.getParent().getParent();
          if (grandParent instanceof PsiExpressionStatement) {
            if (hasSemicolon(grandParent)) return false;
          }
          else if (grandParent instanceof PsiStatement) return false;
        }
      case UIUtil.TYPE:
        if (variableNode instanceof PsiExpressionStatement) {
          final PsiElement child = variableNode.getLastChild();
          if (child instanceof PsiErrorElement) {
            final PsiErrorElement errorElement = (PsiErrorElement)child;
            return "';' expected".equals(errorElement.getErrorDescription());
          }
        }
        return variableNode != null && variableNode.getParent() instanceof PsiExpression;
      case UIUtil.MINIMUM_ZERO:
        if (target || variableNode == null) return false;
        return isApplicableMinCount(variableNode) || isApplicableMinMaxCount(variableNode);
      case UIUtil.MAXIMUM_UNLIMITED:
        if (variableNode == null) return false;
        return isApplicableMaxCount(variableNode) || isApplicableMinMaxCount(variableNode);
      case UIUtil.REFERENCE:
        if (completePattern || variableNode == null) return false;
        if (variableNode instanceof PsiLiteralExpression && ((PsiLiteralExpression)variableNode).getValue() instanceof String) return true;
        final PsiElement parent = variableNode.getParent();
        return parent instanceof PsiReferenceExpression || parent instanceof PsiJavaCodeReferenceElement;
      default: return super.isApplicableConstraint(constraintName, variableNode, completePattern, target);
    }
  }

  private static boolean isApplicableMinCount(@NotNull PsiElement variableNode) {
    final PsiElement parent = variableNode.getParent();
    if (parent instanceof PsiBreakStatement) return true;
    if (parent instanceof PsiContinueStatement) return true;

    final PsiElement grandParent = parent.getParent();
    if (grandParent instanceof PsiReferenceList) return true;
    if (grandParent instanceof PsiPolyadicExpression) {
      return ((PsiPolyadicExpression)grandParent).getOperands().length > 2;
    }
    if (parent instanceof PsiReferenceExpression) {
      if (grandParent instanceof PsiReferenceExpression) return true;
      if (grandParent instanceof PsiReturnStatement) return true;
      if (grandParent instanceof PsiAssertStatement) return ((PsiAssertStatement)grandParent).getAssertDescription() == parent;
      if (grandParent instanceof PsiNameValuePair) return ((PsiNameValuePair)grandParent).getValue() == parent;
    }
    if (grandParent instanceof PsiSwitchLabelStatement) {
      return ((PsiSwitchLabelStatement)grandParent).getEnclosingSwitchStatement() != null;
    }
    if (grandParent instanceof PsiVariable) {
      return ((PsiVariable)grandParent).getInitializer() == parent;
    }
    if (grandParent instanceof PsiNewExpression) {
      return ((PsiNewExpression)grandParent).getArrayInitializer() != null;
    }
    if (grandParent instanceof PsiTypeElement) {
      final PsiElement greatGrandParent = grandParent.getParent();
      if (greatGrandParent instanceof PsiTypeElement) {
        final PsiType type = ((PsiTypeElement)greatGrandParent).getType();
        return type instanceof PsiWildcardType && ((PsiWildcardType)type).isExtends();
      }
    }
    if (grandParent instanceof PsiExpressionStatement && hasSemicolon(grandParent)) {
      final PsiElement greatGrandParent = grandParent.getParent();
      return !(greatGrandParent instanceof PsiCodeBlock) ||
             !(greatGrandParent.getParent() instanceof JavaDummyHolder) ||
             PsiTreeUtil.getChildrenOfAnyType(greatGrandParent, PsiStatement.class, PsiComment.class).size() > 1;
    }
    return false;
  }

  private static boolean isApplicableMaxCount(@NotNull PsiElement variableNode) {
    final PsiElement parent = variableNode.getParent();
    if (parent instanceof PsiLocalVariable) {
      final PsiLocalVariable localVariable = (PsiLocalVariable)parent;
      if (localVariable instanceof PsiResourceVariable) return false;
      if (localVariable.getTypeElement().isInferredType()) return false;
      return true;
    }
    if (parent instanceof PsiField) return true;

    final PsiElement grandParent = parent.getParent();
    if (grandParent instanceof PsiPolyadicExpression) return true;
    if (grandParent instanceof PsiSwitchLabelStatement)  return true;
    if (grandParent instanceof PsiExpressionStatement && hasSemicolon(grandParent)) return true;
    if (grandParent instanceof PsiReferenceList) {
      final PsiElement greatGrandParent = grandParent.getParent();
      return !(greatGrandParent instanceof PsiClass) || ((PsiClass)greatGrandParent).getExtendsList() != grandParent ||
        greatGrandParent instanceof PsiTypeParameter;
    }
    return false;
  }

  private static boolean isApplicableMinMaxCount(@NotNull PsiElement variableNode) {
    if (variableNode instanceof PsiDocToken) return true;
    final PsiElement parent = variableNode.getParent();
    if (isMemberSurroundedByClass(parent)) return true;
    final PsiElement grandParent = parent.getParent();
    if (grandParent instanceof PsiCatchSection && parent instanceof PsiParameter) return true;
    if (grandParent instanceof PsiAnnotation && !(grandParent.getParent().getNextSibling() instanceof PsiErrorElement)) return true;
    if (grandParent instanceof PsiParameterList || grandParent instanceof PsiExpressionList ||
        grandParent instanceof PsiTypeParameterList || grandParent instanceof PsiResourceList ||
        grandParent instanceof PsiResourceExpression || grandParent instanceof PsiArrayInitializerExpression ||
        grandParent instanceof PsiArrayInitializerMemberValue) {
      return true;
    }
    if (grandParent instanceof PsiTypeElement) {
      final PsiElement greatGrandParent = grandParent.getParent();
      if (greatGrandParent instanceof PsiReferenceParameterList || greatGrandParent instanceof PsiClass) return true;
    }
    if (grandParent instanceof PsiAnnotationParameterList && parent instanceof PsiNameValuePair) {
      return ((PsiNameValuePair)parent).getNameIdentifier() == variableNode;
    }
    return false;
  }

  private static boolean isMemberSurroundedByClass(PsiElement parent) {
    if (!(parent instanceof PsiMember) || parent instanceof PsiTypeParameter) {
      return false;
    }
    final PsiMember member = (PsiMember)parent;
    final PsiClass aClass = member.getContainingClass();
    if (aClass == null) {
      return false;
    }
    final String name = aClass.getName();
    return name != null && !"_Dummy_".equals(name);
  }

  private static boolean hasSemicolon(PsiElement element) {
    PsiElement lastChild = element.getLastChild();
    while (lastChild instanceof PsiComment || lastChild instanceof PsiWhiteSpace) {
      lastChild = lastChild.getPrevSibling();
    }
    return PsiUtil.isJavaToken(lastChild, JavaTokenType.SEMICOLON);
  }
}
