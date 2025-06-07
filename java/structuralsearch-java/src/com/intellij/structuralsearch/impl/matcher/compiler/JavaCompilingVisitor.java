// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchUtil;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.JavaCompiledPattern;
import com.intellij.structuralsearch.impl.matcher.JavaMatchUtil;
import com.intellij.structuralsearch.impl.matcher.filters.*;
import com.intellij.structuralsearch.impl.matcher.handlers.*;
import com.intellij.structuralsearch.impl.matcher.iterators.DocValuesIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.ExprTypePredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor.OccurenceKind.*;

public class JavaCompilingVisitor extends JavaRecursiveElementWalkingVisitor {
  private final @NotNull GlobalCompilingVisitor myCompilingVisitor;

  private static final @NonNls Pattern COMMENT_PATTERN = Pattern.compile("__\\$_\\w+");
  private static final Set<String> excludedKeywords = ContainerUtil.newHashSet(JavaKeywords.CLASS, JavaKeywords.INTERFACE, JavaKeywords.ENUM,
                                                                               JavaKeywords.THROWS, JavaKeywords.EXTENDS, JavaKeywords.IMPLEMENTS);

  public JavaCompilingVisitor(@NotNull GlobalCompilingVisitor compilingVisitor) {
    myCompilingVisitor = compilingVisitor;
  }

