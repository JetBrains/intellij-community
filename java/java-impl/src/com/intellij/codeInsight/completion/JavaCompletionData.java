/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.patterns.*;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.classes.EnumOrAnnotationTypeFilter;
import com.intellij.psi.filters.classes.InterfaceFilter;
import com.intellij.psi.filters.element.ReferenceOnFilter;
import com.intellij.psi.filters.getters.JavaMembersGetter;
import com.intellij.psi.filters.position.*;
import com.intellij.psi.filters.types.TypeCodeFragmentIsVoidEnabledFilter;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;

import static com.intellij.patterns.PsiJavaPatterns.*;
import static com.intellij.patterns.StandardPatterns.not;

public class JavaCompletionData extends JavaAwareCompletionData{

  private static final @NonNls String[] ourBlockFinalizers = {"{", "}", ";", ":", "else"};
  private static final PsiElementPattern<PsiElement,?> AFTER_DOT = psiElement().afterLeaf(".");
  public static final LeftNeighbour INSTANCEOF_PLACE = new LeftNeighbour(new OrFilter(
      new ReferenceOnFilter(new ClassFilter(PsiVariable.class)),
      new TextFilter(PsiKeyword.THIS),
      new AndFilter(new TextFilter(")"), new ParentElementFilter(new AndFilter(
        new ClassFilter(PsiTypeCastExpression.class, false),
        new OrFilter(
          new ParentElementFilter(new ClassFilter(PsiExpression.class)),
          new ClassFilter(PsiExpression.class))))),
      new AndFilter(new TextFilter("]"), new ParentElementFilter(new ClassFilter(PsiArrayAccessExpression.class)))));
  public static final PsiJavaElementPattern.Capture<PsiElement> VARIABLE_AFTER_FINAL =
    PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.FINAL).inside(PsiDeclarationStatement.class);
  public static final LeftNeighbour AFTER_TRY_BLOCK = new LeftNeighbour(new AndFilter(
    new TextFilter("}"),
    new ParentElementFilter(new AndFilter(
      new LeftNeighbour(new TextFilter(PsiKeyword.TRY)),
      new ParentElementFilter(new ClassFilter(PsiTryStatement.class)))
    )));
  public static final PsiJavaElementPattern.Capture<PsiElement> INSIDE_PARAMETER_LIST =
    PsiJavaPatterns.psiElement().withParent(
      psiElement(PsiJavaCodeReferenceElement.class).insideStarting(
        psiElement().withTreeParent(
          psiElement(PsiParameterList.class).andNot(psiElement(PsiAnnotationParameterList.class)))));

  private static final AndFilter START_OF_CODE_FRAGMENT = new AndFilter(
    new ScopeFilter(new AndFilter(
      new ClassFilter(JavaCodeFragment.class),
      new ClassFilter(PsiExpressionCodeFragment.class, false),
      new ClassFilter(PsiJavaCodeReferenceCodeFragment.class, false),
      new ClassFilter(PsiTypeCodeFragment.class, false)
    )),
    new StartElementFilter()
  );

  static final ElementFilter END_OF_BLOCK = new OrFilter(
    new AndFilter(
      new LeftNeighbour(
          new OrFilter(
              new AndFilter (
                  new TextFilter(ourBlockFinalizers),
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

  static final ElementPattern<PsiElement> START_SWITCH = psiElement().afterLeaf(psiElement().withText("{").withParents(PsiCodeBlock.class, PsiSwitchStatement.class));

  private static final ElementPattern<PsiElement> SUPER_OR_THIS_PATTERN =
    and(JavaSmartCompletionContributor.INSIDE_EXPRESSION,
        not(psiElement().afterLeaf(PsiKeyword.CASE)),
        not(psiElement().afterLeaf(psiElement().withText(".").afterLeaf(PsiKeyword.THIS, PsiKeyword.SUPER))),
        not(START_SWITCH));


  public static final AndFilter CLASS_START = new AndFilter(
    new OrFilter(
      END_OF_BLOCK,
      new PatternFilter(psiElement().afterLeaf(
        or(
          psiElement().withoutText(".").inside(psiElement(PsiModifierList.class)),
          psiElement().isNull())))
    ),
    new PatternFilter(not(psiElement().afterLeaf("@"))));

  private static final String[] PRIMITIVE_TYPES = new String[]{
    PsiKeyword.SHORT, PsiKeyword.BOOLEAN,
    PsiKeyword.DOUBLE, PsiKeyword.LONG,
    PsiKeyword.INT, PsiKeyword.FLOAT,
    PsiKeyword.CHAR, PsiKeyword.BYTE
  };

  final static ElementFilter CLASS_BODY = new OrFilter(
    new AfterElementFilter(new TextFilter("{")),
    new ScopeFilter(new ClassFilter(JspClassLevelDeclarationStatement.class)));
  public static final ElementPattern<PsiElement> START_FOR =
    psiElement().afterLeaf(psiElement().withText("(").afterLeaf("for")).withParents(PsiJavaCodeReferenceElement.class,
                                                                                    PsiExpressionStatement.class, PsiForStatement.class);
  private static final PsiJavaElementPattern.Capture<PsiElement> CLASS_REFERENCE =
    psiElement().withParent(psiReferenceExpression().referencing(psiClass()));
  public static final ElementPattern<PsiElement> EXPR_KEYWORDS = and(
    psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(
      not(
        or(psiElement(PsiTypeCastExpression.class),
           psiElement(PsiSwitchLabelStatement.class),
           psiElement(PsiExpressionStatement.class)
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

  public static final AndFilter DECLARATION_START = new AndFilter(
    CLASS_BODY,
    new OrFilter(
      END_OF_BLOCK,
      new LeftNeighbour(new OrFilter(
        new SuperParentFilter(new ClassFilter(PsiModifierList.class)),
        new AndFilter (new TokenTypeFilter(JavaTokenType.GT),
                       new SuperParentFilter(new ClassFilter(PsiTypeParameterList.class)))))
    ),
    new PatternFilter(not(psiElement().afterLeaf("@", "."))));

  private void declareCompletionSpaces() {
    declareFinalScope(PsiFile.class);

    {
      // Class body
      final CompletionVariant variant = new CompletionVariant(CLASS_BODY);
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
// package keyword completion
    {
      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, new StartElementFilter());
      variant.addCompletion(PsiKeyword.PACKAGE, TailType.INSERT_SPACE);
      registerVariant(variant);
    }

// import keyword completion
    {
      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, new OrFilter(
        new StartElementFilter(),
        END_OF_BLOCK
      ));
      variant.addCompletion(PsiKeyword.IMPORT);

      registerVariant(variant);
    }
// other in file scope
    {
      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, CLASS_START);
      variant.includeScopeClass(PsiClass.class);

      variant.addCompletion(PsiKeyword.CLASS);
      variant.addCompletion(PsiKeyword.INTERFACE);

      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(PsiTypeCodeFragment.class, new StartElementFilter());
      addPrimitiveTypes(variant, TailType.NONE);
      final CompletionVariant variant1 = new CompletionVariant(PsiTypeCodeFragment.class,
                                                               new AndFilter(
                                                                 new StartElementFilter(),
                                                                 new TypeCodeFragmentIsVoidEnabledFilter()
                                                               )
                                                               );
      variant1.addCompletion(PsiKeyword.VOID, TailType.NONE);
      registerVariant(variant);
      registerVariant(variant1);

    }

  }

  /**
   * aClass == null for JspDeclaration scope
   */
  protected void initVariantsInClassScope() {
// Completion for extends keyword
// position
    {
      final ElementFilter position = new AndFilter(
          new NotFilter(CLASS_BODY),
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
      variant.addCompletion(PsiKeyword.EXTENDS, TailType.HUMBLE_SPACE);
      variant.excludeScopeClass(PsiAnonymousClass.class);
      variant.excludeScopeClass(PsiTypeParameter.class);

      registerVariant(variant);
    }
// Completion for implements keyword
// position
    {
      final ElementFilter position = new AndFilter(
          new NotFilter(CLASS_BODY),
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
      variant.addCompletion(PsiKeyword.IMPLEMENTS, TailType.HUMBLE_SPACE);
      variant.excludeScopeClass(PsiAnonymousClass.class);

      registerVariant(variant);
    }


    {
      final CompletionVariant variant = new CompletionVariant(PsiElement.class, psiElement().afterLeaf(
          psiElement(PsiIdentifier.class).afterLeaf(
            psiElement().withText(string().oneOf(",", "<")).withParent(PsiTypeParameterList.class))));
      //variant.includeScopeClass(PsiClass.class, true);
      variant.addCompletion(PsiKeyword.EXTENDS, TailType.HUMBLE_SPACE);
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
// instanceof keyword
      final ElementFilter position = INSTANCEOF_PLACE;
      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiExpression.class, true);
      variant.includeScopeClass(PsiMethod.class);
      variant.addCompletion(PsiKeyword.INSTANCEOF);

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

// Catch/Finnaly completion
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

  private static void addPrimitiveTypes(CompletionVariant variant, TailType tailType){
    variant.addCompletion(PRIMITIVE_TYPES, tailType);
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

        return TailType.HUMBLE_SPACE;
      }
      scope = scope.getParent();
    }
  }

  private static void addStatementKeywords(CompletionResultSet variant, PsiElement position) {
    variant.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.SWITCH), TailTypes.SWITCH_LPARENTH));
    variant.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.WHILE), TailTypes.WHILE_LPARENTH));
    variant.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.DO), TailType.createSimpleTailType('{')));
    variant.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.FOR), TailTypes.FOR_LPARENTH));
    variant.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.IF), TailTypes.IF_LPARENTH));
    variant.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.TRY), TailType.createSimpleTailType('{')));
    variant.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.THROW), TailType.INSERT_SPACE));
    variant.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.NEW), TailType.INSERT_SPACE));
    variant.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.SYNCHRONIZED), TailTypes.SYNCHRONIZED_LPARENTH));

    if (PsiUtil.getLanguageLevel(position).isAtLeast(LanguageLevel.JDK_1_4)) {
      variant.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.ASSERT), TailType.INSERT_SPACE));
    }

    TailType returnTail = getReturnTail(position);
    LookupElement ret = createKeyword(position, PsiKeyword.RETURN);
    if (returnTail != TailType.NONE) {
      ret = new OverrideableSpace(ret, returnTail);
    }
    variant.addElement(ret);
  }

  @Override
  public void fillCompletions(CompletionParameters parameters, final CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    if (PsiTreeUtil.getParentOfType(position, PsiComment.class, false) != null) {
      return;
    }

    PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiExpressionStatement.class);
    if (statement == null) {
      statement = PsiTreeUtil.getParentOfType(position, PsiDeclarationStatement.class);
    }
    if (statement != null && statement.getTextRange().getStartOffset() == position.getTextRange().getStartOffset()) {
      if (!psiElement().withSuperParent(2, PsiSwitchStatement.class).accepts(statement)) {
        result.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.FINAL), TailType.HUMBLE_SPACE));
      }
    }

    if (isStatementPosition(position)) {
      if (PsiTreeUtil.getParentOfType(position, PsiSwitchStatement.class, false, PsiMember.class) != null) {
        result.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.CASE), TailType.INSERT_SPACE));
        result.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.DEFAULT), TailType.CASE_COLON));
        if (START_SWITCH.accepts(position)) {
          return;
        }
      }

      addBreakContinue(result, position);
      addStatementKeywords(result, position);
    }

    if (SUPER_OR_THIS_PATTERN.accepts(position)) {
      if (!AFTER_DOT.accepts(position) || isInsideQualifierClass(position)) {
        result.addElement(createKeyword(position, PsiKeyword.THIS));

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

        result.addElement(superItem);
      }
    }

    if (EXPR_KEYWORDS.accepts(position)) {
      result.addElement(TailTypeDecorator.withTail(createKeyword(position, PsiKeyword.NEW), TailType.INSERT_SPACE));
      result.addElement(createKeyword(position, PsiKeyword.NULL));
      result.addElement(createKeyword(position, PsiKeyword.TRUE));
      result.addElement(createKeyword(position, PsiKeyword.FALSE));
    }

    if (INSIDE_PARAMETER_LIST.accepts(position) && !psiElement().afterLeaf(PsiKeyword.FINAL).accepts(position) && !AFTER_DOT.accepts(position)) {
      result.addElement(TailTypeDecorator.withTail(createKeyword(position, PsiKeyword.FINAL), TailType.HUMBLE_SPACE));
    }

    if (CLASS_START.isAcceptable(position, position) &&
        PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class) == null) {
      for (String s : ModifierChooser.getKeywords(position)) {
        result.addElement(new OverrideableSpace(createKeyword(position, s), TailType.HUMBLE_SPACE));
      }
    }

    addPrimitiveTypes(result, position);

    if (isAfterTypeDot(position)) {
      result.addElement(createKeyword(position, PsiKeyword.CLASS));
    }

    addUnfinishedMethodTypeParameters(position, result);

    if (JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(position) &&
        !BasicExpressionCompletionContributor.AFTER_DOT.accepts(position) &&
        !(position.getParent() instanceof PsiLiteralExpression)) {
      for (final ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
        new JavaMembersGetter(info.getDefaultType(), position).addMembers(parameters, parameters.getInvocationCount() > 1, new Consumer<LookupElement>() {
          @Override
          public void consume(LookupElement element) {
            result.addElement(element);
          }
        });
      }
    }
  }

  private static void addUnfinishedMethodTypeParameters(PsiElement position, CompletionResultSet result) {
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
          result.addElement(new JavaPsiClassReferenceElement(typeParameter));
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
    if (INSIDE_PARAMETER_LIST.accepts(position) || position.getContainingFile() instanceof PsiJavaCodeReferenceCodeFragment) {
      return false;
    }

    return psiElement().afterLeaf(psiElement().withText(".").afterLeaf(CLASS_REFERENCE)).accepts(position) ||
           isAfterPrimitiveOrArrayType(position);
  }

  private static void addPrimitiveTypes(CompletionResultSet result, PsiElement position) {
    boolean inCast = psiElement()
      .afterLeaf(psiElement().withText("(").withParent(psiElement(PsiParenthesizedExpression.class, PsiTypeCastExpression.class)))
      .accepts(position);

    boolean declaration = DECLARATION_START.isAcceptable(position, position) ||
                          psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiMember.class).accepts(position) ||
                          psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiClassLevelDeclarationStatement.class).accepts(position);
    if (START_FOR.accepts(position) ||
        INSIDE_PARAMETER_LIST.accepts(position) && !AFTER_DOT.accepts(position) ||
        VARIABLE_AFTER_FINAL.accepts(position) ||
        inCast ||
        declaration ||
        isStatementPosition(position)) {
      for (String primitiveType : PRIMITIVE_TYPES) {
        LookupElement keyword = createKeyword(position, primitiveType);
        result.addElement(inCast ? keyword : new OverrideableSpace(keyword, TailType.HUMBLE_SPACE));
      }
    }
    if (declaration) {
      result.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.VOID), TailType.HUMBLE_SPACE));
    }
  }

  private static void addBreakContinue(CompletionResultSet result, PsiElement position) {
    PsiLoopStatement loop = PsiTreeUtil.getParentOfType(position, PsiLoopStatement.class);

    LookupElement br = createKeyword(position, PsiKeyword.BREAK);
    LookupElement cont = createKeyword(position, PsiKeyword.CONTINUE);
    if (!psiElement().insideSequence(true, psiElement(PsiLabeledStatement.class),
                                                  or(psiElement(PsiFile.class), psiElement(PsiMethod.class),
                                                     psiElement(PsiClassInitializer.class))).accepts(position)) {
      br = TailTypeDecorator.withTail(br, TailType.SEMICOLON);
      cont = TailTypeDecorator.withTail(cont, TailType.SEMICOLON);
    }

    if (loop != null && new InsideElementFilter(new ClassFilter(PsiStatement.class)).isAcceptable(position, loop)) {
      result.addElement(br);
      result.addElement(cont);
    }
    if (psiElement().inside(PsiSwitchStatement.class).accepts(position)) {
      result.addElement(br);
    }
  }

  private static boolean isStatementPosition(PsiElement position) {
    if (PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class) != null) {
      return false;
    }

    if (psiElement().withSuperParent(2, PsiConditionalExpression.class).accepts(position)) {
      return false;
    }

    if (END_OF_BLOCK.isAcceptable(position, position) &&
        PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, true, PsiMember.class) != null) {
      return true;
    }

    if (psiElement().withParents(PsiReferenceExpression.class, PsiExpressionStatement.class, PsiIfStatement.class).accepts(position)) {
      PsiElement stmt = position.getParent().getParent();
      PsiIfStatement ifStatement = (PsiIfStatement)stmt.getParent();
      if (ifStatement.getElseBranch() == stmt || ifStatement.getThenBranch() == stmt) {
        return true;
      }
    }

    return false;
  }

  private static LookupElement createKeyword(PsiElement position, String keyword) {
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

  private static class OverrideableSpace extends TailTypeDecorator<LookupElement> {
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
