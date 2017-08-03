/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch;

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
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.structuralsearch.impl.matcher.*;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.JavaCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.ParameterInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementBuilder;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
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
      PsiElement parent = match.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement && !(parent instanceof PsiExpression)) {
        match = parent; // care about generic
      }
    }
    final String matchText = match.getText();
    if (start==0 && end==-1) return matchText;
    return matchText.substring(start,end == -1? matchText.length():end);
  }

  @Override
  public Class getElementContextByPsi(PsiElement element) {
    if (element instanceof PsiIdentifier) {
      element = element.getParent();
    }

    if (element instanceof PsiMember) {
      return PsiMember.class;
    } else {
      return PsiExpression.class;
    }
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
    if (targetNode instanceof PsiCodeBlock && ((PsiCodeBlock)targetNode).getStatements().length == 1) {
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
    elements[0].getParent().accept(new JavaCompilingVisitor(globalVisitor));
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
      final PsiElement element = elementFactory.createStatementFromText("{\n" + text + "\n}", null);
      final PsiElement[] children = ((PsiBlockStatement)element).getCodeBlock().getChildren();
      final int extraChildCount = 4;

      if (children.length > extraChildCount) {
        PsiElement[] result = new PsiElement[children.length - extraChildCount];
        System.arraycopy(children, 2, result, 0, children.length - extraChildCount);

        if (shouldTryExpressionPattern(result)) {
          try {
            final PsiElement[] expressionPattern =
              createPatternTree(text, PatternTreeContext.Expression, fileType, language, contextName, extension, project, false);
            if (expressionPattern.length == 1) {
              result = expressionPattern;
            }
          } catch (IncorrectOperationException ignore) {}
        }
        else if (shouldTryClassPattern(result)) {
          final PsiElement[] classPattern =
            createPatternTree(text, PatternTreeContext.Class, fileType, language, contextName, extension, project, false);
          if (classPattern.length == 1) {
            result = classPattern;
          }
        }
        return result;
      }
      else {
        return PsiElement.EMPTY_ARRAY;
      }
    }
    else if (context == PatternTreeContext.Class) {
      final PsiClass clazz = elementFactory.createClassFromText(text, null);
      PsiElement startChild = clazz.getLBrace();
      if (startChild != null) startChild = startChild.getNextSibling();

      PsiElement endChild = clazz.getRBrace();
      if (endChild != null) endChild = endChild.getPrevSibling();
      if (startChild == endChild) return PsiElement.EMPTY_ARRAY; // nothing produced

      final PsiCodeBlock codeBlock = elementFactory.createCodeBlock();
      final List<PsiElement> result = new ArrayList<>(3);
      assert startChild != null;
      for (PsiElement el = startChild.getNextSibling(); el != endChild && el != null; el = el.getNextSibling()) {
        if (el instanceof PsiErrorElement) continue;
        result.add(codeBlock.add(el));
      }

      return PsiUtilCore.toPsiElementArray(result);
    }
    else if (context == PatternTreeContext.Expression) {
      final PsiExpression expression = elementFactory.createExpressionFromText(text, null);
      final PsiBlockStatement statement = (PsiBlockStatement)elementFactory.createStatementFromText("{\na\n}", null);
      final PsiElement[] children = statement.getCodeBlock().getChildren();
      if (children.length != 5) return PsiElement.EMPTY_ARRAY;
      final PsiExpressionStatement childStatement = (PsiExpressionStatement)children[2];
      childStatement.getExpression().replace(expression);
      return new PsiElement[] { childStatement };
    }
    else {
      return PsiFileFactory.getInstance(project).createFileFromText("__dummy.java", JavaFileType.INSTANCE, text).getChildren();
    }
  }

  private static boolean shouldTryExpressionPattern(PsiElement[] elements) {
    if (elements.length >= 1 && elements.length <= 3) {
      final PsiElement firstElement = elements[0];
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

  private static boolean shouldTryClassPattern(PsiElement[] elements) {
    if (elements.length < 2) {
      return false;
    }
    final PsiElement firstElement = elements[0];
    final PsiElement secondElement = elements[1];

    if (firstElement instanceof PsiDocComment) {
      // might be method with javadoc
      return true;
    }
    else if (firstElement instanceof PsiDeclarationStatement && PsiTreeUtil.lastChild(firstElement) instanceof PsiErrorElement) {
      // might be method
      return true;
    }
    else if (firstElement instanceof PsiErrorElement &&
             secondElement instanceof PsiExpressionStatement &&
             PsiTreeUtil.lastChild(secondElement) instanceof PsiErrorElement) {
      // might be generic method
      return true;
    }
    else if (elements.length == 3 && PsiModifier.STATIC.equals(firstElement.getText()) && secondElement instanceof PsiWhiteSpace &&
        elements[2] instanceof PsiBlockStatement) {
      // looks like static initializer
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

    if (element != null && !useLastConfiguration) {
      final Editor selectedEditor = FileEditorManager.getInstance(searchContext.getProject()).getSelectedTextEditor();

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

    final PsiManager psimanager = PsiManager.getInstance(searchContext.getProject());
    final Project project = psimanager.getProject();
    final PsiCodeFragment file = createCodeFragment(project, text, element);
    final Document doc = PsiDocumentManager.getInstance(searchContext.getProject()).getDocument(file);
    DaemonCodeAnalyzer.getInstance(searchContext.getProject()).setHighlightingEnabled(file, false);
    return UIUtil.createEditor(doc, searchContext.getProject(), true, true, getTemplateContextType());
  }

  @Override
  public Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return JavaCodeContextType.class;
  }

  @Override
  public PsiCodeFragment createCodeFragment(Project project, String text, PsiElement context) {
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    return factory.createCodeBlockCodeFragment(text, context, true);
  }

  @Override
  public void checkSearchPattern(Project project, MatchOptions options) {
    ValidatingVisitor visitor = new ValidatingVisitor();
    final CompiledPattern compiledPattern = PatternCompiler.compilePattern(project, options);
    final int nodeCount = compiledPattern.getNodeCount();
    final NodeIterator nodes = compiledPattern.getNodes();
    while (nodes.hasNext()) {
      final PsiElement current = nodes.current();
      visitor.setCurrent((nodeCount == 1 && (current instanceof PsiExpressionStatement|| current instanceof PsiDeclarationStatement))
                         ? current : null);
      current.accept(visitor);
      nodes.advance();
    }
    nodes.reset();
  }

  @Override
  public void checkReplacementPattern(Project project, ReplaceOptions options) {
    MatchOptions matchOptions = options.getMatchOptions();
    FileType fileType = matchOptions.getFileType();
    PsiElement[] statements = MatcherImplUtil.createTreeFromText(
      matchOptions.getSearchPattern(),
      PatternTreeContext.Block,
      fileType,
      project
    );
    final boolean searchIsExpression = statements.length == 1 && statements[0].getLastChild() instanceof PsiErrorElement;

    PsiElement[] statements2 = MatcherImplUtil.createTreeFromText(
      options.getReplacement(),
      PatternTreeContext.Block,
      fileType,
      project
    );
    final boolean replaceIsExpression = statements2.length == 1 && statements2[0].getLastChild() instanceof PsiErrorElement;

    ValidatingVisitor visitor = new ValidatingVisitor();
    for (PsiElement statement : statements2) {
      visitor.setCurrent((statements.length == 1 && (statement instanceof PsiExpressionStatement || statement instanceof PsiDeclarationStatement))
                         ? statement : null);
      statement.accept(visitor);
    }

    if (searchIsExpression && statements[0].getFirstChild() instanceof PsiModifierList && statements2.length == 0) {
      return;
    }
    boolean targetFound = false;
    for (final String name : matchOptions.getVariableConstraintNames()) {
      final MatchVariableConstraint constraint = matchOptions.getVariableConstraint(name);
      if (constraint.isPartOfSearchResults()) {
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

      for(PsiNameValuePair pair:annotation.getParameterList().getAttributes()) {
        final PsiAnnotationMemberValue value = pair.getValue();

        if (value instanceof PsiArrayInitializerMemberValue) {
          for(PsiAnnotationMemberValue v:((PsiArrayInitializerMemberValue)value).getInitializers()) {
            final String name = StringUtil.unquoteString(v.getText());
            checkModifier(name);
          }

        } else if (value != null) {
          final String name = StringUtil.unquoteString(value.getText());
          checkModifier(name);
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
        if ("';' expected".equals(errorDescription)) {
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
                                HashMap<String, MatchResult> matchMap) {
    if (info.getName().equals(match.getName())) {
      final String replacementString;
      boolean forceAddingNewLine = false;

      if (info.isMethodParameterContext()) {
        final StringBuilder buf = new StringBuilder();
        handleMethodParameter(buf, info, matchMap);
        replacementString = buf.toString();
      }
      else if (match.hasSons() && !match.isScopeMatch()) {
        // compound matches
        final StringBuilder buf = new StringBuilder();

        MatchResult previous = null;
        boolean stripSemicolon = false;
        for (final MatchResult matchResult : match.getAllSons()) {
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
  public boolean isIdentifier(PsiElement element) {
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

  private static void handleMethodParameter(StringBuilder buf, ParameterInfo info, HashMap<String, MatchResult> matchMap) {
    if(!(info.getElement() instanceof PsiTypeElement)) {
      // no specific handling for name of method parameter since it is handled with type
      return;
    }

    String name = ((PsiParameter)info.getElement().getParent()).getName();
    name = StructuralSearchUtil.isTypedVariable(name) ? Replacer.stripTypedVariableDecoration(name):name;

    final MatchResult matchResult = matchMap.get(name);
    if (matchResult == null) return;

    if (matchResult.isMultipleMatch()) {
      for (MatchResult result : matchResult.getAllSons()) {
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
    final List<MatchResult> sons = matchResult.getAllSons();
    assert sons.size() == 1;
    buf.append(sons.get(0).getMatchImage()).append(' ').append(matchResult.getMatchImage());
  }

  private static int removeExtraSemicolon(ParameterInfo info, int offset, StringBuilder result, MatchResult match) {
    if (info.isStatementContext()) {
      final int index = offset + info.getStartIndex();
      if (result.charAt(index)==';' &&
          ( match == null ||
            ( result.charAt(index-1)=='}' &&
              !(match.getMatch() instanceof PsiDeclarationStatement) && // array init in dcl
              !(match.getMatch() instanceof PsiNewExpression) // array initializer
            ) ||
            ( !match.isMultipleMatch() &&                                                // ; in comment
              match.getMatch() instanceof PsiComment
            ) ||
            ( match.isMultipleMatch() &&                                                 // ; in comment
              match.getAllSons().get( match.getAllSons().size() - 1 ).getMatch() instanceof PsiComment
            )
          )
        ) {
        result.deleteCharAt(index);
        --offset;
      }
    }

    return offset;
  }
}
