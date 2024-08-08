// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.core.JavaPsiBundle;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.PsiCodeFragmentImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.structuralsearch.impl.matcher.*;
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.JavaCompilingVisitor;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.impl.matcher.predicates.ExprTypePredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.FormalArgTypePredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ParameterInfo;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementBuilder;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JavaStructuralSearchProfile extends StructuralSearchProfile {
  private static final Key<Map<String, ParameterInfo>> PARAMETER_CONTEXT = new Key<>("PARAMETER_CONTEXT");
  private static final Key<Integer> PARAMETER_LENGTH = new Key<>("PARAMETER_LENGTH");

  public static final PatternContext DEFAULT_CONTEXT = new PatternContext("default", () -> SSRBundle.message("pattern.context.default"));
  public static final PatternContext MEMBER_CONTEXT = new PatternContext("member", () -> SSRBundle.message("pattern.context.class.member"));
  private static final List<PatternContext> PATTERN_CONTEXTS = List.of(DEFAULT_CONTEXT, MEMBER_CONTEXT);

  private static final Set<String> PRIMITIVE_TYPES = Set.of(
    PsiKeyword.SHORT, PsiKeyword.BOOLEAN,
    PsiKeyword.DOUBLE, PsiKeyword.LONG,
    PsiKeyword.INT, PsiKeyword.FLOAT,
    PsiKeyword.CHAR, PsiKeyword.BYTE
  );

  private static final Set<String> RESERVED_WORDS =
    Set.of(MatchOptions.MODIFIER_ANNOTATION_NAME, MatchOptions.INSTANCE_MODIFIER_NAME, PsiModifier.PACKAGE_LOCAL);

  @Override
  public @NotNull String getText(@NotNull PsiElement match, int start, int end) {
    if (match instanceof PsiIdentifier) {
      final PsiElement parent = match.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement referenceElement && !(parent instanceof PsiExpression)) {
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
  public String getTypedVarString(@NotNull PsiElement element) {
    String text;

    if (element instanceof PsiReceiverParameter) {
      text = ((PsiReceiverParameter)element).getIdentifier().getText();
    }
    else if (element instanceof PsiNamedElement) {
      text = ((PsiNamedElement)element).getName();
    }
    else if (element instanceof PsiAnnotation) {
      final PsiJavaCodeReferenceElement referenceElement = ((PsiAnnotation)element).getNameReferenceElement();
      text = referenceElement == null ? null : referenceElement.getText();
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
        final int i = text.indexOf(';');
        if (i != -1) text = text.substring(0, i);
      }
    }

    if (text==null) text = element.getText();

    return text;
  }

  @Override
  public String getMeaningfulText(@NotNull PsiElement element) {
    if (element instanceof PsiReferenceExpression && ((PsiReferenceExpression)element).getQualifierExpression() != null) {
      final PsiElement resolve = ((PsiReferenceExpression)element).resolve();
      if (resolve instanceof PsiClass) return element.getText();

      final PsiElement referencedElement = ((PsiReferenceExpression)element).getReferenceNameElement();
      final String text = referencedElement != null ? referencedElement.getText() : "";

      if (resolve == null && !text.isEmpty() && Character.isUpperCase(text.charAt(0))) {
        return element.getText();
      }
      return text;
    }
    return super.getMeaningfulText(element);
  }

  @Override
  @Nullable
  public String getAlternativeText(@NotNull PsiElement node, @NotNull String previousText) {
    // Short class name is matched with fully qualified name
    if (node instanceof PsiJavaCodeReferenceElement || node instanceof PsiClass) {
      final PsiElement element = (node instanceof PsiJavaCodeReferenceElement)
                                 ? ((PsiJavaCodeReferenceElement)node).resolve()
                                 : node;

      if (element instanceof PsiClass) {
        String text = ((PsiClass)element).getQualifiedName();
        if (previousText.equals(text)) {
          text = ((PsiClass)element).getName();
        }

        return text;
      }
    }
    else if (node instanceof PsiLiteralExpression || node instanceof PsiComment) {
      return node.getText();
    }
    return null;
  }

  @Override
  public @NotNull PsiElement updateCurrentNode(@NotNull PsiElement targetNode) {
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
  public @NotNull PsiElement extendMatchedByDownUp(@NotNull PsiElement targetNode) {
    if (targetNode instanceof PsiIdentifier) {
      targetNode = targetNode.getParent();
    }
    final PsiElement parent = targetNode.getParent();
    if (parent instanceof PsiTypeElement || parent instanceof PsiStatement || parent instanceof PsiLocalVariable) {
      targetNode = parent;
    }
    return targetNode;
  }

  @NotNull
  @Override
  public PsiElement getPresentableElement(@NotNull PsiElement element) {
    element = super.getPresentableElement(element);
    if (element instanceof PsiReferenceExpression) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        return parent;
      }
    }
    else if (element instanceof PsiJavaCodeReferenceElement) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiTypeElement || parent instanceof PsiNewExpression
          || parent instanceof PsiAnnotation || parent instanceof PsiAnonymousClass) {
        return parent;
      }
    }
    return element;
  }

  @Override
  public void compile(PsiElement @NotNull [] elements, @NotNull GlobalCompilingVisitor globalVisitor) {
    new JavaCompilingVisitor(globalVisitor).compile(elements);
  }

  @Override
  @NotNull
  public PsiElementVisitor createMatchingVisitor(@NotNull GlobalMatchingVisitor globalVisitor) {
    return new JavaMatchingVisitor(globalVisitor);
  }

  @Override
  public boolean isMatchNode(PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      return false;
    }
    else if (element instanceof PsiJavaToken) {
      // do not filter out type keyword of new primitive arrays (e.g. int in new int[10])
      return element instanceof PsiKeyword &&
             PRIMITIVE_TYPES.contains(element.getText()) &&
             element.getParent() instanceof PsiNewExpression;
    }
    return true;
  }

  @Override
  @NotNull
  public CompiledPattern createCompiledPattern() {
    return new JavaCompiledPattern();
  }

  @NotNull
  @Override
  public List<MatchPredicate> getCustomPredicates(@NotNull MatchVariableConstraint constraint, @NotNull String name, @NotNull MatchOptions options) {
    final List<MatchPredicate> result = new SmartList<>();

    if (!StringUtil.isEmptyOrSpaces(constraint.getNameOfExprType())) {
      final MatchPredicate predicate = new ExprTypePredicate(
        constraint.isRegexExprType() ? constraint.getNameOfExprType() : constraint.getExpressionTypes(),
        name,
        constraint.isExprTypeWithinHierarchy(),
        options.isCaseSensitiveMatch(),
        constraint.isPartOfSearchResults(),
        constraint.isRegexExprType()
      );
      result.add(constraint.isInvertExprType() ? new NotPredicate(predicate) : predicate);
    }

    if (!StringUtil.isEmptyOrSpaces(constraint.getNameOfFormalArgType())) {
      final MatchPredicate predicate = new FormalArgTypePredicate(
        constraint.isRegexFormalType() ? constraint.getNameOfFormalArgType() : constraint.getExpectedTypes(),
        name,
        constraint.isFormalArgTypeWithinHierarchy(),
        options.isCaseSensitiveMatch(),
        constraint.isPartOfSearchResults(),
        constraint.isRegexFormalType()
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
  public StructuralReplaceHandler getReplaceHandler(@NotNull Project project, @NotNull ReplaceOptions replaceOptions) {
    return new JavaReplaceHandler(project, replaceOptions);
  }

  @Override
  public boolean supportsShortenFQNames() {
    return true;
  }

  @Override
  public boolean supportsUseStaticImports() {
    return true;
  }

  @Override
  public PsiElement @NotNull [] createPatternTree(@NotNull String text,
                                                  @NotNull PatternTreeContext context,
                                                  @NotNull LanguageFileType fileType,
                                                  @NotNull Language language,
                                                  String contextId,
                                                  @NotNull Project project,
                                                  boolean physical) {
    if (MEMBER_CONTEXT.getId().equals(contextId)) {
      context = PatternTreeContext.Class;
    }
    if (context == PatternTreeContext.Block) {
      final PsiCodeFragment fragment = JavaCodeFragmentFactory.getInstance(project).createCodeBlockCodeFragment(text, null, physical);
      final List<PsiElement> result = getNonWhitespaceChildren(fragment);
      if (result.isEmpty()) return PsiElement.EMPTY_ARRAY;

      if (shouldTryExpressionPattern(result)) {
        try {
          final PsiElement[] expressionPattern =
            createPatternTree(text, PatternTreeContext.Expression, fileType, language, contextId, project, physical);
          if (expressionPattern.length == 1) {
            return expressionPattern;
          }
        } catch (IncorrectOperationException ignore) {}
      }
      else if (shouldTryClassPattern(result)) {
        final PsiElement[] classPattern =
          createPatternTree(text, PatternTreeContext.Class, fileType, language, contextId, project, physical);
        if (classPattern.length <= result.size()) {
          return classPattern;
        }
      }
      return PsiUtilCore.toPsiElementArray(result);
    }
    else if (context == PatternTreeContext.Class) {
      final JavaCodeFragment fragment = JavaCodeFragmentFactory.getInstance(project).createMemberCodeFragment(text, null, physical);
      final List<PsiElement> result = getNonWhitespaceChildren(fragment);

      return PsiUtilCore.toPsiElementArray(result);
    }
    else if (context == PatternTreeContext.Expression) {
      final PsiExpressionCodeFragment fragment =
        JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment(text, null, null, physical);
      final PsiExpression expression = fragment.getExpression();
      return (expression == null) ? PsiElement.EMPTY_ARRAY : new PsiElement[] {expression};
    }
    else {
      return new PsiElement[] {PsiFileFactory.getInstance(project).createFileFromText("__dummy.java", JavaFileType.INSTANCE, text)};
    }
  }

  private static List<PsiElement> getNonWhitespaceChildren(PsiElement fragment) {
    PsiElement element = fragment.getFirstChild();
    final List<PsiElement> result = new SmartList<>();
    while (element != null) {
      if (!(element instanceof PsiWhiteSpace)) {
        result.add(element);
      }
      element = element.getNextSibling();
    }
    return result;
  }

  private static boolean shouldTryExpressionPattern(@NotNull List<? extends PsiElement> elements) {
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

  private static boolean shouldTryClassPattern(@NotNull List<? extends PsiElement> elements) {
    if (elements.isEmpty()) {
      return false;
    }
    final PsiElement firstElement = elements.get(0);
    if (firstElement instanceof PsiDeclarationStatement && firstElement.getFirstChild() instanceof PsiClass) {
      return true;
    }
    if (elements.size() < 2) {
      return false;
    }
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
    else if (firstElement instanceof PsiSwitchLabelStatement && ((PsiSwitchLabelStatement)firstElement).isDefaultCase() &&
             PsiTreeUtil.lastChild(firstElement) instanceof PsiErrorElement && secondElement instanceof PsiDeclarationStatement) {
      // possible default method
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
  public Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return JavaCodeContextType.Generic.class;
  }

  @NotNull
  @Override
  public List<PatternContext> getPatternContexts() {
    if (!Registry.is("ssr.in.editor.problem.highlighting")) return super.getPatternContexts();
    return PATTERN_CONTEXTS;
  }

  @NotNull
  @Override
  public PsiCodeFragment createCodeFragment(@NotNull Project project, @NotNull String text, String contextId) {
    final PsiCodeFragmentImpl fragment =
      MEMBER_CONTEXT.getId().equals(contextId)
      ? (PsiCodeFragmentImpl)JavaCodeFragmentFactory.getInstance(project).createMemberCodeFragment(text, null, true)
      : (PsiCodeFragmentImpl)JavaCodeFragmentFactory.getInstance(project).createCodeBlockCodeFragment(text, null, true);
    fragment.setIntentionActionsFilter(intentionAction -> false);
    return fragment;
  }

  @Override
  public @NotNull String getCodeFragmentText(@NotNull PsiFile fragment) {
    final List<String> imports = StringUtil.split(((JavaCodeFragment)fragment).importsToString(), ",");
    final Map<String, String> importMap =
      imports.stream().collect(Collectors.toMap(s -> s.substring(s.lastIndexOf('.') + 1), Function.identity()));
    final StringBuilder result = new StringBuilder();
    fragment.accept(new JavaRecursiveElementWalkingVisitor() {

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        visitReferenceElement(expression);
      }

      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        if (!reference.isQualified()) {
          final String text = reference.getText();
          final String fqName = importMap.get(text);
          result.append(fqName != null ? fqName : text);
        }
        else {
          super.visitReferenceElement(reference);
        }
      }

      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        if (element.getFirstChild() == null) {
          result.append(element.getText());
        }
      }
    });
    return result.toString();
  }

  @Override
  public boolean shouldShowProblem(@NotNull PsiErrorElement error) {
    final String description = error.getErrorDescription();
    final PsiElement parent = error.getParent();

    if (parent instanceof PsiClass && !(parent instanceof PsiCodeFragment) &&
        JavaPsiBundle.message("expected.identifier").equals(description)) {
      final PsiElement prev = error.getPrevSibling();
      if (prev instanceof PsiTypeElement) {
        final String text = prev.getText();
        if (StringUtil.startsWithChar(text, '$') && StringUtil.endsWithChar(text, '$') ||
            text.startsWith(JavaCompiledPattern.TYPED_VAR_PREFIX)) {
          // other class content variable.
          return false;
        }
      }
    }
    if (parent instanceof PsiTryStatement && JavaPsiBundle.message("expected.catch.or.finally").equals(description)) {
      // searching for naked try allowed
      return false;
    }
    final PsiElement grandParent = parent.getParent();
    if (parent instanceof PsiStatement && grandParent instanceof PsiCodeFragment) {
      final int count = PsiTreeUtil.countChildrenOfType(grandParent, PsiStatement.class);
      if (count == 1) {
        final PsiStatement statement = (PsiStatement)parent;
        if (statement instanceof PsiExpressionStatement || statement instanceof PsiDeclarationStatement) {
          // search for expression, type, annotation or symbol
          if (JavaPsiBundle.message("expected.semicolon").equals(description) && error.getNextSibling() == null) {
            // expression
            return false;
          }
          if (JavaPsiBundle.message("expected.identifier.or.type").equals(description)) {
            // annotation
            return false;
          }
          if (JavaPsiBundle.message("expected.identifier").equals(description)) {
            // type
            return false;
          }
        }
      }
    }
    else if (parent instanceof PsiReferenceParameterList && grandParent instanceof PsiJavaCodeReferenceElement) {
      final PsiElement greatGrandParent = grandParent.getParent();
      if (greatGrandParent instanceof PsiTypeElement) {
        final PsiElement greatGreatGrandParent = greatGrandParent.getParent();
        if (greatGreatGrandParent instanceof PsiDeclarationStatement &&
            PsiTreeUtil.skipWhitespacesAndCommentsBackward(greatGreatGrandParent) == null &&
            PsiTreeUtil.skipWhitespacesAndCommentsForward(greatGreatGrandParent) == null &&
            JavaPsiBundle.message("expected.gt.or.comma").equals(description)) {
          // probably less-than expression, not unfinished generic variable declaration e.g. List<String
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public void checkSearchPattern(@NotNull CompiledPattern pattern) {
    final ValidatingVisitor visitor = new ValidatingVisitor();
    final NodeIterator nodes = pattern.getNodes();
    while (nodes.hasNext()) {
      nodes.current().accept(visitor);
      nodes.advance();
    }
    nodes.reset();
  }

  @Override
  public void checkReplacementPattern(@NotNull Project project, @NotNull ReplaceOptions options) {
    final MatchOptions matchOptions = options.getMatchOptions();
    final LanguageFileType fileType = matchOptions.getFileType();
    final Language dialect = matchOptions.getDialect();
    final PatternContext patternContext = matchOptions.getPatternContext();

    final PsiElement[] statements =
      createPatternTree(options.getReplacement(), PatternTreeContext.Block, fileType, dialect, patternContext.getId(), project, false);
    final ValidatingVisitor visitor = new ValidatingVisitor();
    for (PsiElement statement : statements) {
      statement.accept(visitor);
    }
    final boolean replaceIsExpression = isExpressionTemplate(Arrays.asList(statements));

    final CompiledPattern compiledPattern = PatternCompiler.compilePattern(project, matchOptions, false, false);
    if (compiledPattern == null) {
      return;
    }
    final PsiElement targetNode = compiledPattern.getTargetNode();
    final boolean searchIsExpression = targetNode != null
                                       ? getPresentableElement(targetNode) instanceof PsiExpression
                                       : isExpressionTemplate(compiledPattern.getVariableNodes(Configuration.CONTEXT_VAR_NAME));
    if (searchIsExpression && !replaceIsExpression) {
      throw new UnsupportedPatternException(SSRBundle.message("replacement.template.is.not.expression.error.message"));
    }
  }

  private static boolean isExpressionTemplate(List<PsiElement> elements) {
    if (elements.size() != 1) {
      return false;
    }
    final PsiElement element = elements.get(0);
    return element instanceof PsiExpression ||
           element instanceof PsiExpressionStatement && element.getLastChild() instanceof PsiErrorElement;
  }

  class ValidatingVisitor extends JavaRecursiveElementWalkingVisitor {

    @Override public void visitAnnotation(@NotNull PsiAnnotation annotation) {
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

    private static void checkModifier(String name) {
      if (!MatchOptions.INSTANCE_MODIFIER_NAME.equals(name) &&
          !PsiModifier.PACKAGE_LOCAL.equals(name) &&
          ArrayUtil.find(JavaMatchingVisitor.MODIFIERS, name) < 0
        ) {
        throw new MalformedPatternException(SSRBundle.message("invalid.modifier.type",name));
      }
    }

    @Override
    public void visitErrorElement(@NotNull PsiErrorElement element) {
      super.visitErrorElement(element);
      if (shouldShowProblem(element)) {
        throw new MalformedPatternException(element);
      }
    }
  }

  @Override
  public LanguageFileType getDefaultFileType(LanguageFileType currentDefaultFileType) {
    return JavaFileType.INSTANCE;
  }

  @Override
  public Configuration @NotNull [] getPredefinedTemplates() {
    return JavaPredefinedConfigurations.createPredefinedTemplates();
  }

  @Override
  public void provideAdditionalReplaceOptions(@NotNull PsiElement node, @NotNull ReplaceOptions options, @NotNull ReplacementBuilder builder) {
    final StructuralSearchProfile profile = this;
    node.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        visitElement(expression);
      }

      @Override
      public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
        super.visitNameValuePair(pair);
        setParameterContext(pair, pair.getNameIdentifier(), pair.getValue());
      }

      @Override
      public void visitParameter(@NotNull PsiParameter parameter) {
        final PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof PsiCatchSection || scope instanceof PsiForeachStatement) {
          return;
        }
        setParameterContext(parameter, parameter.getNameIdentifier(), parameter.getTypeElement());
      }

      private void setParameterContext(@NotNull PsiElement element, PsiElement nameIdentifier, @Nullable PsiElement scopeElement) {
        final ParameterInfo nameInfo = builder.findParameterization(nameIdentifier);
        if (nameInfo == null) return;
        nameInfo.setArgumentContext(false);
        final Map<String, ParameterInfo> infos = new HashMap<>();
        infos.put(nameInfo.getName(), nameInfo);
        nameInfo.putUserData(PARAMETER_CONTEXT, infos);
        nameInfo.setElement(element);

        if (scopeElement == null) return;
        scopeElement.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitElement(@NotNull PsiElement element) {
            final String type = element.getText();
            if (profile.isReplacementTypedVariable(type)) {
              final ParameterInfo typeInfo = builder.findParameterization(element);
              if (typeInfo != null) {
                typeInfo.setArgumentContext(false);
                typeInfo.putUserData(PARAMETER_CONTEXT, Collections.emptyMap());
                infos.put(typeInfo.getName(), typeInfo);
                return;
              }
            }
            super.visitElement(element);
          }
        });
        final int length = element.getTextLength() - infos.keySet().stream().mapToInt(key -> key.length() + 2).sum();
        nameInfo.putUserData(PARAMETER_LENGTH, length);
      }
    });
  }

  @Override
  public void handleSubstitution(@NotNull ParameterInfo info, @NotNull MatchResult match, @NotNull StringBuilder result, @NotNull ReplacementInfo replacementInfo) {
    if (info.getName().equals(match.getName())) {
      final String replacementString;
      boolean forceAddingNewLine = false;
      int offset = 0;

      final PsiElement element = info.getElement();
      final Map<String, ParameterInfo> typeInfos = info.getUserData(PARAMETER_CONTEXT);
      if (typeInfos != null) {
        if (element instanceof PsiParameter) {
          final int parameterEnd = info.getStartIndex();
          final Integer length = info.getUserData(PARAMETER_LENGTH);
          assert length != null;
          final int parameterStart = parameterEnd - length;
          final String template = result.substring(parameterStart, parameterEnd);
          replacementString = handleParameter(info, replacementInfo, -parameterStart, template);
          result.delete(parameterStart, parameterEnd);
          offset -= template.length();
        }
        else if (element instanceof PsiNameValuePair) {
          final int parameterStart = info.getStartIndex();
          final Integer length = info.getUserData(PARAMETER_LENGTH);
          assert length != null;
          final int parameterEnd = parameterStart + length;
          final String template = result.substring(parameterStart, parameterEnd);
          replacementString = handleParameter(info, replacementInfo, -parameterStart, template);
          result.delete(parameterStart, parameterEnd);
        }
        else {
          return;
        }
      }
      else if (match.hasChildren() && !match.isScopeMatch()) {
        // compound matches
        final StringBuilder buf = new StringBuilder();

        PsiElement previous = null;
        boolean stripSemicolon = false;
        for (MatchResult matchResult : match.getChildren()) {
          final PsiElement currentElement = matchResult.getMatch();
          stripSemicolon = !(currentElement instanceof PsiField);

          if (previous != null) {
            final PsiElement parent = currentElement.getParent();
            if (parent instanceof PsiVariable || parent instanceof PsiTypeElement || parent instanceof PsiTypeParameter) {
              addSeparatorText(previous.getParent(), parent, buf);
            }
            else if (parent instanceof PsiClass || parent instanceof PsiReferenceList) {
              addSeparatorTextMatchedInAnyOrder(currentElement,
                                                parent instanceof PsiClass ? PsiMember.class : PsiJavaCodeReferenceElement.class, buf);
            }
            else if (info.isStatementContext() ||
                     info.isArgumentContext() ||
                     parent instanceof PsiPolyadicExpression ||
                     parent instanceof PsiArrayInitializerExpression) {
              addSeparatorText(previous, currentElement, buf);
            }
            else {
              buf.append(" "); // fallback, but shouldn't happen
            }
          }

          buf.append(matchResult.getMatchImage());
          forceAddingNewLine = currentElement instanceof PsiComment;
          previous = matchResult.getMatch();
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
      }
    }
  }

  private static void addSeparatorTextMatchedInAnyOrder(PsiElement element, Class<? extends PsiElement> aClass, StringBuilder out) {
    final PsiElement prev = PsiTreeUtil.getPrevSiblingOfType(element, aClass);
    assert prev != null;
    addSeparatorText(prev, element, out);
  }

  private static void addSeparatorText(PsiElement left, PsiElement right, StringBuilder out) {
    for (PsiElement e = left.getNextSibling(); e != null && e != right; e = e.getNextSibling()) {
      out.append(e.getText());
    }
  }

  @Override
  public void handleNoSubstitution(@NotNull ParameterInfo info, @NotNull StringBuilder result) {
    final PsiElement element = info.getElement();
    final PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(element);
    if (prevSibling instanceof PsiJavaToken && isRemovableToken(prevSibling)) {
      final int start = info.getBeforeDelimiterPos() - (prevSibling.getTextLength() - 1);
      final int end = info.getStartIndex();
      result.delete(start, end);
      return;
    }
    final PsiElement nextSibling = PsiTreeUtil.skipWhitespacesForward(element);
    if (isRemovableToken(nextSibling)) {
      final int start = info.getStartIndex();
      final int end = info.getAfterDelimiterPos() + nextSibling.getTextLength();
      result.delete(start, end);
      return;
    }
    else if (element instanceof PsiTypeElement && nextSibling instanceof PsiIdentifier) {
      final int start = info.getStartIndex();
      final int end = info.getAfterDelimiterPos();
      result.delete(start, end);
      return;
    }
    if (element == null || !(element.getParent() instanceof PsiForStatement)) {
      removeExtraSemicolon(info, 0, result, null);
    }
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
          parent instanceof PsiReferenceExpression || // '.' between qualifier & reference
          parent instanceof PsiReferenceList || // ','
          parent instanceof PsiReferenceParameterList || // ','
          parent instanceof PsiResourceList || // ';'
          parent instanceof PsiTypeParameterList || // ','
          parent instanceof PsiAnnotation || // '@'
          parent instanceof PsiLocalVariable || parent instanceof PsiField)) { // '=' before initializer
      return false;
    }
    final String text = element.getText();
    if (text.length() != 1) {
      return true;
    }
    return switch (text.charAt(0)) {
      case '<', '>', '(', ')', '{', '}', '[', ']' -> false;
      default -> true;
    };
  }

  @Override
  public boolean isIdentifier(@Nullable PsiElement element) {
    return element instanceof PsiIdentifier;
  }

  @NotNull
  @Override
  public Collection<String> getReservedWords() {
    return RESERVED_WORDS;
  }

  @Override
  public boolean isDocCommentOwner(@NotNull PsiElement match) {
    return match instanceof PsiMember;
  }

  private static String handleParameter(@NotNull ParameterInfo info, ReplacementInfo replacementInfo, int offset, String template) {
    final MatchResult matchResult = replacementInfo.getNamedMatchResult(info.getName());
    assert matchResult != null;

    final StringBuilder result = new StringBuilder();
    if (matchResult.isMultipleMatch()) {
      PsiElement previous = null;
      for (MatchResult child : matchResult.getChildren()) {
        final PsiElement match = child.getMatch().getParent();
        if (previous != null) addSeparatorText(previous, match, result);
        appendParameter(info, child, offset + result.length(), result.append(template));
        previous = match;
      }
    }
    else {
      result.append(template);
      appendParameter(info, matchResult, offset, result);
    }
    return result.toString();
  }

  private static void appendParameter(ParameterInfo parameterInfo, MatchResult matchResult, int offset, StringBuilder out) {
    final Map<String, ParameterInfo> infos = parameterInfo.getUserData(PARAMETER_CONTEXT);
    assert infos != null;
    final List<MatchResult> matches = new SmartList<>(matchResult.getChildren());
    matches.add(matchResult);
    matches.sort(Comparator.comparingInt((MatchResult result) -> result.getMatch().getTextOffset()).reversed());
    for (MatchResult match : matches) {
      final ParameterInfo typeInfo = infos.get(match.getName());
      if (typeInfo != null) out.insert(typeInfo.getStartIndex() + offset, match.getMatchImage());
    }
  }

  private static int removeExtraSemicolon(ParameterInfo info, int offset, StringBuilder result, MatchResult match) {
    if (!info.isStatementContext()) {
      return offset;
    }
    final int index = offset + info.getStartIndex();
    if (result.charAt(index) != ';') {
      return offset;
    }
    final PsiElement matchElement;
    if (match == null) matchElement = null;
    else if (match.isMultipleMatch()) matchElement = match.getChildren().get(match.size() - 1).getMatch();
    else matchElement = match.getMatch();

    if (matchElement == null || matchElement instanceof PsiComment ||
        result.charAt(index - 1) == '}' &&
        !(matchElement instanceof PsiDeclarationStatement) && // array init in dcl
        !(matchElement instanceof PsiNewExpression) && // array initializer
        !(matchElement instanceof PsiArrayInitializerExpression)) {
      result.deleteCharAt(index);
      --offset;
    }
    return offset;
  }

  @Override
  public boolean isApplicableConstraint(@NotNull String constraintName, @Nullable PsiElement variableNode, boolean completePattern, boolean target) {
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
            if (isCompleteStatement((PsiExpressionStatement)grandParent)) return false;
          }
          else if (grandParent instanceof PsiStatement) return false;
        }
      case UIUtil.TYPE:
      case UIUtil.TYPE_REGEX:
        if (variableNode instanceof PsiExpressionStatement) {
          final PsiElement child = variableNode.getLastChild();
          if (child instanceof PsiErrorElement errorElement) {
            return JavaPsiBundle.message("expected.semicolon").equals(errorElement.getErrorDescription());
          }
        }
        return variableNode != null && variableNode.getParent() instanceof PsiExpression;
      case UIUtil.MINIMUM_ZERO:
        if (target || variableNode == null) return false;
        return isApplicableMinCount(variableNode) || isApplicableMinMaxCount(variableNode);
      case UIUtil.MAXIMUM_UNLIMITED:
        if (target || variableNode == null) return false;
        return isApplicableMaxCount(variableNode) || isApplicableMinMaxCount(variableNode);
      case UIUtil.REFERENCE:
        if (completePattern || variableNode == null) return false;
        if (variableNode instanceof PsiLiteralExpression && ((PsiLiteralExpression)variableNode).getValue() instanceof String) return true;
        final PsiElement parent = variableNode.getParent();
        return parent instanceof PsiJavaCodeReferenceElement;
      default: return super.isApplicableConstraint(constraintName, variableNode, completePattern, target);
    }
  }

  private static boolean isApplicableMinCount(@NotNull PsiElement variableNode) {
    final PsiElement parent = variableNode.getParent();
    if (parent instanceof PsiContinueStatement) return true;
    if (parent instanceof PsiBreakStatement) return true;

    final PsiElement grandParent = parent.getParent();
    if (parent instanceof PsiPatternVariable && grandParent instanceof PsiTypeTestPattern) {
      return true;
    }
    if (grandParent instanceof PsiReferenceList) return true;
    if (grandParent instanceof PsiPolyadicExpression) {
      return ((PsiPolyadicExpression)grandParent).getOperands().length > 2;
    }
    if (parent instanceof PsiReferenceExpression) {
      if (grandParent instanceof PsiReferenceExpression) return true;
      if (grandParent instanceof PsiReturnStatement) return true;
      if (grandParent instanceof PsiAssertStatement) return ((PsiAssertStatement)grandParent).getAssertDescription() == parent;
      if (grandParent instanceof PsiNameValuePair) return ((PsiNameValuePair)grandParent).getValue() == parent;
      if (grandParent instanceof PsiForStatement) return true;
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
      if (greatGrandParent instanceof PsiMethod || greatGrandParent instanceof PsiVariable) {
        return true;
      }
    }
    if (grandParent instanceof PsiExpressionStatement) {
      final PsiElement greatGrandParent = grandParent.getParent();
      if (greatGrandParent instanceof PsiForStatement &&
          !PsiTreeUtil.isAncestor(((PsiForStatement)greatGrandParent).getBody(), variableNode, true)) {
        return true;
      }
      if (isCompleteStatement((PsiExpressionStatement)grandParent)) {
        if (greatGrandParent instanceof PsiLoopStatement ||
            (greatGrandParent instanceof PsiIfStatement && ((PsiIfStatement)greatGrandParent).getElseBranch() != grandParent)) return false;
        return !(greatGrandParent instanceof PsiCodeBlock) ||
               !(greatGrandParent.getParent() instanceof JavaDummyHolder) ||
               PsiTreeUtil.getChildrenOfAnyType(greatGrandParent, PsiStatement.class, PsiComment.class).size() > 1;
      }
    }
    return false;
  }

  private static boolean isApplicableMaxCount(@NotNull PsiElement variableNode) {
    final PsiElement parent = variableNode.getParent();
    if (parent instanceof PsiLocalVariable localVariable) {
      if (localVariable instanceof PsiResourceVariable) return false;
      if (localVariable.getTypeElement().isInferredType()) return false;
      return true;
    }
    if (parent instanceof PsiField) return true;

    final PsiElement grandParent = parent.getParent();
    if (grandParent instanceof PsiPolyadicExpression) return true;
    if (grandParent instanceof PsiExpressionStatement && isCompleteStatement((PsiExpressionStatement)grandParent)) {
      final PsiElement greatGrandParent = grandParent.getParent();
      if (greatGrandParent instanceof PsiForStatement forStatement) {
        return forStatement.getInitialization() == grandParent || forStatement.getUpdate() == grandParent;
      }
      return greatGrandParent instanceof PsiCodeBlock || greatGrandParent instanceof PsiCodeFragment;
    }
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
    if (grandParent instanceof PsiParameterList || grandParent instanceof PsiArrayInitializerMemberValue ||
        grandParent instanceof PsiExpressionList || grandParent instanceof PsiCaseLabelElementList ||
        grandParent instanceof PsiTypeParameterList || grandParent instanceof PsiResourceList ||
        grandParent instanceof PsiResourceExpression || grandParent instanceof PsiArrayInitializerExpression) {
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
    if (!(parent instanceof PsiMember member) || parent instanceof PsiTypeParameter) {
      return false;
    }
    final PsiClass aClass = member.getContainingClass();
    if (aClass == null) {
      return false;
    }
    @NonNls final String name = aClass.getName();
    return name != null && !"_Dummy_".equals(name);
  }

  private static boolean isCompleteStatement(PsiExpressionStatement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiForStatement && ((PsiForStatement)parent).getUpdate() == element) {
      return true;
    }
    PsiElement lastChild = element.getLastChild();
    while (lastChild instanceof PsiComment || lastChild instanceof PsiWhiteSpace) {
      lastChild = lastChild.getPrevSibling();
    }
    return PsiUtil.isJavaToken(lastChild, JavaTokenType.SEMICOLON);
  }
}
