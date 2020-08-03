// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.JavaCompiledPattern;
import com.intellij.structuralsearch.impl.matcher.JavaMatchUtil;
import com.intellij.structuralsearch.impl.matcher.filters.*;
import com.intellij.structuralsearch.impl.matcher.handlers.*;
import com.intellij.structuralsearch.impl.matcher.iterators.DocValuesIterator;
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

/**
 * @author Eugene.Kudelevsky
 */
public class JavaCompilingVisitor extends JavaRecursiveElementWalkingVisitor {
  final GlobalCompilingVisitor myCompilingVisitor;

  @NonNls private static final Pattern COMMENT_PATTERN = Pattern.compile("__\\$_\\w+");
  static final Set<String> excludedKeywords = ContainerUtil.newHashSet(PsiKeyword.CLASS, PsiKeyword.INTERFACE, PsiKeyword.ENUM,
                                                                       PsiKeyword.THROWS, PsiKeyword.EXTENDS, PsiKeyword.IMPLEMENTS);

  public JavaCompilingVisitor(GlobalCompilingVisitor compilingVisitor) {
    myCompilingVisitor = compilingVisitor;
  }

  public void compile(PsiElement[] topLevelElements) {
    final JavaWordOptimizer optimizer = new JavaWordOptimizer();
    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    for (PsiElement element : topLevelElements) {
      element.accept(this);
      element.accept(optimizer);
      pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)));
    }
  }

  private class JavaWordOptimizer extends JavaRecursiveElementWalkingVisitor implements WordOptimizer {

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      final String word = reference.getReferenceName();
      if (!handleWord(word, CODE, myCompilingVisitor.getContext())) return;
      if (reference.isQualified() && isClassFromJavaLangPackage(reference.resolve())) return;
      super.visitReferenceElement(reference);
    }

    private boolean isClassFromJavaLangPackage(PsiElement target) {
      if (!(target instanceof PsiClass)) {
        return false;
      }
      final PsiFile file = target.getContainingFile();
      if (!(file instanceof PsiJavaFile)) {
        return false;
      }
      final PsiJavaFile javaFile = (PsiJavaFile)file;
      return "java.lang".equals(javaFile.getPackageName());
    }

    @Override
    public void visitMethod(PsiMethod method) {
      if (!handleWord(method.getName(), CODE, myCompilingVisitor.getContext())) return;
      super.visitMethod(method);
    }

    @Override
    public void visitVariable(PsiVariable variable) {
      if (!handleWord(variable.getName(), CODE, myCompilingVisitor.getContext())) return;
      super.visitVariable(variable);
    }

    @Override
    public void visitCatchSection(PsiCatchSection section) {
      // check parameter first and skip catch section if count is zero
      final PsiParameter parameter = section.getParameter();
      if (parameter != null && !handleWord(parameter.getName(), CODE, myCompilingVisitor.getContext())) return;
      super.visitCatchSection(section);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      final CompileContext context = myCompilingVisitor.getContext();
      if (!handleWord(aClass.getName(), CODE, context)) return;
      if (aClass.isInterface()) {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(PsiKeyword.INTERFACE, true, CODE, context);
      }
      else if (aClass.isEnum()) {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(PsiKeyword.ENUM, true, CODE, context);
      }
      else {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(PsiKeyword.INTERFACE, false, CODE, context);
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(PsiKeyword.ENUM, false, CODE, context);
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(PsiKeyword.CLASS, true, CODE, context);
      }
      super.visitClass(aClass);
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      final PsiType type = expression.getType();
      if (PsiType.BOOLEAN.equals(type) || PsiType.NULL.equals(type)) {
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
    public List<String> getDescendantsOf(String className, boolean includeSelf, Project project) {
      final SmartList<String> result = new SmartList<>();

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
  public void visitDocTag(PsiDocTag psiDocTag) {
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
      final RegExpPredicate predicate = handler.findRegExpPredicate();
      if (GlobalCompilingVisitor.isSuitablePredicate(predicate, handler)) {
        myCompilingVisitor.processTokenizedName(predicate.getRegExp(), true, COMMENT);
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
        myCompilingVisitor.processTokenizedName(commentText, false, COMMENT);
      }
    }
  }

  @Override
  public void visitExpression(PsiExpression expression) {
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
  public void visitLiteralExpression(PsiLiteralExpression expression) {
    final String text = expression.getText();

    if (StringUtil.isQuotedString(text)) {
      @Nullable final MatchingHandler handler = myCompilingVisitor.processPatternStringWithFragments(text, LITERAL);

      if (PsiType.CHAR.equals(expression.getType()) &&
          (handler instanceof LiteralWithSubstitutionHandler || handler == null && expression.getValue() == null)) {
        throw new MalformedPatternException("Bad character literal");
      }
      if (handler != null) {
        expression.putUserData(CompiledPattern.HANDLER_KEY, handler);
      }
    }
    else {
      if (!PsiType.NULL.equals(expression.getType()) && expression.getValue() == null) {
        throw new MalformedPatternException("Bad literal");
      }
    }
    super.visitLiteralExpression(expression);
  }

  @Override
  public void visitField(PsiField psiField) {
    super.visitField(psiField);
    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final MatchingHandler handler = pattern.getHandler(psiField);

    if (needsSupers(psiField, handler)) {
      assert pattern instanceof JavaCompiledPattern;
      ((JavaCompiledPattern)pattern).setRequestsSuperFields(true);
    }
  }

  @Override
  public void visitMethod(PsiMethod psiMethod) {
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
  public void visitReferenceExpression(PsiReferenceExpression reference) {
    visitElement(reference);
    final PsiElement referenceParent = reference.getParent();

    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final boolean typedVar = pattern.isRealTypedVar(reference) &&
                             reference.getQualifierExpression() == null &&
                             !(referenceParent instanceof PsiExpressionStatement);

    final MatchingHandler handler = pattern.getHandler(reference);
    GlobalCompilingVisitor.setFilter(handler, ExpressionFilter.getInstance());

    // We want to merge qname related to class to find it in any form
    final String referencedName = reference.getReferenceName();

    if (!typedVar && !(handler instanceof SubstitutionHandler)) {
      final PsiElement resolve = reference.resolve();

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
  public void visitBlockStatement(PsiBlockStatement statement) {
    super.visitBlockStatement(statement);
    myCompilingVisitor.setFilterSimple(statement, BlockFilter.getInstance());
  }

  @Override
  public void visitSwitchStatement(PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    myCompilingVisitor.setFilterSimple(statement, e -> e instanceof PsiSwitchBlock);
  }

  @Override
  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    super.visitSwitchLabelStatement(statement);
    myCompilingVisitor.setFilterSimple(statement, e -> e instanceof PsiSwitchLabelStatementBase);
  }

  @Override
  public void visitSwitchLabeledRuleStatement(PsiSwitchLabeledRuleStatement statement) {
    super.visitSwitchLabeledRuleStatement(statement);
    myCompilingVisitor.setFilterSimple(statement, e -> e instanceof PsiSwitchLabelStatementBase);
  }

  @Override
  public void visitVariable(PsiVariable variable) {
    super.visitVariable(variable);
    myCompilingVisitor.setFilterSimple(variable, e -> e instanceof PsiVariable);
  }

  @Override
  public void visitParameter(PsiParameter parameter) {
    super.visitParameter(parameter);
    final PsiElement parent = parameter.getParent();
    if (!(parent instanceof PsiCatchSection)) {
      return;
    }
    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final MatchingHandler handler = pattern.getHandlerSimple(parameter);
    @NonNls final String name = "__catch_" + parent.getTextOffset();
    final SubstitutionHandler substitutionHandler =
      handler instanceof SubstitutionHandler
      ? new SubstitutionHandler(name, false, ((SubstitutionHandler)handler).getMinOccurs(), ((SubstitutionHandler)handler).getMaxOccurs(), true)
      : new SubstitutionHandler(name, false, 1, 1, true);
    pattern.setHandler(parent, substitutionHandler);
  }

  @Override
  public void visitDeclarationStatement(PsiDeclarationStatement psiDeclarationStatement) {
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
                  (myCompilingVisitor.getContext().getPattern().isRealTypedVar(
                    param.getInnermostComponentReferenceElement().getReferenceNameElement()))
              ) {
                myCompilingVisitor.setFilterSimple(param, TypeParameterFilter.getInstance());
              }
            }

            return;
          }
        }
      }
    }
    else if (firstChild instanceof PsiModifierList) {
      final PsiModifierList modifierList = (PsiModifierList)firstChild;
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
  public void visitDocComment(PsiDocComment psiDocComment) {
    super.visitDocComment(psiDocComment);
    myCompilingVisitor.setFilterSimple(psiDocComment, JavaDocFilter.getInstance());
  }

  @Override
  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    super.visitReferenceElement(reference);

    final PsiElement parent = reference.getParent();
    final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(reference);
    if (parent != null && parent.getParent() instanceof PsiClass) {
      GlobalCompilingVisitor.setFilter(handler, TypeFilter.getInstance());
    }
    else if (parent instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)parent;
      if (newExpression.getArrayInitializer() != null) {
        GlobalCompilingVisitor.setFilter(handler, e -> e instanceof PsiJavaCodeReferenceElement || e instanceof PsiKeyword);
      }
      else {
        GlobalCompilingVisitor.setFilter(handler, e -> e instanceof PsiJavaCodeReferenceElement);
      }
    }
  }

  @Override
  public void visitTypeElement(PsiTypeElement type) {
    super.visitTypeElement(type);

    final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(type);
    GlobalCompilingVisitor.setFilter(handler, e -> e instanceof PsiTypeElement);
  }

  @Override
  public void visitClass(PsiClass psiClass) {
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
    final boolean caseSensitive = myCompilingVisitor.getContext().getOptions().isCaseSensitiveMatch();
    substitutionHandler.setPredicate(new RegExpPredicate(StructuralSearchUtil.shieldRegExpMetaChars(referenceText),
                                                         caseSensitive, null, false, false));
    myCompilingVisitor.getContext().getPattern().setHandler(expr, substitutionHandler);
  }

  @Override
  public void visitExpressionStatement(PsiExpressionStatement expressionStatement) {
    super.visitExpressionStatement(expressionStatement);

    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final PsiElement child = expressionStatement.getLastChild();
    final PsiElement parent = expressionStatement.getParent();
    if (!(child instanceof PsiJavaToken) && !(child instanceof PsiComment) && parent instanceof PsiCodeFragment) {
      // search for expression or symbol
      final PsiElement reference = expressionStatement.getFirstChild();
      final MatchingHandler referenceHandler = pattern.getHandler(reference);

      if (referenceHandler instanceof SubstitutionHandler && (reference instanceof PsiReferenceExpression)) {
        // symbol
        pattern.setHandler(expressionStatement, referenceHandler);
        referenceHandler.setFilter(SymbolNodeFilter.getInstance());

        myCompilingVisitor.setHandler(expressionStatement, new SymbolHandler((SubstitutionHandler)referenceHandler));
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
        if (handler instanceof SubstitutionHandler) {
          final SubstitutionHandler substitutionHandler = (SubstitutionHandler)handler;
          if (parent instanceof PsiForStatement &&
              (((PsiForStatement)parent).getInitialization() == expressionStatement ||
               ((PsiForStatement)parent).getUpdate() == expressionStatement)) {
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
  public void visitCodeBlock(PsiCodeBlock block) {
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
    if (element.getParent() instanceof PsiClass && handler instanceof SubstitutionHandler) {
      final SubstitutionHandler handler2 = (SubstitutionHandler)handler;

      return (handler2.isStrictSubtype() || handler2.isSubtype());
    }
    return false;
  }
}