  public void compile(PsiElement @NotNull [] topLevelElements) {
    final CompileContext context = myCompilingVisitor.getContext();

    // When dumb the index is not used while editing pattern (e.g. no warning when zero hits in project).
    final JavaWordOptimizer optimizer = DumbService.isDumb(context.getProject()) ? null : new JavaWordOptimizer();
    final CompiledPattern pattern = context.getPattern();
    for (PsiElement element : topLevelElements) {
      element.accept(this);
      if (optimizer != null) element.accept(optimizer);
      pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)));
    }
  }

  private class JavaWordOptimizer extends JavaRecursiveElementWalkingVisitor implements WordOptimizer {

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      final String word = reference.getReferenceName();
      final PsiElement target = reference.resolve();
      if (target == null && Strings.isCapitalized(word)) {
        return;
      }
      if (handleWord(word, CODE, myCompilingVisitor.getContext())) {
        if (!isStaticAccessibleFromSubclass(target) && (!reference.isQualified() || !isClassFromJavaLangPackage(target))) {
          super.visitReferenceElement(reference);
        }
      }
    }

    private static boolean isStaticAccessibleFromSubclass(PsiElement element) {
      if (!(element instanceof PsiMember member) || !member.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      final PsiClass aClass = member.getContainingClass();
      return aClass == null || (!aClass.isInterface() && !aClass.hasModifierProperty(PsiModifier.FINAL));
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    private static boolean isClassFromJavaLangPackage(PsiElement target) {
      return target instanceof PsiClass &&
             target.getContainingFile() instanceof PsiJavaFile javaFile &&
             "java.lang".equals(javaFile.getPackageName());
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!handleWord(method.getName(), CODE, myCompilingVisitor.getContext())) return;
      super.visitMethod(method);
    }

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      if (!handleWord(variable.getName(), CODE, myCompilingVisitor.getContext())) return;
      super.visitVariable(variable);
    }

    @Override
    public void visitCatchSection(@NotNull PsiCatchSection section) {
      // check parameter first and skip catch section if count is zero
      final PsiParameter parameter = section.getParameter();
      if (parameter != null && !handleWord(parameter.getName(), CODE, myCompilingVisitor.getContext())) return;
      super.visitCatchSection(section);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final CompileContext context = myCompilingVisitor.getContext();
      if (!handleWord(aClass.getName(), CODE, context)) return;
      if (aClass.isInterface()) {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(JavaKeywords.INTERFACE, true, CODE, context);
      }
      else if (aClass.isEnum()) {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(JavaKeywords.ENUM, true, CODE, context);
      }
      else if (aClass.isRecord()) {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(JavaKeywords.RECORD, true, CODE, context);
      }
      else {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(JavaKeywords.INTERFACE, false, CODE, context);
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(JavaKeywords.ENUM, false, CODE, context);
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(JavaKeywords.RECORD, false, CODE, context);
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(JavaKeywords.CLASS, true, CODE, context);
      }
      super.visitClass(aClass);
    }

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      final PsiType type = expression.getType();
      if (PsiTypes.booleanType().equals(type) || PsiTypes.nullType().equals(type)) {
        // don't search index for literals of other types, as they can be written in many many kinds of ways for the same value.
        if (!handleWord(expression.getText(), CODE, myCompilingVisitor.getContext())) return;
      }
      super.visitLiteralExpression(expression);
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      super.visitElement(element);
      if (element instanceof PsiMethodReferenceExpression) {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord("::", true, CODE, myCompilingVisitor.getContext());
      }
      else if (element instanceof PsiLambdaExpression) {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord("->", true, CODE, myCompilingVisitor.getContext());
      }
      else if (element instanceof PsiKeyword) {
        final String keyword = element.getText();
        if (!excludedKeywords.contains(keyword) || element.getParent() instanceof PsiExpression) {
          GlobalCompilingVisitor.addFilesToSearchForGivenWord(keyword, true, CODE, myCompilingVisitor.getContext());
        }
      }
    }

    @Override
    public @NotNull List<String> getDescendantsOf(@NotNull String className, boolean includeSelf, @NotNull Project project) {
      final List<String> result = new SmartList<>();

      // use project and libraries scope, because super class may be outside the scope of the search
      final GlobalSearchScope projectAndLibraries = ProjectScope.getAllScope(project);
      final PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(className, projectAndLibraries);
      if (classes.length == 0) {
        // to fail fast with "does not match anything in scope" result on unknown class name
        result.add(className);
        return result;
      }
      for (PsiClass aClass : classes) {
        if (includeSelf) {
          final String name = aClass.getName();
          if (name != null) result.add(name);
        }
        ClassInheritorsSearch.search(aClass, projectAndLibraries, true).forEach(c -> {
          final String name = c.getName();
          if (name != null) result.add(name);
          return true;
        });
      }
      return result;
    }
  }

  @Override
  public void visitDocTag(@NotNull PsiDocTag psiDocTag) {
    super.visitDocTag(psiDocTag);

    final NodeIterator nodes = new DocValuesIterator(psiDocTag.getFirstChild());
    while (nodes.hasNext()) {
      myCompilingVisitor.setHandler(nodes.current(), new DocDataHandler());
      nodes.advance();
    }
  }

  @Override
  public void visitComment(@NotNull PsiComment comment) {
    super.visitComment(comment);

    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    GlobalCompilingVisitor.setFilter(pattern.getHandler(comment), CommentFilter.getInstance());
    final String commentText = JavaMatchUtil.getCommentText(comment).trim();
    if (COMMENT_PATTERN.matcher(commentText).matches()) {
      final SubstitutionHandler handler = (SubstitutionHandler)pattern.getHandler(commentText);
      if (handler == null) {
        throw new MalformedPatternException();
      }

      comment.putUserData(CompiledPattern.HANDLER_KEY, handler);
      final RegExpPredicate predicate = handler.findPredicate(RegExpPredicate.class);
      if (GlobalCompilingVisitor.isSuitablePredicate(predicate, handler)) {
        myCompilingVisitor.processTokenizedName(predicate.getRegExp(), COMMENT);
      }
    }
    else if (!commentText.isEmpty()) {
      if (myCompilingVisitor.hasFragments(commentText)) {
        final MatchingHandler handler = myCompilingVisitor.processPatternStringWithFragments(
          comment instanceof PsiDocComment ? comment.getText() : JavaMatchUtil.getCommentText(comment).trim(),
          COMMENT);
        if (handler != null) comment.putUserData(CompiledPattern.HANDLER_KEY, handler);
      }
      else {
        myCompilingVisitor.processTokenizedName(commentText, COMMENT);
      }
    }
  }

  @Override
  public void visitExpression(@NotNull PsiExpression expression) {
    super.visitExpression(expression);
    if (!(expression.getParent() instanceof PsiExpressionStatement) && !(expression instanceof PsiParenthesizedExpression)) {
      final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(expression);
      if (handler.getFilter() == null) {
        handler.setFilter(e -> DefaultFilter.accepts(expression,
                                                     (e instanceof PsiExpression) ? PsiUtil.skipParenthesizedExprDown((PsiExpression)e) : e));
      }
    }
  }

  @Override
  public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
    final String text = expression.getText();

    if (StringUtil.isQuotedString(text)) {
      final @Nullable MatchingHandler handler = myCompilingVisitor.processPatternStringWithFragments(text, LITERAL);

      if (PsiTypes.charType().equals(expression.getType()) &&
          (handler instanceof LiteralWithSubstitutionHandler || handler == null && expression.getValue() == null)) {
        throw new MalformedPatternException(SSRBundle.message("error.bad.character.literal"));
      }
      if (handler != null) {
        expression.putUserData(CompiledPattern.HANDLER_KEY, handler);
      }
    }
    else {
      if (!PsiTypes.nullType().equals(expression.getType()) && expression.getValue() == null) {
        throw new MalformedPatternException(SSRBundle.message("error.bad.literal"));
      }
    }
    super.visitLiteralExpression(expression);
  }

  @Override
  public void visitField(@NotNull PsiField psiField) {
    super.visitField(psiField);
    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final MatchingHandler handler = pattern.getHandler(psiField);

    if (needsSupers(psiField, handler)) {
      assert pattern instanceof JavaCompiledPattern;
      ((JavaCompiledPattern)pattern).setRequestsSuperFields(true);
    }
  }

  @Override
  public void visitMethod(@NotNull PsiMethod psiMethod) {
    super.visitMethod(psiMethod);
    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final MatchingHandler handler = pattern.getHandler(psiMethod);

    if (needsSupers(psiMethod, handler)) {
      assert pattern instanceof JavaCompiledPattern;
      ((JavaCompiledPattern)pattern).setRequestsSuperMethods(true);
    }

    GlobalCompilingVisitor.setFilter(handler, MethodFilter.getInstance());
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression reference) {
    visitElement(reference);
    final PsiElement referenceParent = reference.getParent();

    final CompileContext context = myCompilingVisitor.getContext();
    final CompiledPattern pattern = context.getPattern();
    final boolean typedVar = pattern.isRealTypedVar(reference) &&
                             reference.getQualifierExpression() == null &&
                             !(referenceParent instanceof PsiExpressionStatement);

    final MatchingHandler handler = pattern.getHandler(reference);
    if (reference.getParent() instanceof PsiLambdaExpression) {
      GlobalCompilingVisitor.setFilter(handler, element -> true);
    }
    else {
      GlobalCompilingVisitor.setFilter(handler, ExpressionFilter.getInstance());
    }

    // We want to merge qname related to class to find it in any form
    final String referencedName = reference.getReferenceName();

    if (!typedVar && !(handler instanceof SubstitutionHandler)) {
      // When in dumb mode fall back to first character of name is upper case heuristic to identify classes.
      // Which is basically already always used when the referenced class is not in java.lang,
      // because SSR patterns have no imports so resolve will return null.
      final PsiElement resolve = DumbService.isDumb(context.getProject()) ? null : reference.resolve();

      final PsiElement referenceQualifier = reference.getQualifier();
      if (resolve instanceof PsiClass ||
          resolve == null && (referencedName != null && Character.isUpperCase(referencedName.charAt(0)) || referenceQualifier == null)) {
        boolean hasNoNestedSubstitutionHandlers = false;
        PsiExpression qualifier;
        PsiReferenceExpression currentReference = reference;

        while ((qualifier = currentReference.getQualifierExpression()) != null) {
          if (!(qualifier instanceof PsiReferenceExpression) || pattern.getHandler(qualifier) instanceof SubstitutionHandler) {
            hasNoNestedSubstitutionHandlers = true;
            break;
          }
          currentReference = (PsiReferenceExpression)qualifier;
        }
        if (!hasNoNestedSubstitutionHandlers && PsiTreeUtil.getChildOfType(reference, PsiAnnotation.class) == null) {
          final String text;
          if (resolve != null) {
            final String fqName = ((PsiClass)resolve).getQualifiedName();
            text = (fqName == null) ? reference.getText() : fqName;
          }
          else {
            text = reference.getText();
          }
          createAndSetSubstitutionHandlerFromReference(reference, text, referenceParent instanceof PsiReferenceExpression);
        }
      }
    }
  }

  @Override
  public void visitBlockStatement(@NotNull PsiBlockStatement statement) {
    super.visitBlockStatement(statement);
    myCompilingVisitor.setFilterSimple(statement, BlockFilter.getInstance());
  }

  @Override
  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    myCompilingVisitor.setFilterSimple(statement, e -> e instanceof PsiSwitchBlock);
  }

  @Override
  public void visitSwitchLabelStatement(@NotNull PsiSwitchLabelStatement statement) {
    super.visitSwitchLabelStatement(statement);
    myCompilingVisitor.setFilterSimple(statement, e -> e instanceof PsiSwitchLabelStatementBase);
  }

  @Override
  public void visitSwitchLabeledRuleStatement(@NotNull PsiSwitchLabeledRuleStatement statement) {
    super.visitSwitchLabeledRuleStatement(statement);
    myCompilingVisitor.setFilterSimple(statement, e -> e instanceof PsiSwitchLabelStatementBase);
  }

  @Override
  public void visitVariable(@NotNull PsiVariable variable) {
    super.visitVariable(variable);
    myCompilingVisitor.setFilterSimple(variable, e -> e instanceof PsiVariable);
  }

  @Override
  public void visitParameter(@NotNull PsiParameter parameter) {
    super.visitParameter(parameter);
    final PsiElement parent = parameter.getParent();
    if (!(parent instanceof PsiCatchSection)) {
      return;
    }
    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final MatchingHandler handler = pattern.getHandlerSimple(parameter);
    final @NonNls String name = "__catch_" + parent.getTextOffset();
    final SubstitutionHandler substitutionHandler;
    if (handler instanceof SubstitutionHandler parameterHandler) {
      substitutionHandler =
        new SubstitutionHandler(name, false, parameterHandler.getMinOccurs(),
                                parameterHandler.isTarget() ? Integer.MAX_VALUE : parameterHandler.getMaxOccurs(), true);
    }
    else {
      substitutionHandler = new SubstitutionHandler(name, false, 1, 1, true);
    }
    pattern.setHandler(parent, substitutionHandler);
  }

  @Override
  public void visitDeclarationStatement(@NotNull PsiDeclarationStatement psiDeclarationStatement) {
    super.visitDeclarationStatement(psiDeclarationStatement);

    final PsiElement firstChild = psiDeclarationStatement.getFirstChild();
    if (firstChild instanceof PsiTypeElement) {
      // search for expression or symbol
      final PsiJavaCodeReferenceElement reference = ((PsiTypeElement)firstChild).getInnermostComponentReferenceElement();

      if (reference != null) {
        final PsiReferenceParameterList parameterList = reference.getParameterList();
        if (parameterList != null) {
          final PsiTypeElement[] typeParameterElements = parameterList.getTypeParameterElements();
          if (typeParameterElements.length > 0) {
            myCompilingVisitor.setHandler(psiDeclarationStatement, new TypedSymbolHandler());
            // typed symbol
            myCompilingVisitor.setFilterSimple(psiDeclarationStatement, TypedSymbolNodeFilter.getInstance());

            for (PsiTypeElement param : typeParameterElements) {
              if (param.getInnermostComponentReferenceElement() != null &&
                  myCompilingVisitor.getContext().getPattern().isRealTypedVar(
                    param.getInnermostComponentReferenceElement().getReferenceNameElement())) {
                myCompilingVisitor.setFilterSimple(param, TypeParameterFilter.getInstance());
              }
            }

            return;
          }
        }
      }
    }
    else if (firstChild instanceof PsiModifierList modifierList) {
      final PsiAnnotation[] annotations = modifierList.getAnnotations();
      if (annotations.length != 1) {
        throw new MalformedPatternException();
      }
      for (String modifier : PsiModifier.MODIFIERS) {
        if (modifierList.hasExplicitModifier(modifier)) {
          throw new MalformedPatternException();
        }
      }
      myCompilingVisitor.setHandler(psiDeclarationStatement, new AnnotationHandler());
      myCompilingVisitor.setFilterSimple(psiDeclarationStatement, AnnotationFilter.getInstance());
      return;
    }

    final DeclarationStatementHandler handler = new DeclarationStatementHandler();
    myCompilingVisitor.getContext().getPattern().setHandler(psiDeclarationStatement, handler);
    final PsiElement previousNonWhiteSpace = PsiTreeUtil.skipWhitespacesBackward(psiDeclarationStatement);

    if (previousNonWhiteSpace instanceof PsiComment) {
      handler.setCommentHandler(myCompilingVisitor.getContext().getPattern().getHandler(previousNonWhiteSpace));
      myCompilingVisitor.getContext().getPattern().setHandler(previousNonWhiteSpace, handler);
    }

    // detect typed symbol, it will have no variable
    handler.setFilter(DeclarationFilter.getInstance());
  }

  @Override
  public void visitDocComment(@NotNull PsiDocComment psiDocComment) {
    super.visitDocComment(psiDocComment);
    myCompilingVisitor.setFilterSimple(psiDocComment, JavaDocFilter.getInstance());
  }

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
    super.visitReferenceElement(reference);

    final PsiElement parent = reference.getParent();
    final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(reference);
    if (parent != null && parent.getParent() instanceof PsiClass) {
      GlobalCompilingVisitor.setFilter(handler, TypeFilter.getInstance());
    }
    else if (parent instanceof PsiNewExpression newExpression) {
      if (newExpression.isArrayCreation()) {
        GlobalCompilingVisitor.setFilter(handler, e -> e instanceof PsiJavaCodeReferenceElement || e instanceof PsiKeyword);
      }
      else {
        GlobalCompilingVisitor.setFilter(handler, e -> e instanceof PsiJavaCodeReferenceElement);
      }
    }
    else if (!(parent instanceof PsiAnnotation)) {
      GlobalCompilingVisitor.setFilter(handler, e -> e instanceof PsiJavaCodeReferenceElement);
    }
  }

  @Override
  public void visitTypeElement(@NotNull PsiTypeElement type) {
    super.visitTypeElement(type);

    final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(type);
    GlobalCompilingVisitor.setFilter(handler, e -> e instanceof PsiTypeElement);
  }

  @Override
  public void visitClass(@NotNull PsiClass psiClass) {
    super.visitClass(psiClass);

    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final MatchingHandler handler = pattern.getHandler(psiClass);

    if (needsSupers(psiClass, handler)) {
      ((JavaCompiledPattern)pattern).setRequestsSuperInners(true);
    }

    GlobalCompilingVisitor.setFilter(handler, ClassFilter.getInstance());
  }

  private void createAndSetSubstitutionHandlerFromReference(final PsiElement expr, final String referenceText, boolean classQualifier) {
    final SubstitutionHandler substitutionHandler =
      new SubstitutionHandler("__" + referenceText.replace('.', '_'), false, classQualifier ? 0 : 1, 1, true);
    if (classQualifier) substitutionHandler.setSubtype(true);
    final boolean caseSensitive = myCompilingVisitor.getContext().getOptions().isCaseSensitiveMatch();
    substitutionHandler.setPredicate(new RegExpPredicate(MatchUtil.shieldRegExpMetaChars(referenceText),
                                                         caseSensitive, null, false, false));
    myCompilingVisitor.getContext().getPattern().setHandler(expr, substitutionHandler);
  }

  @Override
  public void visitExpressionStatement(@NotNull PsiExpressionStatement expressionStatement) {
    super.visitExpressionStatement(expressionStatement);

    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final PsiElement child = expressionStatement.getLastChild();
    final PsiElement parent = expressionStatement.getParent();
    if (!(child instanceof PsiJavaToken) && !(child instanceof PsiComment) && parent instanceof PsiCodeFragment) {
      // search for expression or symbol
      final PsiElement reference = expressionStatement.getFirstChild();
      final MatchingHandler referenceHandler = pattern.getHandler(reference);

      if (referenceHandler instanceof SubstitutionHandler substitutionHandler &&
          substitutionHandler.findPredicate(ExprTypePredicate.class) == null &&
          reference instanceof PsiReferenceExpression) {
        // symbol
        pattern.setHandler(expressionStatement, referenceHandler);
        referenceHandler.setFilter(SymbolNodeFilter.getInstance());

        myCompilingVisitor.setHandler(expressionStatement, new SymbolHandler(substitutionHandler));
      }
      else if (reference instanceof PsiLiteralExpression) {
        final MatchingHandler handler = new ExpressionHandler();
        myCompilingVisitor.setHandler(expressionStatement, handler);
        handler.setFilter(ConstantFilter.getInstance());
      }
      else {
        // just expression
        final MatchingHandler handler = new ExpressionHandler();
        myCompilingVisitor.setHandler(expressionStatement, handler);

        handler.setFilter(ExpressionFilter.getInstance());
      }
    }
    else {
      if (expressionStatement.getExpression() instanceof PsiReferenceExpression && pattern.isRealTypedVar(expressionStatement)) {
        // search for statement
        final MatchingHandler handler = pattern.getHandler(expressionStatement);
        if (handler instanceof SubstitutionHandler substitutionHandler) {
          if (parent instanceof PsiForStatement forStatement &&
              (forStatement.getInitialization() == expressionStatement ||
               forStatement.getUpdate() == expressionStatement)) {
            substitutionHandler.setFilter(e -> e instanceof PsiExpression || e instanceof PsiExpressionListStatement ||
                                               e instanceof PsiDeclarationStatement || e instanceof PsiEmptyStatement);
          }
          else {
            substitutionHandler.setFilter(new StatementFilter());
            substitutionHandler.setMatchHandler(new StatementHandler());
          }
        }
      }
    }
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    myCompilingVisitor.handle(element);
    super.visitElement(element);
  }

  @Override
  public void visitCodeBlock(@NotNull PsiCodeBlock block) {
    for (PsiElement el = block.getFirstChild(); el != null; el = el.getNextSibling()) {
      if (GlobalCompilingVisitor.getFilter().accepts(el)) {
        if (el instanceof PsiWhiteSpace) {
          myCompilingVisitor.addLexicalNode(el);
        }
      }
      else {
        el.accept(this);
      }
    }
  }

  private static boolean needsSupers(final PsiElement element, final MatchingHandler handler) {
    if (element.getParent() instanceof PsiClass && handler instanceof SubstitutionHandler handler2) {
      return handler2.isStrictSubtype() || handler2.isSubtype();
    }
    return false;
  }
}
