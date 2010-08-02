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

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.patterns.*;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.classes.EnumOrAnnotationTypeFilter;
import com.intellij.psi.filters.classes.InterfaceFilter;
import com.intellij.psi.filters.element.ReferenceOnFilter;
import com.intellij.psi.filters.position.*;
import com.intellij.psi.filters.types.TypeCodeFragmentIsVoidEnabledFilter;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
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
  public static final PsiJavaElementPattern.Capture<PsiElement> AFTER_FINAL =
    PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.FINAL).inside(PsiDeclarationStatement.class);
  public static final LeftNeighbour AFTER_TRY_BLOCK = new LeftNeighbour(new AndFilter(
    new TextFilter("}"),
    new ParentElementFilter(new AndFilter(
      new LeftNeighbour(new TextFilter(PsiKeyword.TRY)),
      new ParentElementFilter(new ClassFilter(PsiTryStatement.class)))
)));
  public static final PsiJavaElementPattern.Capture<PsiElement> INSIDE_PARAMETER_LIST =
    PsiJavaPatterns.psiElement().withParent(
      psiElement(PsiJavaCodeReferenceElement.class).withParent(
        psiElement(PsiTypeElement.class).withParent(PsiParameterList.class)));

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

  static final AndFilter START_SWITCH = new AndFilter(END_OF_BLOCK, new LeftNeighbour(
        new AndFilter(new TextFilter("{"), new ParentElementFilter(new ClassFilter(PsiSwitchStatement.class), 2))));

  private static final ElementPattern<Object> SUPER_OR_THIS_PATTERN =
    and(JavaSmartCompletionContributor.INSIDE_EXPRESSION,
        not(psiElement().afterLeaf(PsiKeyword.CASE)),
        not(psiElement().afterLeaf(psiElement().withText(".").afterLeaf(PsiKeyword.THIS, PsiKeyword.SUPER))),
        not(new FilterPattern(START_SWITCH)));


  protected static final AndFilter CLASS_START = new AndFilter(
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
    PsiKeyword.VOID, PsiKeyword.CHAR, PsiKeyword.BYTE
  };

  final static ElementFilter CLASS_BODY = new OrFilter(
    new AfterElementFilter(new TextFilter("{")),
    new ScopeFilter(new ClassFilter(JspClassLevelDeclarationStatement.class)));

  public JavaCompletionData(){
    declareCompletionSpaces();

    final CompletionVariant variant = new CompletionVariant(PsiMethod.class,
                                                            new PatternFilter(not(psiElement().afterLeaf("@", "."))));
    variant.includeScopeClass(PsiVariable.class);
    variant.includeScopeClass(PsiClass.class);
    variant.includeScopeClass(PsiFile.class);
    variant.includeScopeClass(PsiParameterList.class);

    variant.addCompletion(new ModifierChooser());

    registerVariant(variant);

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
      variant.addCompletion(PsiKeyword.PACKAGE);
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
      variant.addCompletion(PsiKeyword.EXTENDS);
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
      variant.addCompletion(PsiKeyword.IMPLEMENTS);
      variant.excludeScopeClass(PsiAnonymousClass.class);

      registerVariant(variant);
    }


    {
// declaration start
// position
      final CompletionVariant variant = new CompletionVariant(PsiClass.class, DECLARATION_START);
      variant.includeScopeClass(JspClassLevelDeclarationStatement.class);

// completion
      addPrimitiveTypes(variant);
      variant.addCompletion(PsiKeyword.VOID);

      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(PsiElement.class, psiElement().afterLeaf(
          psiElement(PsiIdentifier.class).afterLeaf(
            psiElement().withText(string().oneOf(",", "<")).withParent(PsiTypeParameterList.class))));
      //variant.includeScopeClass(PsiClass.class, true);
      variant.addCompletion(PsiKeyword.EXTENDS, TailType.SPACE);
      registerVariant(variant);
    }
  }

  private void initVariantsInMethodScope() {
    {
// parameters list completion
      final CompletionVariant variant = new CompletionVariant(INSIDE_PARAMETER_LIST);
      variant.includeScopeClass(PsiParameterList.class, true);
      addPrimitiveTypes(variant);
      registerVariant(variant);
    }

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
// completion for declarations
      final CompletionVariant variant = new CompletionVariant(new OrFilter(END_OF_BLOCK, new LeftNeighbour(new TextFilter(PsiKeyword.FINAL))));
      variant.includeScopeClass(PsiCodeBlock.class, false);
      addPrimitiveTypes(variant);
      variant.addCompletion(PsiKeyword.CLASS);
      registerVariant(variant);
    }

