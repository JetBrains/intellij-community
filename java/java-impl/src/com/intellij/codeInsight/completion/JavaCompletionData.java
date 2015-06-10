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
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.classes.EnumOrAnnotationTypeFilter;
import com.intellij.psi.filters.classes.InterfaceFilter;
import com.intellij.psi.filters.getters.JavaMembersGetter;
import com.intellij.psi.filters.position.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PsiJavaPatterns.*;
import static com.intellij.patterns.StandardPatterns.not;

public class JavaCompletionData extends JavaAwareCompletionData {
  private static final @NonNls String[] BLOCK_FINALIZERS = {"{", "}", ";", ":", "else"};

  public static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".");

  public static final PsiJavaElementPattern.Capture<PsiElement> VARIABLE_AFTER_FINAL =
    psiElement().afterLeaf(PsiKeyword.FINAL).inside(PsiDeclarationStatement.class);

  public static final LeftNeighbour AFTER_TRY_BLOCK = new LeftNeighbour(new AndFilter(
    new TextFilter("}"),
    new ParentElementFilter(new AndFilter(
      new LeftNeighbour(new TextFilter(PsiKeyword.TRY)),
      new ParentElementFilter(new ClassFilter(PsiTryStatement.class)))
    )));

  private static final PsiJavaElementPattern.Capture<PsiElement> INSIDE_PARAMETER_LIST =
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
                new TextFilter(BLOCK_FINALIZERS),
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

  public static final ElementPattern<PsiElement> START_FOR =
    psiElement().afterLeaf(psiElement().withText("(").afterLeaf("for")).withParents(PsiJavaCodeReferenceElement.class,
                                                                                    PsiExpressionStatement.class, PsiForStatement.class);
  private static final PsiJavaElementPattern.Capture<PsiElement> CLASS_REFERENCE =
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

  public JavaCompletionData(){
    declareCompletionSpaces();

    initVariantsInFileScope();
    initVariantsInClassScope();
    initVariantsInMethodScope();

    defineScopeEquivalence(PsiMethod.class, PsiClassInitializer.class);
    defineScopeEquivalence(PsiMethod.class, JavaCodeFragment.class);
  }

  public static final NotNullLazyValue<ElementPattern<PsiElement>> DECLARATION_START = new NotNullLazyValue<ElementPattern<PsiElement>>() {
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

  private void declareCompletionSpaces() {
    declareFinalScope(PsiFile.class);

    {
      // Class body
      final CompletionVariant variant = new CompletionVariant(CLASS_BODY.getValue());
      variant.includeScopeClass(PsiClass.class, true);
      registerVariant(variant);
    }
    {
      // Method body
      final CompletionVariant variant = new CompletionVariant(new AndFilter(new InsideElementFilter(new ClassFilter(PsiCodeBlock.class)),
                                                                            new NotFilter(new InsideElementFilter(new ClassFilter(JspClassLevelDeclarationStatement.class)))));
      variant.includeScopeClass(PsiMethod.class, true);
      variant.includeScopeClass(PsiClassInitializer.class, true);
      registerVariant(variant);
    }

    {
      // Field initializer
      final CompletionVariant variant = new CompletionVariant(new AfterElementFilter(new TextFilter("=")));
      variant.includeScopeClass(PsiField.class, true);
      registerVariant(variant);
    }

    declareFinalScope(PsiLiteralExpression.class);
    declareFinalScope(PsiComment.class);
  }

  protected void initVariantsInFileScope(){
  }

  /**
   * aClass == null for JspDeclaration scope
   */
  protected void initVariantsInClassScope() {
// Completion for extends keyword
// position
    {
      final ElementFilter position = new AndFilter(
          new NotFilter(CLASS_BODY.getValue()),
          new NotFilter(new AfterElementFilter(new ContentFilter(new TextFilter(PsiKeyword.EXTENDS)))),
          new NotFilter(new AfterElementFilter(new ContentFilter(new TextFilter(PsiKeyword.IMPLEMENTS)))),
          new NotFilter(new LeftNeighbour(new LeftNeighbour(new TextFilter("<", ",")))),
          new NotFilter(new ScopeFilter(new EnumOrAnnotationTypeFilter())),
          new LeftNeighbour(new OrFilter(
            new ClassFilter(PsiIdentifier.class),
            new TextFilter(">"))));
// completion
      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiClass.class, true);
      variant.addCompletion(PsiKeyword.EXTENDS, TailType.HUMBLE_SPACE_BEFORE_WORD);
      variant.excludeScopeClass(PsiAnonymousClass.class);
      variant.excludeScopeClass(PsiTypeParameter.class);

      registerVariant(variant);
    }
// Completion for implements keyword
// position
    {
      final ElementFilter position = new AndFilter(
          new NotFilter(CLASS_BODY.getValue()),
          new NotFilter(new BeforeElementFilter(new ContentFilter(new TextFilter(PsiKeyword.EXTENDS)))),
          new NotFilter(new AfterElementFilter(new ContentFilter(new TextFilter(PsiKeyword.IMPLEMENTS)))),
          new NotFilter(new LeftNeighbour(new LeftNeighbour(new TextFilter("<", ",")))),
          new LeftNeighbour(new OrFilter(
            new ClassFilter(PsiIdentifier.class),
            new TextFilter(">"))),
          new NotFilter(new ScopeFilter(new InterfaceFilter())));
// completion
      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiClass.class, true);
      variant.addCompletion(PsiKeyword.IMPLEMENTS, TailType.HUMBLE_SPACE_BEFORE_WORD);
      variant.excludeScopeClass(PsiAnonymousClass.class);

      registerVariant(variant);
    }


    {
      final CompletionVariant variant = new CompletionVariant(PsiElement.class, psiElement().afterLeaf(
          psiElement(PsiIdentifier.class).afterLeaf(
            psiElement().withText(string().oneOf(",", "<")).withParent(PsiTypeParameterList.class))));
      //variant.includeScopeClass(PsiClass.class, true);
      variant.addCompletion(PsiKeyword.EXTENDS, TailType.HUMBLE_SPACE_BEFORE_WORD);
      registerVariant(variant);
    }
  }

  private void initVariantsInMethodScope() {
// Completion for classes in method throws section
// position
    {
      final ElementFilter position = new LeftNeighbour(new AndFilter(
        new TextFilter(")"),
        new ParentElementFilter(new ClassFilter(PsiParameterList.class))));

// completion
      CompletionVariant variant = new CompletionVariant(PsiMethod.class, position);
      variant.includeScopeClass(PsiClass.class); // for throws on separate line
      variant.addCompletion(PsiKeyword.THROWS);

      registerVariant(variant);

//in annotation methods
      variant = new CompletionVariant(PsiAnnotationMethod.class, position);
      variant.addCompletion(PsiKeyword.DEFAULT);
      registerVariant(variant);
    }

    {
// Keyword completion in returns  !!!!
      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, new LeftNeighbour(new TextFilter(PsiKeyword.RETURN)));
      variant.addCompletion(PsiKeyword.TRUE, TailType.NONE);
      variant.addCompletion(PsiKeyword.FALSE, TailType.NONE);
      registerVariant(variant);
    }


