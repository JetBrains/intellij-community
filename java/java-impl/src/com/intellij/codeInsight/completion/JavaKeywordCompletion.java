/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.daemon.impl.analysis.LambdaHighlightingUtil;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.getters.JavaMembersGetter;
import com.intellij.psi.filters.position.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.Conditions.notInstanceOf;
import static com.intellij.patterns.PsiJavaPatterns.*;
import static com.intellij.patterns.StandardPatterns.not;
import static com.intellij.psi.SyntaxTraverser.psiApi;

public class JavaKeywordCompletion {
  public static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".");

  static final ElementPattern<PsiElement> VARIABLE_AFTER_FINAL = psiElement().afterLeaf(PsiKeyword.FINAL).inside(PsiDeclarationStatement.class);

  private static final ElementPattern<PsiElement> INSIDE_PARAMETER_LIST =
    psiElement().withParent(
      psiElement(PsiJavaCodeReferenceElement.class).insideStarting(
        psiElement().withTreeParent(
          psiElement(PsiParameterList.class).andNot(psiElement(PsiAnnotationParameterList.class)))));

  public static boolean isInsideParameterList(PsiElement position) {
    PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
    PsiModifierList modifierList = PsiTreeUtil.getParentOfType(prev, PsiModifierList.class);
    if (modifierList != null) {
      if (PsiTreeUtil.isAncestor(modifierList, position, false)) {
        return false;
      }
      PsiElement parent = modifierList.getParent();
      return parent instanceof PsiParameterList || parent instanceof PsiParameter && parent.getParent() instanceof PsiParameterList;
    }
    return INSIDE_PARAMETER_LIST.accepts(position);
  }


  private static final AndFilter START_OF_CODE_FRAGMENT = new AndFilter(
    new ScopeFilter(new AndFilter(
      new ClassFilter(JavaCodeFragment.class),
      new ClassFilter(PsiExpressionCodeFragment.class, false),
      new ClassFilter(PsiJavaCodeReferenceCodeFragment.class, false),
      new ClassFilter(PsiTypeCodeFragment.class, false)
    )),
    new StartElementFilter()
  );

  static final NotNullLazyValue<ElementFilter> END_OF_BLOCK = new AtomicNotNullLazyValue<ElementFilter>() {
    @NotNull
    @Override
    protected ElementFilter compute() {
      return new OrFilter(
        new AndFilter(
          new LeftNeighbour(
            new OrFilter(
              new AndFilter (
                new TextFilter("{", "}", ";", ":", "else"),
                new NotFilter (
                  new SuperParentFilter(new ClassFilter(PsiAnnotation.class))
                )
              ),
              new TextFilter("*/"),
              new TokenTypeFilter(JspElementType.HOLDER_TEMPLATE_DATA),
              new ClassFilter(OuterLanguageElement.class),
              new AndFilter(
                new TextFilter(")"),
                new NotFilter(
                  new OrFilter(
                    new ParentElementFilter(new ClassFilter(PsiExpressionList.class)),
                    new ParentElementFilter(new ClassFilter(PsiParameterList.class)),
                    new ParentElementFilter(new ClassFilter(PsiTypeCastExpression.class))
                  )
                )
              ))),
          new NotFilter(new TextFilter("."))
        ),
        START_OF_CODE_FRAGMENT
      );
    }
  };

  static final ElementPattern<PsiElement> START_SWITCH =
    psiElement().afterLeaf(psiElement().withText("{").withParents(PsiCodeBlock.class, PsiSwitchStatement.class));

  private static final ElementPattern<PsiElement> SUPER_OR_THIS_PATTERN =
    and(JavaSmartCompletionContributor.INSIDE_EXPRESSION,
        not(psiElement().afterLeaf(PsiKeyword.CASE)),
        not(psiElement().afterLeaf(psiElement().withText(".").afterLeaf(PsiKeyword.THIS, PsiKeyword.SUPER))),
        not(psiElement().inside(PsiAnnotation.class)),
        not(START_SWITCH),
        not(JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN));

  private static final String[] PRIMITIVE_TYPES = new String[]{
    PsiKeyword.SHORT, PsiKeyword.BOOLEAN,
    PsiKeyword.DOUBLE, PsiKeyword.LONG,
    PsiKeyword.INT, PsiKeyword.FLOAT,
    PsiKeyword.CHAR, PsiKeyword.BYTE
  };

  private static final NotNullLazyValue<ElementFilter> CLASS_BODY = new AtomicNotNullLazyValue<ElementFilter>() {
    @NotNull
    @Override
    protected ElementFilter compute() {
      return new OrFilter(
        new AfterElementFilter(new TextFilter("{")),
        new ScopeFilter(new ClassFilter(JspClassLevelDeclarationStatement.class)));
    }
  };

  static final ElementPattern<PsiElement> START_FOR =
    psiElement().afterLeaf(psiElement().withText("(").afterLeaf("for")).withParents(PsiJavaCodeReferenceElement.class,
                                                                                    PsiExpressionStatement.class, PsiForStatement.class);
  private static final ElementPattern<PsiElement> CLASS_REFERENCE =
    psiElement().withParent(psiReferenceExpression().referencing(psiClass().andNot(psiElement(PsiTypeParameter.class))));

  private static final ElementPattern<PsiElement> EXPR_KEYWORDS = and(
    psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(
      not(
        or(psiElement(PsiTypeCastExpression.class),
           psiElement(PsiSwitchLabelStatement.class),
           psiElement(PsiExpressionStatement.class),
           psiElement(PsiPrefixExpression.class)
        )
      )
    )),
    not(psiElement().afterLeaf("."))
  );

  static final NotNullLazyValue<ElementPattern<PsiElement>> DECLARATION_START = new NotNullLazyValue<ElementPattern<PsiElement>>() {
    @NotNull
    @Override
    protected ElementPattern<PsiElement> compute() {
      return psiElement().andNot(psiElement().afterLeaf("@", ".")).
        andOr(
          psiElement().and(new FilterPattern(CLASS_BODY.getValue())).
            andOr(
              new FilterPattern(END_OF_BLOCK.getValue()),
              psiElement().afterLeaf(or(
                psiElement().inside(PsiModifierList.class),
                psiElement().withElementType(JavaTokenType.GT).inside(PsiTypeParameterList.class)
              ))),
          psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiMember.class),
          psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiClassLevelDeclarationStatement.class)
        );
    }
  };

  private static TailType getReturnTail(PsiElement position) {
    PsiElement scope = position;
    while(true){
      if (scope instanceof PsiFile || scope instanceof PsiClassInitializer){
        return TailType.NONE;
      }

      if (scope instanceof PsiMethod){
        final PsiMethod method = (PsiMethod)scope;
        if(method.isConstructor() || PsiType.VOID.equals(method.getReturnType())) {
          return TailType.SEMICOLON;
        }

        return TailType.HUMBLE_SPACE_BEFORE_WORD;
      }
      if (scope instanceof PsiLambdaExpression) {
        final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(((PsiLambdaExpression)scope));
        if (PsiType.VOID.equals(returnType)) {
          return TailType.SEMICOLON;
        }
        return TailType.HUMBLE_SPACE_BEFORE_WORD;
      }
      scope = scope.getParent();
    }
  }

  private static void addStatementKeywords(Consumer<LookupElement> variant, PsiElement position, @Nullable PsiElement prevLeaf) {
    variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.SWITCH), TailTypes.SWITCH_LPARENTH));
    variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.WHILE), TailTypes.WHILE_LPARENTH));
    variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.DO), TailTypes.DO_LBRACE));
    variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.FOR), TailTypes.FOR_LPARENTH));
    variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.IF), TailTypes.IF_LPARENTH));
    variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.TRY), TailTypes.TRY_LBRACE));
    variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.THROW), TailType.INSERT_SPACE));
    variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.NEW), TailType.INSERT_SPACE));
    variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.SYNCHRONIZED), TailTypes.SYNCHRONIZED_LPARENTH));

    if (PsiUtil.getLanguageLevel(position).isAtLeast(LanguageLevel.JDK_1_4)) {
      variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.ASSERT), TailType.INSERT_SPACE));
    }

    TailType returnTail = getReturnTail(position);
    LookupElement ret = createKeyword(position, PsiKeyword.RETURN);
    if (returnTail != TailType.NONE) {
      ret = new OverrideableSpace(ret, returnTail);
    }
    variant.consume(ret);

    if (psiElement().withText(";").withSuperParent(2, PsiIfStatement.class).accepts(prevLeaf) ||
        psiElement().withText("}").withSuperParent(3, PsiIfStatement.class).accepts(prevLeaf)) {
      variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.ELSE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }

    if (psiElement().withText("}").withParent(psiElement(PsiCodeBlock.class).withParent(or(psiElement(PsiTryStatement.class), psiElement(PsiCatchSection.class)))).accepts(prevLeaf)) {
      variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.CATCH), TailTypes.CATCH_LPARENTH));
      variant.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.FINALLY), TailTypes.FINALLY_LBRACE));
    }

  }

  static void addKeywords(CompletionParameters parameters, final Consumer<LookupElement> result) {
    final PsiElement position = parameters.getPosition();
    if (PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class) != null) {
      return;
    }

    PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(position);
    addFinal(result, position, prevLeaf);

    if (isStatementPosition(position)) {
      addCaseDefault(result, position);
      if (START_SWITCH.accepts(position)) {
        return;
      }

      addBreakContinue(result, position);
      addStatementKeywords(result, position, prevLeaf);
    }

    addThisSuper(result, position);

    addExpressionKeywords(parameters, result, position, prevLeaf);

    addFileHeaderKeywords(result, position, prevLeaf);

    addInstanceof(result, position);

    addClassKeywords(result, position, prevLeaf);

    addMethodHeaderKeywords(result, position, prevLeaf);

    addPrimitiveTypes(result, position);

    addClassLiteral(result, position);

    addUnfinishedMethodTypeParameters(position, result);

    addExtendsSuperImplements(result, position, prevLeaf);
  }

  private static void addMethodHeaderKeywords(Consumer<LookupElement> result, PsiElement position, @Nullable PsiElement prevLeaf) {
    if (psiElement().withText(")").withParents(PsiParameterList.class, PsiMethod.class).accepts(prevLeaf)) {
      assert prevLeaf != null;
      if (prevLeaf.getParent().getParent() instanceof PsiAnnotationMethod) {
        result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.DEFAULT), TailType.HUMBLE_SPACE_BEFORE_WORD));
      } else {
        result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.THROWS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }
  }

  private static void addCaseDefault(Consumer<LookupElement> result, PsiElement position) {
    if (PsiTreeUtil.getParentOfType(position, PsiSwitchStatement.class, false, PsiMember.class) != null) {
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.CASE), TailType.INSERT_SPACE));
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.DEFAULT), TailType.CASE_COLON));
    }
  }

  private static void addFinal(Consumer<LookupElement> result, PsiElement position, @Nullable PsiElement prevLeaf) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiExpressionStatement.class);
    if (statement == null) {
      statement = PsiTreeUtil.getParentOfType(position, PsiDeclarationStatement.class);
    }
    if (statement != null && statement.getTextRange().getStartOffset() == position.getTextRange().getStartOffset()) {
      if (!psiElement().withSuperParent(2, PsiSwitchStatement.class).afterLeaf("{").accepts(statement)) {
        PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(prevLeaf, PsiTryStatement.class);
        if (tryStatement == null || tryStatement.getCatchSections().length > 0 || tryStatement.getFinallyBlock() != null || tryStatement.getResourceList() != null) {
          result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.FINAL), TailType.HUMBLE_SPACE_BEFORE_WORD));
          return;
        }
      }
    }

    if ((isInsideParameterList(position) || isAtResourceVariableStart(position) || isAtCatchVariableStart(position)) &&
        !psiElement().afterLeaf(PsiKeyword.FINAL).accepts(position) &&
        !AFTER_DOT.accepts(position)) {
      result.consume(TailTypeDecorator.withTail(createKeyword(position, PsiKeyword.FINAL), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }

  }

  private static void addThisSuper(Consumer<LookupElement> result, PsiElement position) {
    if (SUPER_OR_THIS_PATTERN.accepts(position)) {
      final boolean afterDot = AFTER_DOT.accepts(position);
      final boolean insideQualifierClass = isInsideQualifierClass(position);
      final boolean insideInheritorClass = PsiUtil.isLanguageLevel8OrHigher(position) && isInsideInheritorClass(position);
      if (!afterDot || insideQualifierClass || insideInheritorClass) {
        if (!afterDot || insideQualifierClass) {
          result.consume(createKeyword(position, PsiKeyword.THIS));
        }

        final LookupElement superItem = createKeyword(position, PsiKeyword.SUPER);
        if (psiElement().afterLeaf(psiElement().withText("{").withSuperParent(2, psiMethod().constructor(true))).accepts(position)) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(position, PsiMethod.class, false, PsiClass.class);
          assert method != null;
          final boolean hasParams = superConstructorHasParameters(method);
          result.consume(LookupElementDecorator.withInsertHandler(superItem, new ParenthesesInsertHandler<LookupElement>() {
            @Override
            protected boolean placeCaretInsideParentheses(InsertionContext context, LookupElement item) {
              return hasParams;
            }

            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
              super.handleInsert(context, item);
              TailType.insertChar(context.getEditor(), context.getTailOffset(), ';');
            }
          }));
          return;
        }

        result.consume(superItem);
      }
    }
  }

  private static void addExpressionKeywords(CompletionParameters parameters, Consumer<LookupElement> result, PsiElement position, @Nullable PsiElement prevLeaf) {
    if (psiElement(JavaTokenType.DOUBLE_COLON).accepts(prevLeaf)) {
      PsiMethodReferenceExpression parent = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiMethodReferenceExpression.class);
      TailType tail = parent != null && !LambdaHighlightingUtil.insertSemicolon(parent.getParent()) ? TailType.SEMICOLON : TailType.NONE;
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.NEW), tail));
      return;
    }

    if (isExpressionPosition(position)) {
      if (PsiTreeUtil.getParentOfType(position, PsiAnnotation.class) == null) {
        result.consume(TailTypeDecorator.withTail(createKeyword(position, PsiKeyword.NEW), TailType.INSERT_SPACE));
        result.consume(createKeyword(position, PsiKeyword.NULL));
      }
      if (mayExpectBoolean(parameters)) {
        result.consume(createKeyword(position, PsiKeyword.TRUE));
        result.consume(createKeyword(position, PsiKeyword.FALSE));
      }
    }
  }

  private static void addFileHeaderKeywords(Consumer<LookupElement> result, PsiElement position, @Nullable PsiElement prevLeaf) {
    PsiFile file = position.getContainingFile();
    if (!(file instanceof PsiExpressionCodeFragment) &&
        !(file instanceof PsiJavaCodeReferenceCodeFragment) &&
        !(file instanceof PsiTypeCodeFragment)) {
      if (prevLeaf == null) {
        result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.PACKAGE), TailType.HUMBLE_SPACE_BEFORE_WORD));
        result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.IMPORT), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      else if (END_OF_BLOCK.getValue().isAcceptable(position, position) && PsiTreeUtil.getParentOfType(position, PsiMember.class) == null) {
        result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.IMPORT), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }

    if (PsiUtil.isLanguageLevel5OrHigher(position) && prevLeaf != null && prevLeaf.textMatches(PsiKeyword.IMPORT)) {
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.STATIC), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private static void addInstanceof(Consumer<LookupElement> result, PsiElement position) {
    if (isInstanceofPlace(position)) {
      result.consume(LookupElementDecorator.withInsertHandler(
        createKeyword(position, PsiKeyword.INSTANCEOF),
        new InsertHandler<LookupElementDecorator<LookupElement>>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElementDecorator<LookupElement> item) {
            TailType tailType = TailType.HUMBLE_SPACE_BEFORE_WORD;
            if (tailType.isApplicable(context)) {
              tailType.processTail(context.getEditor(), context.getTailOffset());
            }

            if ('!' == context.getCompletionChar()) {
              context.setAddCompletionChar(false);
              context.commitDocument();
              PsiInstanceOfExpression expr =
                PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiInstanceOfExpression.class, false);
              if (expr != null) {
                String space = context.getCodeStyleSettings().SPACE_WITHIN_PARENTHESES ? " " : "";
                context.getDocument().insertString(expr.getTextRange().getStartOffset(), "!(" + space);
                context.getDocument().insertString(context.getTailOffset(), space + ")");
              }
            }
          }
        }));
    }
  }

  private static void addClassKeywords(Consumer<LookupElement> result, PsiElement position, @Nullable PsiElement prevLeaf) {
    if (isSuitableForClass(position)) {
      for (String s : ModifierChooser.getKeywords(position)) {
        result.consume(new OverrideableSpace(createKeyword(position, s), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      if (PsiUtil.isLanguageLevel8OrHigher(position)) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(position, PsiClass.class);
        if (containingClass != null && containingClass.isInterface()) {
          result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.DEFAULT), TailType.HUMBLE_SPACE_BEFORE_WORD));
        }
      }

      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.CLASS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      if (PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, true, PsiMember.class) == null) {
        result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.INTERFACE), TailType.HUMBLE_SPACE_BEFORE_WORD));
        if (PsiUtil.isLanguageLevel5OrHigher(position)) {
          result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.ENUM), TailType.INSERT_SPACE));
        }
      }
    }

    if (psiElement().withText("@").andNot(psiElement().inside(PsiParameterList.class)).andNot(psiElement().inside(psiNameValuePair()))
      .accepts(prevLeaf)) {
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.INTERFACE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private static void addClassLiteral(Consumer<LookupElement> result, PsiElement position) {
    if (isAfterTypeDot(position)) {
      result.consume(createKeyword(position, PsiKeyword.CLASS));
    }
  }

  private static void addExtendsSuperImplements(Consumer<LookupElement> result, PsiElement position, @Nullable PsiElement prevLeaf) {
    if (JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) {
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.EXTENDS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.SUPER), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }

    if (prevLeaf == null || !(prevLeaf instanceof PsiIdentifier || prevLeaf.textMatches(">"))) return;

    PsiClass psiClass = null;
    PsiElement prevParent = prevLeaf.getParent();
    if (prevLeaf instanceof PsiIdentifier && prevParent instanceof PsiClass) {
      psiClass = (PsiClass)prevParent;
    } else {
      PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(prevLeaf, PsiReferenceList.class);
      if (referenceList != null && referenceList.getParent() instanceof PsiClass) {
        psiClass = (PsiClass)referenceList.getParent();
      }
      else if (prevParent instanceof PsiTypeParameterList && prevParent.getParent() instanceof PsiClass) {
        psiClass = (PsiClass)prevParent.getParent();
      }
    }

    if (psiClass != null) {
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.EXTENDS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      if (!psiClass.isInterface()) {
        result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.IMPLEMENTS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }
  }

  private static boolean mayExpectBoolean(CompletionParameters parameters) {
    for (ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
      PsiType type = info.getType();
      if (type instanceof PsiClassType || PsiType.BOOLEAN.equals(type)) return true;
    }
    return false;
  }

  private static boolean isExpressionPosition(PsiElement position) {
    return EXPR_KEYWORDS.accepts(position) ||
           psiElement().insideStarting(psiElement(PsiClassObjectAccessExpression.class)).accepts(position);
  }

  public static boolean isInstanceofPlace(PsiElement position) {
    PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
    if (prev == null) return false;

    PsiElement expr = PsiTreeUtil.getParentOfType(prev, PsiExpression.class);
    if (expr != null && expr.getTextRange().getEndOffset() == prev.getTextRange().getEndOffset()) {
      return true;
    }

    if (position instanceof PsiIdentifier && position.getParent() instanceof PsiLocalVariable) {
      PsiType type = ((PsiLocalVariable)position.getParent()).getType();
      if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == null) {
        return true;
      }
    }
    
    return false;
  }

  public static boolean isSuitableForClass(PsiElement position) {
    if (psiElement().afterLeaf("@").accepts(position) ||
        PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class, PsiExpressionCodeFragment.class) != null) {
      return false;
    }

    PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
    if (prev == null) {
      return true;
    }
    if (psiElement().withoutText(".").inside(
      psiElement(PsiModifierList.class).withParent(
        not(psiElement(PsiParameter.class)).andNot(psiElement(PsiParameterList.class)))).accepts(prev) &&
        !psiElement().inside(PsiAnnotationParameterList.class).accepts(prev)) {
      return true;
    }

    return END_OF_BLOCK.getValue().isAcceptable(position, position);
  }

  static void addExpectedTypeMembers(CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getInvocationCount() <= 1) { // on second completion, StaticMemberProcessor will suggest those
      for (final ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
        new JavaMembersGetter(info.getDefaultType(), parameters).addMembers(false, result);
      }
    }
  }

  private static void addUnfinishedMethodTypeParameters(PsiElement position, final Consumer<LookupElement> result) {
    final ProcessingContext context = new ProcessingContext();
    if (psiElement().inside(
      psiElement(PsiTypeElement.class).afterLeaf(
        psiElement().withText(">").withParent(
          psiElement(PsiTypeParameterList.class).withParent(PsiErrorElement.class).save("typeParameterList")))).accepts(position, context)) {
      final PsiTypeParameterList list = (PsiTypeParameterList)context.get("typeParameterList");
      PsiElement current = list.getParent().getParent();
      if (current instanceof PsiField) {
        current = current.getParent();
      }
      if (current instanceof PsiClass) {
        for (PsiTypeParameter typeParameter : list.getTypeParameters()) {
          result.consume(new JavaPsiClassReferenceElement(typeParameter));
        }
      }
    }
  }

  static boolean isAfterPrimitiveOrArrayType(PsiElement element) {
    return psiElement().withParent(
      psiReferenceExpression().withFirstChild(
        psiElement(PsiClassObjectAccessExpression.class).withLastChild(
          not(psiElement().withText(PsiKeyword.CLASS))))).accepts(element);
  }

  static boolean isAfterTypeDot(PsiElement position) {
    if (isInsideParameterList(position) || position.getContainingFile() instanceof PsiJavaCodeReferenceCodeFragment) {
      return false;
    }

    return psiElement().afterLeaf(psiElement().withText(".").afterLeaf(CLASS_REFERENCE)).accepts(position) ||
           isAfterPrimitiveOrArrayType(position);
  }

  static void addPrimitiveTypes(final Consumer<LookupElement> result, PsiElement position) {
    if (AFTER_DOT.accepts(position) ||
        psiElement().inside(psiAnnotation()).accepts(position) && !expectsClassLiteral(position)) {
      return;
    }

    boolean afterNew = psiElement().afterLeaf(
      psiElement().withText(PsiKeyword.NEW).andNot(psiElement().afterLeaf(PsiKeyword.THROW, "."))).accepts(position);
    if (afterNew) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(position.getProject());
      for (String primitiveType : PRIMITIVE_TYPES) {
        result.consume(PsiTypeLookupItem.createLookupItem(factory.createTypeFromText(primitiveType + "[]", null), null));
      }
      result.consume(PsiTypeLookupItem.createLookupItem(factory.createTypeFromText("void[]", null), null));
      return;
    }

    boolean inCast = psiElement()
      .afterLeaf(psiElement().withText("(").withParent(psiElement(PsiParenthesizedExpression.class, PsiTypeCastExpression.class)))
      .accepts(position);

    boolean typeFragment = position.getContainingFile() instanceof PsiTypeCodeFragment && PsiTreeUtil.prevVisibleLeaf(position) == null;
    boolean declaration = DECLARATION_START.getValue().accepts(position);
    boolean expressionPosition = isExpressionPosition(position);
    boolean inGenerics = PsiTreeUtil.getParentOfType(position, PsiReferenceParameterList.class) != null;
    if (START_FOR.accepts(position) ||
        isInsideParameterList(position) ||
        inGenerics ||
        VARIABLE_AFTER_FINAL.accepts(position) ||
        inCast ||
        declaration ||
        typeFragment ||
        expressionPosition ||
        isStatementPosition(position)) {
      for (String primitiveType : PRIMITIVE_TYPES) {
        result.consume(createKeyword(position, primitiveType));
      }
    }
    if (declaration) {
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.VOID), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
    else if (typeFragment && ((PsiTypeCodeFragment)position.getContainingFile()).isVoidValid()) {
      result.consume(createKeyword(position, PsiKeyword.VOID));
    }
  }

  private static boolean expectsClassLiteral(PsiElement position) {
    return ContainerUtil.find(JavaSmartCompletionContributor.getExpectedTypes(position, false), new Condition<ExpectedTypeInfo>() {
      @Override
      public boolean value(ExpectedTypeInfo info) {
        return InheritanceUtil.isInheritor(info.getType(), CommonClassNames.JAVA_LANG_CLASS);
      }
    }) != null;
  }

  private static boolean isAtResourceVariableStart(PsiElement position) {
    return psiElement().insideStarting(psiElement(PsiTypeElement.class).withParent(PsiResourceList.class)).accepts(position);
  }

  private static boolean isAtCatchVariableStart(PsiElement position) {
    return psiElement().insideStarting(psiElement(PsiTypeElement.class).withParent(PsiCatchSection.class)).accepts(position);
  }

  private static void addBreakContinue(Consumer<LookupElement> result, PsiElement position) {
    PsiLoopStatement loop = PsiTreeUtil.getParentOfType(position, PsiLoopStatement.class);

    LookupElement br = createKeyword(position, PsiKeyword.BREAK);
    LookupElement cont = createKeyword(position, PsiKeyword.CONTINUE);
    TailType tailType;
    if (psiElement().insideSequence(true, psiElement(PsiLabeledStatement.class),
                                    or(psiElement(PsiFile.class), psiElement(PsiMethod.class),
                                       psiElement(PsiClassInitializer.class))).accepts(position)) {
      tailType = TailType.HUMBLE_SPACE_BEFORE_WORD;
    }
    else {
      tailType = TailType.SEMICOLON;
    }
    br = TailTypeDecorator.withTail(br, tailType);
    cont = TailTypeDecorator.withTail(cont, tailType);

    if (loop != null && PsiTreeUtil.isAncestor(loop.getBody(), position, false)) {
      result.consume(br);
      result.consume(cont);
    }
    if (psiElement().inside(PsiSwitchStatement.class).accepts(position)) {
      result.consume(br);
    }

    for (PsiLabeledStatement labeled : psiApi().parents(position).takeWhile(notInstanceOf(PsiMember.class)).filter(PsiLabeledStatement.class)) {
      result.consume(TailTypeDecorator.withTail(LookupElementBuilder.create("break " + labeled.getName()).bold(), TailType.SEMICOLON));
    }
  }

  private static boolean isStatementPosition(PsiElement position) {
    if (psiElement().withSuperParent(2, PsiConditionalExpression.class).andNot(psiElement().insideStarting(psiElement(PsiConditionalExpression.class))).accepts(position)) {
      return false;
    }

    if (END_OF_BLOCK.getValue().isAcceptable(position, position) &&
        PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, true, PsiMember.class) != null) {
      return true;
    }

    if (psiElement().withParents(PsiReferenceExpression.class, PsiExpressionStatement.class, PsiIfStatement.class).andNot(
      psiElement().afterLeaf(".")).accepts(position)) {
      PsiElement stmt = position.getParent().getParent();
      PsiIfStatement ifStatement = (PsiIfStatement)stmt.getParent();
      if (ifStatement.getElseBranch() == stmt || ifStatement.getThenBranch() == stmt) {
        return true;
      }
    }

    return false;
  }

  protected static LookupElement createKeyword(PsiElement position, String keyword) {
    return BasicExpressionCompletionContributor.createKeywordLookupItem(position, keyword);
  }

  private static boolean isInsideQualifierClass(PsiElement position) {
    if (position.getParent() instanceof PsiJavaCodeReferenceElement) {
      final PsiElement qualifier = ((PsiJavaCodeReferenceElement)position.getParent()).getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiElement qualifierClass = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        if (qualifierClass instanceof PsiClass) {
          PsiElement parent = position;
          final PsiManager psiManager = position.getManager();
          while ((parent = PsiTreeUtil.getParentOfType(parent, PsiClass.class, true)) != null) {
            if (psiManager.areElementsEquivalent(parent, qualifierClass)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean isInsideInheritorClass(PsiElement position) {
    if (position.getParent() instanceof PsiJavaCodeReferenceElement) {
      final PsiElement qualifier = ((PsiJavaCodeReferenceElement)position.getParent()).getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiElement qualifierClass = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        if (qualifierClass instanceof PsiClass && ((PsiClass)qualifierClass).isInterface()) {
          PsiElement parent = position;
          while ((parent = PsiTreeUtil.getParentOfType(parent, PsiClass.class, true)) != null) {
            if (PsiUtil.getEnclosingStaticElement(position, (PsiClass)parent) == null && 
                ((PsiClass)parent).isInheritor((PsiClass)qualifierClass, true)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean superConstructorHasParameters(PsiMethod method) {
    final PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) {
      return false;
    }

    final PsiClass superClass = psiClass.getSuperClass();
    if (superClass != null) {
      for (final PsiMethod psiMethod : superClass.getConstructors()) {
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
        if (resolveHelper.isAccessible(psiMethod, method, null) && psiMethod.getParameterList().getParameters().length > 0) {
          return true;
        }
      }
    }
    return false;
  }

  public static class OverrideableSpace extends TailTypeDecorator<LookupElement> {
    private final TailType myTail;

    public OverrideableSpace(LookupElement keyword, TailType tail) {
      super(keyword);
      myTail = tail;
    }

    @Override
    protected TailType computeTailType(InsertionContext context) {
      return context.shouldAddCompletionChar() ? TailType.NONE : myTail;
    }
  }
}