// Completion in cast expressions
    {
      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, new LeftNeighbour(new AndFilter(
        new TextFilter("("),
        new ParentElementFilter(new OrFilter(
          new ClassFilter(PsiParenthesizedExpression.class),
          new ClassFilter(PsiTypeCastExpression.class))))));
      addPrimitiveTypes(variant);
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
// after final keyword
      final ElementFilter position = new PatternFilter(AFTER_FINAL);
      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiDeclarationStatement.class, true);
      addPrimitiveTypes(variant);

      registerVariant(variant);
    }


    {
// Keyword completion in start of declaration
      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, END_OF_BLOCK);
      addKeywords(variant);
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

    {
// Class field completion

      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, new LeftNeighbour(
        new AndFilter(
            new TextFilter("."),
            new LeftNeighbour(
                new OrFilter(
                    new ReferenceOnFilter(ElementClassFilter.CLASS),
                    new TextFilter(PRIMITIVE_TYPES),
                    new TextFilter("]"))))));
      variant.includeScopeClass(PsiAnnotationParameterList.class);
      variant.includeScopeClass(PsiVariable.class);
      variant.excludeScopeClass(PsiJavaCodeReferenceCodeFragment.class);
      variant.addCompletion(PsiKeyword.CLASS, TailType.NONE);
      registerVariant(variant);
    }

    {
// break completion
      final CompletionVariant variant = new CompletionVariant(new AndFilter(END_OF_BLOCK, new OrFilter(
        new ScopeFilter(new ClassFilter(PsiSwitchStatement.class)),
        new InsideElementFilter(new ClassFilter(PsiBlockStatement.class)))));

      variant.includeScopeClass(PsiForStatement.class, false);
      variant.includeScopeClass(PsiForeachStatement.class, false);
      variant.includeScopeClass(PsiWhileStatement.class, false);
      variant.includeScopeClass(PsiDoWhileStatement.class, false);
      variant.includeScopeClass(PsiSwitchStatement.class, false);
      variant.addCompletion(PsiKeyword.BREAK);
      registerVariant(variant);
    }
    {
// continue completion
      final CompletionVariant variant = new CompletionVariant(new AndFilter(END_OF_BLOCK, new InsideElementFilter(new ClassFilter(PsiBlockStatement.class))));
      variant.includeScopeClass(PsiForeachStatement.class, false);
      variant.includeScopeClass(PsiForStatement.class, false);
      variant.includeScopeClass(PsiWhileStatement.class, false);
      variant.includeScopeClass(PsiDoWhileStatement.class, false);

      variant.addCompletion(PsiKeyword.CONTINUE);
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(
        new AndFilter(
          END_OF_BLOCK,
          new NotFilter(START_SWITCH),
          new OrFilter(
            new ParentElementFilter(new ClassFilter(PsiSwitchLabelStatement.class)),
            new LeftNeighbour(new OrFilter(
              new ParentElementFilter(new ClassFilter(PsiSwitchStatement.class), 2),
              new AndFilter(new TextFilter(";", "}", ":"),new ParentElementFilter(new ClassFilter(PsiSwitchStatement.class), 3)
              ))))));
      variant.includeScopeClass(PsiElement.class, false);
      variant.addCompletion(PsiKeyword.CASE, TailType.SPACE);
      variant.addCompletion(PsiKeyword.DEFAULT, TailType.CASE_COLON);
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(START_SWITCH);
      variant.includeScopeClass(PsiElement.class, true);
      variant.addCompletion(PsiKeyword.CASE, TailType.SPACE);
      variant.addCompletion(PsiKeyword.DEFAULT, TailType.CASE_COLON);
      registerVariant(variant);
    }

    {
      // null completion !!!!!!
      final CompletionVariant variant = new CompletionVariant(and(
          psiElement().inside(or(
              psiElement(PsiExpressionList.class),
              psiElement(PsiExpression.class).withParent(or(psiElement(PsiIfStatement.class), psiElement(PsiLocalVariable.class))),
              psiElement(PsiAssignmentExpression.class))
          ),
          not(psiElement().afterLeaf(".", PsiKeyword.RETURN)),
          not(psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiTypeCastExpression.class)))
      ));
      variant.addCompletion(PsiKeyword.NULL, TailType.NONE);
      variant.addCompletion(PsiKeyword.TRUE, TailType.NONE);
      variant.addCompletion(PsiKeyword.FALSE, TailType.NONE);
      variant.includeScopeClass(PsiExpressionList.class);
      variant.includeScopeClass(PsiStatement.class);
      registerVariant(variant);
    }
  }

  private static void addPrimitiveTypes(CompletionVariant variant){
    addPrimitiveTypes(variant, CompletionVariant.DEFAULT_TAIL_TYPE);
  }

  private static void addPrimitiveTypes(CompletionVariant variant, TailType tailType){
    variant.addCompletion(new String[]{
      PsiKeyword.SHORT, PsiKeyword.BOOLEAN,
      PsiKeyword.DOUBLE, PsiKeyword.LONG,
      PsiKeyword.INT, PsiKeyword.FLOAT,
      PsiKeyword.CHAR, PsiKeyword.BYTE
    }, tailType);
  }

  private static void addKeywords(CompletionVariant variant){
    variant.addCompletion(PsiKeyword.SWITCH, TailTypes.SWITCH_LPARENTH);
    variant.addCompletion(PsiKeyword.WHILE, TailTypes.WHILE_LPARENTH);
    variant.addCompletion(PsiKeyword.FOR, TailTypes.FOR_LPARENTH);
    variant.addCompletion(PsiKeyword.TRY, TailType.createSimpleTailType('{'));
    variant.addCompletion(PsiKeyword.THROW, TailType.SPACE);
    variant.addCompletion(PsiKeyword.RETURN, TailType.SPACE);
    variant.addCompletion(PsiKeyword.NEW, TailType.SPACE);
    variant.addCompletion(PsiKeyword.ASSERT, TailType.SPACE);
  }

  @Override
  public void fillCompletions(CompletionParameters parameters, CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    if (SUPER_OR_THIS_PATTERN.accepts(position)) {
      if (AFTER_DOT.accepts(position) && !isInsideQualifierClass(position)) return;

      result.addElement(BasicExpressionCompletionContributor.createKeywordLookupItem(position, PsiKeyword.THIS));

      final LookupItem superItem = (LookupItem)BasicExpressionCompletionContributor.createKeywordLookupItem(position, PsiKeyword.SUPER);
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
}
