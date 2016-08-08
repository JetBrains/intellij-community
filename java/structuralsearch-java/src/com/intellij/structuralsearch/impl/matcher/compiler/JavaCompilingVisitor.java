/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.JavaCompiledPattern;
import com.intellij.structuralsearch.impl.matcher.filters.*;
import com.intellij.structuralsearch.impl.matcher.handlers.*;
import com.intellij.structuralsearch.impl.matcher.iterators.DocValuesIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.structuralsearch.impl.matcher.strategies.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaCompilingVisitor extends JavaRecursiveElementWalkingVisitor {
  private final GlobalCompilingVisitor myCompilingVisitor;

  @NonNls private static final String COMMENT = "\\s*(__\\$_\\w+)\\s*";
  private static final Pattern ourPattern = Pattern.compile("//" + COMMENT, Pattern.DOTALL);
  private static final Pattern ourPattern2 = Pattern.compile("/\\*" + COMMENT + "\\*/", Pattern.DOTALL);
  private static final Pattern ourPattern3 = Pattern.compile("/\\*\\*" + COMMENT + "\\*/", Pattern.DOTALL);

  public JavaCompilingVisitor(GlobalCompilingVisitor compilingVisitor) {
    this.myCompilingVisitor = compilingVisitor;
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

      RegExpPredicate predicate = MatchingHandler.getSimpleRegExpPredicate(handler);
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
    String value = expression.getText();

    if (value.length() > 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
      @Nullable MatchingHandler handler =
        myCompilingVisitor.processPatternStringWithFragments(value, GlobalCompilingVisitor.OccurenceKind.LITERAL);

      if (handler != null) {
        expression.putUserData(CompiledPattern.HANDLER_KEY, handler);
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
    handleReferenceText(psiMethod.getName(), myCompilingVisitor.getContext());
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

    if (!(referenceParent instanceof PsiMethodCallExpression)) {
      handleReference(reference);
    }

    MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(reference);

    // We want to merge qname related to class to find it in any form
    final String referencedName = reference.getReferenceName();

    if (!typedVarProcessed &&
        !(handler instanceof SubstitutionHandler)) {
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
  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    handleReference(expression.getMethodExpression());
    super.visitMethodCallExpression(expression);
  }

  @Override
  public void visitBlockStatement(PsiBlockStatement psiBlockStatement) {
    super.visitBlockStatement(psiBlockStatement);
    myCompilingVisitor.getContext().getPattern().getHandler(psiBlockStatement).setFilter(BlockFilter.getInstance());
  }

  @Override
  public void visitVariable(PsiVariable psiVariable) {
    super.visitVariable(psiVariable);
    myCompilingVisitor.getContext().getPattern().getHandler(psiVariable).setFilter(VariableFilter.getInstance());
    handleReferenceText(psiVariable.getName(), myCompilingVisitor.getContext());
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
        final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(psiDeclarationStatement);
        // typed symbol
        handler.setFilter(TypedSymbolNodeFilter.getInstance());

        final PsiTypeElement[] params = reference.getParameterList().getTypeParameterElements();
        for (PsiTypeElement param : params) {
          if (param.getInnermostComponentReferenceElement() != null &&
              (myCompilingVisitor.getContext().getPattern().isRealTypedVar(
                param.getInnermostComponentReferenceElement().getReferenceNameElement()))
            ) {
            myCompilingVisitor.getContext().getPattern().getHandler(param).setFilter(
              TypeParameterFilter.getInstance()
            );
          }
        }

        return;
      }
    }
    else if (firstChild instanceof PsiModifierList) {
      final PsiModifierList modifierList = (PsiModifierList)firstChild;
      final PsiAnnotation[] annotations = modifierList.getAnnotations();
      if (annotations.length != 1) {
        throw new UnsupportedPatternException("Pattern is malformed");
      }
      for (String modifier : PsiModifier.MODIFIERS) {
        if (modifierList.hasExplicitModifier(modifier)) {
          throw new UnsupportedPatternException("Pattern is malformed");
        }
      }
      myCompilingVisitor.setHandler(psiDeclarationStatement, new AnnotationHandler());
      final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(psiDeclarationStatement);
      handler.setFilter(AnnotationFilter.getInstance());
      return;
    }

    final MatchingHandler handler = new DeclarationStatementHandler();
    myCompilingVisitor.getContext().getPattern().setHandler(psiDeclarationStatement, handler);
    final PsiElement previousNonWhiteSpace = PsiTreeUtil.skipSiblingsBackward(psiDeclarationStatement, PsiWhiteSpace.class);

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
    myCompilingVisitor.getContext().getPattern().getHandler(psiDocComment).setFilter(JavaDocFilter.getInstance());
  }

  @Override
  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    super.visitReferenceElement(reference);

    if (reference.getParent() != null &&
        reference.getParent().getParent() instanceof PsiClass) {
      GlobalCompilingVisitor.setFilter(myCompilingVisitor.getContext().getPattern().getHandler(reference), TypeFilter.getInstance());
    }

    handleReference(reference);
  }

  @Override
  public void visitClass(PsiClass psiClass) {
    super.visitClass(psiClass);

    CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    final MatchingHandler handler = pattern.getHandler(psiClass);

    if (needsSupers(psiClass, handler)) {
      ((JavaCompiledPattern)pattern).setRequestsSuperInners(true);
    }
    handleReferenceText(psiClass.getName(), myCompilingVisitor.getContext());

    GlobalCompilingVisitor.setFilter(handler, ClassFilter.getInstance());

    boolean hasSubstitutionHandler = false;
    for (PsiElement element = psiClass.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiTypeElement && element.getNextSibling() instanceof PsiErrorElement) {
        // found match that
        MatchingHandler unmatchedSubstitutionHandler = pattern.getHandler(element);
        if (unmatchedSubstitutionHandler != null) {
          psiClass.putUserData(JavaCompiledPattern.ALL_CLASS_CONTENT_VAR_NAME_KEY, pattern.getTypedVarString(element));
          hasSubstitutionHandler = true;
        }
      }
    }

    if (!hasSubstitutionHandler) {
      String name = CompiledPattern.ALL_CLASS_UNMATCHED_CONTENT_VAR_ARTIFICIAL_NAME;
      psiClass.putUserData(JavaCompiledPattern.ALL_CLASS_CONTENT_VAR_NAME_KEY, name);
      MatchOptions options = myCompilingVisitor.getContext().getOptions();
      if (pattern.getHandler(name) == null) {
        pattern.createSubstitutionHandler(name, name, false, 0, Integer.MAX_VALUE, true);
        MatchVariableConstraint constraint = new MatchVariableConstraint(true);
        constraint.setName(name);
        constraint.setMinCount(0);
        constraint.setMaxCount(Integer.MAX_VALUE);
        options.addVariableConstraint(constraint);
      }
    }
  }

  private SubstitutionHandler createAndSetSubstitutionHandlerFromReference(final PsiElement expr, final String referenceText,
                                                                           boolean classQualifier) {
    final SubstitutionHandler substitutionHandler =
      new SubstitutionHandler("__" + referenceText.replace('.', '_'), false, classQualifier ? 0 : 1, 1, false);
    final boolean caseSensitive = myCompilingVisitor.getContext().getOptions().isCaseSensitiveMatch();
    substitutionHandler.setPredicate(new RegExpPredicate(referenceText.replaceAll("\\.", "\\\\."), caseSensitive, null, false, false));
    myCompilingVisitor.getContext().getPattern().setHandler(expr, substitutionHandler);
    return substitutionHandler;
  }

  @Override
  public void visitExpressionStatement(PsiExpressionStatement expr) {
    myCompilingVisitor.handle(expr);

    super.visitExpressionStatement(expr);

    final PsiElement child = expr.getLastChild();
    if (!(child instanceof PsiJavaToken) && !(child instanceof PsiComment)) {
      // search for expression or symbol
      final PsiElement reference = expr.getFirstChild();
      MatchingHandler referenceHandler = myCompilingVisitor.getContext().getPattern().getHandler(reference);

      if (referenceHandler instanceof SubstitutionHandler &&
          (reference instanceof PsiReferenceExpression)
        ) {
        // symbol
        myCompilingVisitor.getContext().getPattern().setHandler(expr, referenceHandler);
        referenceHandler.setFilter(
          SymbolNodeFilter.getInstance()
        );

        myCompilingVisitor.setHandler(expr, new SymbolHandler((SubstitutionHandler)referenceHandler));
      }
      else if (reference instanceof PsiLiteralExpression) {
        MatchingHandler handler = new ExpressionHandler();
        myCompilingVisitor.setHandler(expr, handler);
        handler.setFilter(ConstantFilter.getInstance());
      }
      else {
        // just expression
        MatchingHandler handler;
        myCompilingVisitor.setHandler(expr, handler = new ExpressionHandler());

        handler.setFilter(ExpressionFilter.getInstance());
      }
    }
    else if (expr.getExpression() instanceof PsiReferenceExpression &&
             (myCompilingVisitor.getContext().getPattern().isRealTypedVar(expr.getExpression()))) {
      // search for statement
      final MatchingHandler exprHandler = myCompilingVisitor.getContext().getPattern().getHandler(expr);
      if (exprHandler instanceof SubstitutionHandler) {
        SubstitutionHandler handler = (SubstitutionHandler)exprHandler;
        handler.setFilter(new StatementFilter());
        handler.setMatchHandler(new StatementHandler());
      }
    }
  }

  @Override
  public void visitElement(PsiElement element) {
    myCompilingVisitor.handle(element);
    super.visitElement(element);
  }


  private void handleReference(PsiJavaCodeReferenceElement reference) {
    if (shouldOccur(reference)) {
      handleReferenceText(reference.getReferenceName(), myCompilingVisitor.getContext());
    }
  }

  private boolean shouldOccur(PsiJavaCodeReferenceElement reference) {
    final CompileContext compileContext = myCompilingVisitor.getContext();
    final PsiElement parent = reference.getParent();
    if (!(parent instanceof PsiReferenceList)) {
      return true;
    }
    final PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiMethod)) {
      return true;
    }
    final PsiMethod method = (PsiMethod)grandParent;
    if (method.getThrowsList() != parent) {
      return true;
    }
    final String name = method.getName();
    if (!compileContext.getPattern().isTypedVar(name)) {
      return true;
    }
    final SubstitutionHandler handler = (SubstitutionHandler)compileContext.getPattern().getHandler(name);
    return !(handler != null && handler.getMinOccurs() == 0);
  }

  private static void handleReferenceText(String refname, CompileContext compileContext) {
    if (refname == null) return;

    if (compileContext.getPattern().isTypedVar(refname)) {
      SubstitutionHandler handler = (SubstitutionHandler)compileContext.getPattern().getHandler(refname);
      RegExpPredicate predicate = MatchingHandler.getSimpleRegExpPredicate(handler);
      if (!GlobalCompilingVisitor.isSuitablePredicate(predicate, handler)) {
        return;
      }

      refname = predicate.getRegExp();

      if (handler.isStrictSubtype() || handler.isSubtype()) {
        final OptimizingSearchHelper searchHelper = compileContext.getSearchHelper();
        if (addDescendantsOf(refname, handler.isSubtype(), searchHelper, compileContext)) {
          searchHelper.endTransaction();
        }

        return;
      }
    }

    GlobalCompilingVisitor.addFilesToSearchForGivenWord(refname, true, GlobalCompilingVisitor.OccurenceKind.CODE, compileContext);
  }


  public static boolean addDescendantsOf(final String refname, final boolean subtype, OptimizingSearchHelper searchHelper, CompileContext context) {
    final List<PsiClass> classes = buildDescendants(refname, subtype, searchHelper, context);

    for (final PsiClass aClass : classes) {
      if (aClass instanceof PsiAnonymousClass) {
        searchHelper.addWordToSearchInCode(((PsiAnonymousClass)aClass).getBaseClassReference().getReferenceName());
      }
      else {
        searchHelper.addWordToSearchInCode(aClass.getName());
      }
    }

    return classes.size() > 0;
  }

  private static List<PsiClass> buildDescendants(String className,
                                                 boolean includeSelf,
                                                 OptimizingSearchHelper searchHelper,
                                                 CompileContext context) {
    if (!searchHelper.doOptimizing()) return Collections.emptyList();
    final SearchScope scope = context.getOptions().getScope();
    if (!(scope instanceof GlobalSearchScope)) return Collections.emptyList();

    final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(context.getProject());
    final PsiClass[] classes = cache.getClassesByName(className, (GlobalSearchScope)scope);
    final List<PsiClass> results = new ArrayList<>();

    final Processor<PsiClass> processor = aClass -> {
      results.add(aClass);
      return true;
    };

    for (PsiClass aClass : classes) {
      ClassInheritorsSearch.search(aClass, scope, true).forEach(processor);
    }

    if (includeSelf) {
      Collections.addAll(results, classes);
    }

    return results;
  }


  @Override
  public void visitCodeBlock(PsiCodeBlock block) {
    myCompilingVisitor.setCodeBlockLevel(myCompilingVisitor.getCodeBlockLevel() + 1);
    MatchingStrategy strategy = null;

    for (PsiElement el = block.getFirstChild(); el != null; el = el.getNextSibling()) {
      if (GlobalCompilingVisitor.getFilter().accepts(el)) {
        if (el instanceof PsiWhiteSpace) {
          myCompilingVisitor.addLexicalNode(el);
        }
      }
      else {
        el.accept(this);
        if (myCompilingVisitor.getCodeBlockLevel() == 1) {
          MatchingStrategy newstrategy = findStrategy(el);
          final MatchingHandler matchingHandler = myCompilingVisitor.getContext().getPattern().getHandler(el);
          myCompilingVisitor.getContext().getPattern().setHandler(el, new TopLevelMatchingHandler(matchingHandler));

          if (strategy == null || (strategy instanceof JavaDocMatchingStrategy)) {
            strategy = newstrategy;
          }
          else {
            if (strategy.getClass() != newstrategy.getClass()) {
              if (!(strategy instanceof CommentMatchingStrategy)) {
                throw new UnsupportedPatternException(SSRBundle.message("different.strategies.for.top.level.nodes.error.message"));
              }
              strategy = newstrategy;
            }
          }
        }
      }
    }

    if (myCompilingVisitor.getCodeBlockLevel() == 1) {
      if (strategy == null) {
        // this should happen only for error patterns
        strategy = ExprMatchingStrategy.getInstance();
      }
      myCompilingVisitor.getContext().getPattern().setStrategy(strategy);
    }
    myCompilingVisitor.setCodeBlockLevel(myCompilingVisitor.getCodeBlockLevel() - 1);
  }

  private MatchingStrategy findStrategy(PsiElement el) {
    if (el instanceof PsiDocComment) {
      return JavaDocMatchingStrategy.getInstance();
    }
    else if (el instanceof PsiComment) {
      return CommentMatchingStrategy.getInstance();
    }

    return ExprMatchingStrategy.getInstance();
  }

  private static boolean needsSupers(final PsiElement element, final MatchingHandler handler) {
    if (element.getParent() instanceof PsiClass &&
        handler instanceof SubstitutionHandler
      ) {
      final SubstitutionHandler handler2 = (SubstitutionHandler)handler;

      return (handler2.isStrictSubtype() || handler2.isSubtype());
    }
    return false;
  }
}
