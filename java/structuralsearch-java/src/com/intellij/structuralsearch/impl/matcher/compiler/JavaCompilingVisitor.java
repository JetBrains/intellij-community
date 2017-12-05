// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.JavaCompiledPattern;
import com.intellij.structuralsearch.impl.matcher.filters.*;
import com.intellij.structuralsearch.impl.matcher.handlers.*;
import com.intellij.structuralsearch.impl.matcher.iterators.DocValuesIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaCompilingVisitor extends JavaRecursiveElementWalkingVisitor {
  final GlobalCompilingVisitor myCompilingVisitor;

  @NonNls private static final String COMMENT = "\\s*(__\\$_\\w+)\\s*";
  private static final Pattern ourPattern = Pattern.compile("//" + COMMENT, Pattern.DOTALL);
  private static final Pattern ourPattern2 = Pattern.compile("/\\*" + COMMENT + "\\*/", Pattern.DOTALL);
  private static final Pattern ourPattern3 = Pattern.compile("/\\*\\*" + COMMENT + "\\*/", Pattern.DOTALL);

  static final Set<String> excludedKeywords = ContainerUtil.newHashSet(PsiKeyword.CLASS, PsiKeyword.INTERFACE, PsiKeyword.ENUM,
                                                                               PsiKeyword.THROWS, PsiKeyword.EXTENDS, PsiKeyword.IMPLEMENTS);

  public JavaCompilingVisitor(GlobalCompilingVisitor compilingVisitor) {
    this.myCompilingVisitor = compilingVisitor;
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
      if (!handleWord(reference.getReferenceName(), myCompilingVisitor.getContext())) return;
      super.visitReferenceElement(reference);
    }

    @Override
    public void visitMethod(PsiMethod method) {
      if (!handleWord(method.getName(), myCompilingVisitor.getContext())) return;
      super.visitMethod(method);
    }

    @Override
    public void visitVariable(PsiVariable variable) {
      if (!handleWord(variable.getName(), myCompilingVisitor.getContext())) return;
      super.visitVariable(variable);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      if (!handleWord(aClass.getName(), myCompilingVisitor.getContext())) return;
      super.visitClass(aClass);
    }

    @Override
    public void visitElement(PsiElement element) {
      super.visitElement(element);
      if (element instanceof PsiMethodReferenceExpression) {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord("::", true, GlobalCompilingVisitor.OccurenceKind.CODE,
                                                            myCompilingVisitor.getContext());
      }
      else if (element instanceof PsiLambdaExpression) {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord("->", true, GlobalCompilingVisitor.OccurenceKind.CODE,
                                                            myCompilingVisitor.getContext());
      }
      else if (element instanceof PsiKeyword) {
        final String keyword = element.getText();
        if (!excludedKeywords.contains(keyword)) {
          GlobalCompilingVisitor.addFilesToSearchForGivenWord(keyword, true, GlobalCompilingVisitor.OccurenceKind.CODE,
                                                              myCompilingVisitor.getContext());
        }
      }
    }

    public List<String> getDescendantsOf(String className, boolean includeSelf, Project project) {
      SmartList<String> result = new SmartList<>();

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
          if (name != null) result.add(name);;
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

    NodeIterator sons = new DocValuesIterator(psiDocTag.getFirstChild());
    while (sons.hasNext()) {
      myCompilingVisitor.setHandler(sons.current(), new DocDataHandler());
      sons.advance();
    }
  }

  @Override
  public void visitComment(PsiComment comment) {
    super.visitComment(comment);

    final String text = comment.getText();
    Matcher matcher = ourPattern.matcher(text);
    boolean matches = false;
    if (!matcher.matches()) {
      matcher = ourPattern2.matcher(text);

      if (!matcher.matches()) {
        matcher = ourPattern3.matcher(text);
      }
      else {
        matches = true;
      }
    }
    else {
      matches = true;
    }

    if (matches || matcher.matches()) {
      String str = matcher.group(1);
      comment.putUserData(CompiledPattern.HANDLER_KEY, str);

      GlobalCompilingVisitor.setFilter(
        myCompilingVisitor.getContext().getPattern().getHandler(comment),
        CommentFilter.getInstance()
      );

      SubstitutionHandler handler = (SubstitutionHandler)myCompilingVisitor.getContext().getPattern().getHandler(str);
      if (handler == null) {
        throw new MalformedPatternException();
      }

      if (handler.getPredicate() != null) {
        ((RegExpPredicate)handler.getPredicate()).setMultiline(true);
      }

      RegExpPredicate predicate = handler.findRegExpPredicate();
      if (GlobalCompilingVisitor.isSuitablePredicate(predicate, handler)) {
        myCompilingVisitor.processTokenizedName(predicate.getRegExp(), true, GlobalCompilingVisitor.OccurenceKind.COMMENT);
      }

      matches = true;
    }

    if (!matches) {
      MatchingHandler handler = myCompilingVisitor.processPatternStringWithFragments(text, GlobalCompilingVisitor.OccurenceKind.COMMENT);
      if (handler != null) comment.putUserData(CompiledPattern.HANDLER_KEY, handler);
    }
  }

  @Override
  public void visitLiteralExpression(PsiLiteralExpression expression) {
    String text = expression.getText();

    if (StringUtil.isQuotedString(text)) {
      @Nullable MatchingHandler handler =
        myCompilingVisitor.processPatternStringWithFragments(text, GlobalCompilingVisitor.OccurenceKind.LITERAL);

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
    CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final MatchingHandler handler = pattern.getHandler(psiField);

    if (needsSupers(psiField, handler)) {
      assert pattern instanceof JavaCompiledPattern;
      ((JavaCompiledPattern)pattern).setRequestsSuperFields(true);
    }
  }

  @Override
  public void visitMethod(PsiMethod psiMethod) {
    super.visitMethod(psiMethod);
    CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
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

    boolean typedVarProcessed = false;
    final PsiElement referenceParent = reference.getParent();

    if ((myCompilingVisitor.getContext().getPattern().isRealTypedVar(reference)) &&
        reference.getQualifierExpression() == null &&
        !(referenceParent instanceof PsiExpressionStatement)
      ) {
      // typed var for expression (but not top level)
      MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(reference);
      GlobalCompilingVisitor.setFilter(handler, ExpressionFilter.getInstance());
      typedVarProcessed = true;
    }

    MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(reference);

    // We want to merge qname related to class to find it in any form
    final String referencedName = reference.getReferenceName();

    if (!typedVarProcessed && !(handler instanceof SubstitutionHandler)) {
      final PsiElement resolve = reference.resolve();

      PsiElement referenceQualifier = reference.getQualifier();
      if (resolve instanceof PsiClass ||
          (resolve == null &&
           ((referencedName != null && Character.isUpperCase(referencedName.charAt(0))) ||
            referenceQualifier == null
           )
          )
        ) {
        boolean hasNoNestedSubstitutionHandlers = false;
        PsiExpression qualifier;
        PsiReferenceExpression currentReference = reference;

        while ((qualifier = currentReference.getQualifierExpression()) != null) {
          if (!(qualifier instanceof PsiReferenceExpression) ||
              myCompilingVisitor.getContext().getPattern().getHandler(qualifier) instanceof SubstitutionHandler
            ) {
            hasNoNestedSubstitutionHandlers = true;
            break;
          }
          currentReference = (PsiReferenceExpression)qualifier;
        }
        if (!hasNoNestedSubstitutionHandlers && PsiTreeUtil.getChildOfType(reference, PsiAnnotation.class) == null) {
          createAndSetSubstitutionHandlerFromReference(
            reference,
            resolve != null ? ((PsiClass)resolve).getQualifiedName() : reference.getText(),
            referenceParent instanceof PsiReferenceExpression
          );
        }
      }
      else if (referenceQualifier != null && reference.getParent() instanceof PsiExpressionStatement) {
        //Handler qualifierHandler = context.pattern.getHandler(referenceQualifier);
        //if (qualifierHandler instanceof SubstitutionHandler &&
        //    !context.pattern.isRealTypedVar(reference)
        //   ) {
        //  createAndSetSubstitutionHandlerFromReference(reference, referencedName);
        //
        //  SubstitutionHandler substitutionHandler = (SubstitutionHandler)qualifierHandler;
        //  RegExpPredicate expPredicate = Handler.getSimpleRegExpPredicate(substitutionHandler);
        //  //if (expPredicate != null)
        //  //  substitutionHandler.setPredicate(new ExprTypePredicate(expPredicate.getRegExp(), null, true, true, false));
        //}
      }
    }
  }

  @Override
  public void visitBlockStatement(PsiBlockStatement psiBlockStatement) {
    super.visitBlockStatement(psiBlockStatement);
    myCompilingVisitor.setFilterSimple(psiBlockStatement, BlockFilter.getInstance());
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
    final String name = "__catch_" + parent.getTextOffset();
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

      if (reference != null && reference.getParameterList().getTypeParameterElements().length > 0) {
        myCompilingVisitor.setHandler(psiDeclarationStatement, new TypedSymbolHandler());
        // typed symbol
        myCompilingVisitor.setFilterSimple(psiDeclarationStatement, TypedSymbolNodeFilter.getInstance());

        final PsiTypeElement[] params = reference.getParameterList().getTypeParameterElements();
        for (PsiTypeElement param : params) {
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

    final MatchingHandler handler = new DeclarationStatementHandler();
    myCompilingVisitor.getContext().getPattern().setHandler(psiDeclarationStatement, handler);
    final PsiElement previousNonWhiteSpace = PsiTreeUtil.skipWhitespacesBackward(psiDeclarationStatement);

    if (previousNonWhiteSpace instanceof PsiComment) {
      ((DeclarationStatementHandler)handler).setCommentHandler(myCompilingVisitor.getContext().getPattern().getHandler(previousNonWhiteSpace));
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
    if (parent != null && parent.getParent() instanceof PsiClass) {
      GlobalCompilingVisitor.setFilter(myCompilingVisitor.getContext().getPattern().getHandler(reference), TypeFilter.getInstance());
    }
  }

  @Override
  public void visitClass(PsiClass psiClass) {
    super.visitClass(psiClass);

    final CompileContext context = myCompilingVisitor.getContext();
    final CompiledPattern pattern = context.getPattern();
    final MatchingHandler handler = pattern.getHandler(psiClass);

    if (needsSupers(psiClass, handler)) {
      ((JavaCompiledPattern)pattern).setRequestsSuperInners(true);
    }

    GlobalCompilingVisitor.setFilter(handler, ClassFilter.getInstance());

    if (!(handler instanceof SubstitutionHandler) || ((SubstitutionHandler)handler).getMinOccurs() > 0) {
      if (psiClass.isInterface()) {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(PsiKeyword.INTERFACE, true, GlobalCompilingVisitor.OccurenceKind.CODE, context);
      }
      else if (psiClass.isEnum()) {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(PsiKeyword.ENUM, true, GlobalCompilingVisitor.OccurenceKind.CODE, context);
      }
      else {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(PsiKeyword.INTERFACE, false, GlobalCompilingVisitor.OccurenceKind.CODE, context);
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(PsiKeyword.ENUM, false, GlobalCompilingVisitor.OccurenceKind.CODE, context);
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(PsiKeyword.CLASS, true, GlobalCompilingVisitor.OccurenceKind.CODE, context);
      }
    }
  }

  private void createAndSetSubstitutionHandlerFromReference(final PsiElement expr, final String referenceText, boolean classQualifier) {
    final SubstitutionHandler substitutionHandler =
      new SubstitutionHandler("__" + referenceText.replace('.', '_'), false, classQualifier ? 0 : 1, 1, false);
    final boolean caseSensitive = myCompilingVisitor.getContext().getOptions().isCaseSensitiveMatch();
    substitutionHandler.setPredicate(new RegExpPredicate(StructuralSearchUtil.shieldRegExpMetaChars(referenceText),
                                                         caseSensitive, null, false, false));
    myCompilingVisitor.getContext().getPattern().setHandler(expr, substitutionHandler);
  }

  @Override
  public void visitExpressionStatement(PsiExpressionStatement expressionStatement) {
    super.visitExpressionStatement(expressionStatement);

    final PsiElement child = expressionStatement.getLastChild();
    if (!(child instanceof PsiJavaToken) && !(child instanceof PsiComment)) {
      // search for expression or symbol
      final PsiElement reference = expressionStatement.getFirstChild();
      final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
      MatchingHandler referenceHandler = pattern.getHandler(reference);

      if (referenceHandler instanceof SubstitutionHandler && (reference instanceof PsiReferenceExpression)) {
        // symbol
        pattern.setHandler(expressionStatement, referenceHandler);
        referenceHandler.setFilter(SymbolNodeFilter.getInstance());

        myCompilingVisitor.setHandler(expressionStatement, new SymbolHandler((SubstitutionHandler)referenceHandler));
      }
      else if (reference instanceof PsiLiteralExpression) {
        MatchingHandler handler = new ExpressionHandler();
        myCompilingVisitor.setHandler(expressionStatement, handler);
        handler.setFilter(ConstantFilter.getInstance());
      }
      else {
        // just expression
        MatchingHandler handler = new ExpressionHandler();
        myCompilingVisitor.setHandler(expressionStatement, handler);

        handler.setFilter(ExpressionFilter.getInstance());
      }
    }
    else {
      final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
      if (expressionStatement.getExpression() instanceof PsiReferenceExpression && pattern.isRealTypedVar(expressionStatement)) {
        // search for statement
        final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(expressionStatement);
        if (handler instanceof SubstitutionHandler) {
          final SubstitutionHandler substitutionHandler = (SubstitutionHandler)handler;
          substitutionHandler.setFilter(new StatementFilter());
          substitutionHandler.setMatchHandler(new StatementHandler());
        }
      }
    }
  }

  @Override
  public void visitElement(PsiElement element) {
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