// Catch/Finally completion
    {
      final ElementFilter position = AFTER_TRY_BLOCK;

      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiCodeBlock.class, true);
      variant.addCompletion(PsiKeyword.CATCH, TailTypes.CATCH_LPARENTH);
      variant.addCompletion(PsiKeyword.FINALLY, TailTypes.FINALLY_LBRACE);
      registerVariant(variant);
    }

// Catch/Finally completion
    {
      final ElementFilter position = new LeftNeighbour(new AndFilter(
        new TextFilter("}"),
        new ParentElementFilter(new AndFilter(
          new LeftNeighbour(new NotFilter(new TextFilter(PsiKeyword.TRY))),
          new OrFilter(
            new ParentElementFilter(new ClassFilter(PsiTryStatement.class)),
            new ParentElementFilter(new ClassFilter(PsiCatchSection.class)))
          ))));

      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiCodeBlock.class, false);
      variant.addCompletion(PsiKeyword.CATCH, TailTypes.CATCH_LPARENTH);
      variant.addCompletion(PsiKeyword.FINALLY, TailTypes.FINALLY_LBRACE);
      registerVariant(variant);
    }

// Completion for else expression
// completion
    {
      final ElementFilter position = new LeftNeighbour(
        new OrFilter(
          new AndFilter(new TextFilter("}"),new ParentElementFilter(new ClassFilter(PsiIfStatement.class), 3)),
          new AndFilter(new TextFilter(";"),new ParentElementFilter(new ClassFilter(PsiIfStatement.class), 2))
        ));
      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, position);
      variant.addCompletion(PsiKeyword.ELSE);

      registerVariant(variant);
    }

  }

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

  private static void addStatementKeywords(Consumer<LookupElement> variant, PsiElement position) {
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
  }

  public void fillCompletions(CompletionParameters parameters, final Consumer<LookupElement> result) {
    final PsiElement position = parameters.getPosition();
    if (PsiTreeUtil.getParentOfType(position, PsiComment.class, false) != null) {
      return;
    }

    PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiExpressionStatement.class);
    if (statement == null) {
      statement = PsiTreeUtil.getParentOfType(position, PsiDeclarationStatement.class);
    }
    PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(position);
    if (statement != null && statement.getTextRange().getStartOffset() == position.getTextRange().getStartOffset()) {
      if (!psiElement().withSuperParent(2, PsiSwitchStatement.class).afterLeaf("{").accepts(statement)) {
        PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(prevLeaf, PsiTryStatement.class);
        if (tryStatement == null || tryStatement.getCatchSections().length > 0 || tryStatement.getFinallyBlock() != null) {
          result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.FINAL), TailType.HUMBLE_SPACE_BEFORE_WORD));
        }
      }
    }

    if (isStatementPosition(position)) {
      if (PsiTreeUtil.getParentOfType(position, PsiSwitchStatement.class, false, PsiMember.class) != null) {
        result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.CASE), TailType.INSERT_SPACE));
        result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.DEFAULT), TailType.CASE_COLON));
        if (START_SWITCH.accepts(position)) {
          return;
        }
      }

      addBreakContinue(result, position);
      addStatementKeywords(result, position);
    }

    if (SUPER_OR_THIS_PATTERN.accepts(position)) {
      final boolean afterDot = AFTER_DOT.accepts(position);
      final boolean insideQualifierClass = isInsideQualifierClass(position);
      final boolean insideInheritorClass = PsiUtil.isLanguageLevel8OrHigher(position) && isInsideInheritorClass(position);
      if (!afterDot || insideQualifierClass || insideInheritorClass) {
        if (!afterDot || insideQualifierClass) {
          result.consume(createKeyword(position, PsiKeyword.THIS));
        }

        final LookupItem superItem = (LookupItem)createKeyword(position, PsiKeyword.SUPER);
        if (psiElement().afterLeaf(psiElement().withText("{").withSuperParent(2, psiMethod().constructor(true))).accepts(position)) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(position, PsiMethod.class, false, PsiClass.class);
          assert method != null;
          final boolean hasParams = superConstructorHasParameters(method);
          superItem.setInsertHandler(new ParenthesesInsertHandler<LookupElement>() {
            @Override
            protected boolean placeCaretInsideParentheses(InsertionContext context, LookupElement item) {
              return hasParams;
            }

            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
              super.handleInsert(context, item);
              TailType.insertChar(context.getEditor(), context.getTailOffset(), ';');
            }
          });
        }

        result.consume(superItem);
      }
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

    if ((isInsideParameterList(position) || isAtResourceVariableStart(position) || isAtCatchVariableStart(position)) &&
        !psiElement().afterLeaf(PsiKeyword.FINAL).accepts(position) &&
        !AFTER_DOT.accepts(position)) {
      result.consume(TailTypeDecorator.withTail(createKeyword(position, PsiKeyword.FINAL), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }

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

    if (isSuitableForClass(position)) {
      for (String s : ModifierChooser.getKeywords(position)) {
        result.consume(new OverrideableSpace(createKeyword(position, s), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.CLASS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      if (PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, true, PsiMember.class) == null) {
        result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.INTERFACE), TailType.HUMBLE_SPACE_BEFORE_WORD));
        if (PsiUtil.getLanguageLevel(position).isAtLeast(LanguageLevel.JDK_1_5)) {
          result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.ENUM), TailType.INSERT_SPACE));
        }
      }
    }

    addPrimitiveTypes(result, position);

    if (isAfterTypeDot(position)) {
      result.consume(createKeyword(position, PsiKeyword.CLASS));
    }

    addUnfinishedMethodTypeParameters(position, result);

    if (JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) {
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.EXTENDS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.SUPER), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private static boolean mayExpectBoolean(CompletionParameters parameters) {
    for (ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
      PsiType type = info.getType();
      if (type instanceof PsiClassType || type == PsiType.BOOLEAN) return true;
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
        PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class) != null) {
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

  private static void addPrimitiveTypes(final Consumer<LookupElement> result, PsiElement position) {
    if (AFTER_DOT.accepts(position)) {
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

    if (loop != null && new InsideElementFilter(new ClassFilter(PsiStatement.class)).isAcceptable(position, loop)) {
      result.consume(br);
      result.consume(cont);
    }
    if (psiElement().inside(PsiSwitchStatement.class).accepts(position)) {
      result.consume(br);
    }
  }

  private static boolean isStatementPosition(PsiElement position) {
    if (PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class) != null) {
      return false;
    }

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
