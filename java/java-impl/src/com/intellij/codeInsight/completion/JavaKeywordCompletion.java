/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
              new ElementFilter() {
                @Override
                public boolean isAcceptable(Object element, @Nullable PsiElement context) {
                  return ((PsiElement)element).getText().endsWith("*/");
                }

                @Override
                public boolean isClassAcceptable(Class hintClass) {
                  return true;
                }
              },
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

  static final Set<String> PRIMITIVE_TYPES = ContainerUtil.newLinkedHashSet(
    PsiKeyword.SHORT, PsiKeyword.BOOLEAN,
    PsiKeyword.DOUBLE, PsiKeyword.LONG,
    PsiKeyword.INT, PsiKeyword.FLOAT,
    PsiKeyword.CHAR, PsiKeyword.BYTE
  );

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
  private final CompletionParameters myParameters;
  private final JavaCompletionSession mySession;
  private final PsiElement myPosition;
  private final String myPrefix;
  private final List<LookupElement> myResults = new ArrayList<>();
  @Nullable private PsiElement myPrevLeaf;

  JavaKeywordCompletion(CompletionParameters parameters, JavaCompletionSession session) {
    myParameters = parameters;
    mySession = session;
    myPrefix = session.getMatcher().getPrefix();
    myPosition = parameters.getPosition();
    myPrevLeaf = PsiTreeUtil.prevVisibleLeaf(myPosition);

    addKeywords();
    addEnumCases();
  }

  private void addKeyword(LookupElement element) {
    if (element.getLookupString().startsWith(myPrefix)) {
      myResults.add(element);
    }
  }

  List<LookupElement> getResults() {
    return myResults;
  }

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

  private void addStatementKeywords() {
    addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.SWITCH), TailTypes.SWITCH_LPARENTH));
    addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.WHILE), TailTypes.WHILE_LPARENTH));
    addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.DO), TailTypes.DO_LBRACE));
    addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.FOR), TailTypes.FOR_LPARENTH));
    addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.IF), TailTypes.IF_LPARENTH));
    addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.TRY), TailTypes.TRY_LBRACE));
    addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.THROW), TailType.INSERT_SPACE));
    addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.NEW), TailType.INSERT_SPACE));
    addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.SYNCHRONIZED), TailTypes.SYNCHRONIZED_LPARENTH));

    if (PsiUtil.getLanguageLevel(myPosition).isAtLeast(LanguageLevel.JDK_1_4)) {
      addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.ASSERT), TailType.INSERT_SPACE));
    }

    TailType returnTail = getReturnTail(myPosition);
    LookupElement ret = createKeyword(PsiKeyword.RETURN);
    if (returnTail != TailType.NONE) {
      ret = new OverrideableSpace(ret, returnTail);
    }
    addKeyword(ret);

    if (psiElement().withText(";").withSuperParent(2, PsiIfStatement.class).accepts(myPrevLeaf) ||
        psiElement().withText("}").withSuperParent(3, PsiIfStatement.class).accepts(myPrevLeaf)) {
      addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.ELSE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }

    if (psiElement().withText("}").withParent(psiElement(PsiCodeBlock.class).withParent(or(psiElement(PsiTryStatement.class), psiElement(PsiCatchSection.class)))).accepts(myPrevLeaf)) {
      addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.CATCH), TailTypes.CATCH_LPARENTH));
      addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.FINALLY), TailTypes.FINALLY_LBRACE));
    }

  }

  void addKeywords() {
    if (PsiTreeUtil.getNonStrictParentOfType(myPosition, PsiLiteralExpression.class, PsiComment.class) != null) {
      return;
    }

    addFinal();

    boolean statementPosition = isStatementPosition(myPosition);
    if (statementPosition) {
      addCaseDefault();
      if (START_SWITCH.accepts(myPosition)) {
        return;
      }

      addBreakContinue();
      addStatementKeywords();
    }

    addThisSuper();

    addExpressionKeywords(statementPosition);

    addFileHeaderKeywords();

    addInstanceof();

    addClassKeywords();

    addMethodHeaderKeywords();

    addPrimitiveTypes(this::addKeyword, myPosition, mySession);

    addClassLiteral();

    addUnfinishedMethodTypeParameters();

    addExtendsImplements();
  }

  static boolean addWildcardExtendsSuper(CompletionResultSet result, PsiElement position) {
    if (JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) {
      for (String keyword : ContainerUtil.ar(PsiKeyword.EXTENDS, PsiKeyword.SUPER)) {
        if (keyword.startsWith(result.getPrefixMatcher().getPrefix())) {
          result.addElement(new OverrideableSpace(BasicExpressionCompletionContributor.createKeywordLookupItem(position, keyword), TailType.HUMBLE_SPACE_BEFORE_WORD));
        }
      }
      return true;
    }
    return false;
  }

  private void addMethodHeaderKeywords() {
    if (psiElement().withText(")").withParents(PsiParameterList.class, PsiMethod.class).accepts(myPrevLeaf)) {
      assert myPrevLeaf != null;
      if (myPrevLeaf.getParent().getParent() instanceof PsiAnnotationMethod) {
        addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.DEFAULT), TailType.HUMBLE_SPACE_BEFORE_WORD));
      } else {
        addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.THROWS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }
  }

  private void addCaseDefault() {
    if (getSwitchFromLabelPosition(myPosition) != null) {
      addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.CASE), TailType.INSERT_SPACE));
      addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.DEFAULT), TailType.CASE_COLON));
    }
  }

  private static PsiSwitchStatement getSwitchFromLabelPosition(PsiElement position) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiStatement.class, false, PsiMember.class);
    if (statement == null || statement.getTextRange().getStartOffset() != position.getTextRange().getStartOffset()) {
      return null;
    }

    if (!(statement instanceof PsiSwitchLabelStatement) && statement.getParent() instanceof PsiCodeBlock) {
      return ObjectUtils.tryCast(statement.getParent().getParent(), PsiSwitchStatement.class);
    }
    return null;
  }

  void addEnumCases() {
    PsiSwitchStatement switchStatement = getSwitchFromLabelPosition(myPosition);
    PsiExpression expression = switchStatement == null ? null : switchStatement.getExpression();
    PsiClass switchType = expression == null ? null : PsiUtil.resolveClassInClassTypeOnly(expression.getType());
    if (switchType == null || !switchType.isEnum()) return;

    Set<PsiField> used = ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch(switchStatement);
    for (PsiField field : switchType.getAllFields()) {
      String name = field.getName();
      if (!(field instanceof PsiEnumConstant) || used.contains(CompletionUtil.getOriginalOrSelf(field)) || name == null) {
        continue;
      }
      String prefix = "case ";
      String suffix = name + ":";
      LookupElementBuilder caseConst = LookupElementBuilder
        .create(field, prefix + suffix)
        .bold()
        .withPresentableText(prefix)
        .withTailText(suffix)
        .withLookupString(name);
      myResults.add(new JavaCompletionContributor.IndentingDecorator(caseConst));
    }
  }

  private void addFinal() {
    PsiStatement statement = PsiTreeUtil.getParentOfType(myPosition, PsiExpressionStatement.class);
    if (statement == null) {
      statement = PsiTreeUtil.getParentOfType(myPosition, PsiDeclarationStatement.class);
    }
    if (statement != null && statement.getTextRange().getStartOffset() == myPosition.getTextRange().getStartOffset()) {
      if (!psiElement().withSuperParent(2, PsiSwitchStatement.class).afterLeaf("{").accepts(statement)) {
        PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(myPrevLeaf, PsiTryStatement.class);
        if (tryStatement == null || tryStatement.getCatchSections().length > 0 || tryStatement.getFinallyBlock() != null || tryStatement.getResourceList() != null) {
          addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.FINAL), TailType.HUMBLE_SPACE_BEFORE_WORD));
          return;
        }
      }
    }

    if ((isInsideParameterList(myPosition) || isAtResourceVariableStart(myPosition) || isAtCatchVariableStart(myPosition)) &&
        !psiElement().afterLeaf(PsiKeyword.FINAL).accepts(myPosition) &&
        !AFTER_DOT.accepts(myPosition)) {
      addKeyword(TailTypeDecorator.withTail(createKeyword(PsiKeyword.FINAL), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }

  }

  private void addThisSuper() {
    if (SUPER_OR_THIS_PATTERN.accepts(myPosition)) {
      final boolean afterDot = AFTER_DOT.accepts(myPosition);
      final boolean insideQualifierClass = isInsideQualifierClass();
      final boolean insideInheritorClass = PsiUtil.isLanguageLevel8OrHigher(myPosition) && isInsideInheritorClass();
      if (!afterDot || insideQualifierClass || insideInheritorClass) {
        if (!afterDot || insideQualifierClass) {
          addKeyword(createKeyword(PsiKeyword.THIS));
        }

        final LookupElement superItem = createKeyword(PsiKeyword.SUPER);
        if (psiElement().afterLeaf(psiElement().withText("{").withSuperParent(2, psiMethod().constructor(true))).accepts(myPosition)) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(myPosition, PsiMethod.class, false, PsiClass.class);
          assert method != null;
          final boolean hasParams = superConstructorHasParameters(method);
          addKeyword(LookupElementDecorator.withInsertHandler(superItem, new ParenthesesInsertHandler<LookupElement>() {
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

        addKeyword(superItem);
      }
    }
  }

  private void addExpressionKeywords(boolean statementPosition) {
    if (psiElement(JavaTokenType.DOUBLE_COLON).accepts(myPrevLeaf)) {
      PsiMethodReferenceExpression parent = PsiTreeUtil.getParentOfType(myPosition, PsiMethodReferenceExpression.class);
      TailType tail = parent != null && !LambdaHighlightingUtil.insertSemicolon(parent.getParent()) ? TailType.SEMICOLON : TailType.NONE;
      addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.NEW), tail));
      return;
    }

    if (isExpressionPosition(myPosition)) {
      if (PsiTreeUtil.getParentOfType(myPosition, PsiAnnotation.class) == null) {
        if (!statementPosition) {
          addKeyword(TailTypeDecorator.withTail(createKeyword(PsiKeyword.NEW), TailType.INSERT_SPACE));
        }
        addKeyword(createKeyword(PsiKeyword.NULL));
      }
      if (mayExpectBoolean(myParameters)) {
        addKeyword(createKeyword(PsiKeyword.TRUE));
        addKeyword(createKeyword(PsiKeyword.FALSE));
      }
    }
  }

  private void addFileHeaderKeywords() {
    PsiFile file = myPosition.getContainingFile();
    if (!(file instanceof PsiExpressionCodeFragment) &&
        !(file instanceof PsiJavaCodeReferenceCodeFragment) &&
        !(file instanceof PsiTypeCodeFragment)) {
      if (myPrevLeaf == null) {
        addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.PACKAGE), TailType.HUMBLE_SPACE_BEFORE_WORD));
        addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.IMPORT), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      else if (END_OF_BLOCK.getValue().isAcceptable(myPosition, myPosition) && PsiTreeUtil.getParentOfType(myPosition, PsiMember.class) == null) {
        addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.IMPORT), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }

    if (PsiUtil.isLanguageLevel5OrHigher(myPosition) && myPrevLeaf != null && myPrevLeaf.textMatches(PsiKeyword.IMPORT)) {
      addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.STATIC), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private void addInstanceof() {
    if (isInstanceofPlace(myPosition)) {
      addKeyword(LookupElementDecorator.withInsertHandler(
        createKeyword(PsiKeyword.INSTANCEOF),
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

  private void addClassKeywords() {
    if (isSuitableForClass(myPosition)) {
      for (String s : ModifierChooser.getKeywords(myPosition)) {
        addKeyword(new OverrideableSpace(createKeyword(s), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }

      addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.CLASS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      if (PsiTreeUtil.getParentOfType(myPosition, PsiCodeBlock.class, true, PsiMember.class) == null) {
        addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.INTERFACE), TailType.HUMBLE_SPACE_BEFORE_WORD));
        if (PsiUtil.isLanguageLevel5OrHigher(myPosition)) {
          addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.ENUM), TailType.INSERT_SPACE));
        }
      }
    }

    if (psiElement().withText("@").andNot(psiElement().inside(PsiParameterList.class)).andNot(psiElement().inside(psiNameValuePair()))
      .accepts(myPrevLeaf)) {
      addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.INTERFACE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private void addClassLiteral() {
    if (isAfterTypeDot(myPosition)) {
      addKeyword(createKeyword(PsiKeyword.CLASS));
    }
  }

  private void addExtendsImplements() {
    if (myPrevLeaf == null || !(myPrevLeaf instanceof PsiIdentifier || myPrevLeaf.textMatches(">"))) return;

    PsiClass psiClass = null;
    PsiElement prevParent = myPrevLeaf.getParent();
    if (myPrevLeaf instanceof PsiIdentifier && prevParent instanceof PsiClass) {
      psiClass = (PsiClass)prevParent;
    } else {
      PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(myPrevLeaf, PsiReferenceList.class);
      if (referenceList != null && referenceList.getParent() instanceof PsiClass) {
        psiClass = (PsiClass)referenceList.getParent();
      }
      else if (prevParent instanceof PsiTypeParameterList && prevParent.getParent() instanceof PsiClass) {
        psiClass = (PsiClass)prevParent.getParent();
      }
    }

    if (psiClass != null) {
      addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.EXTENDS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      if (!psiClass.isInterface()) {
        addKeyword(new OverrideableSpace(createKeyword(PsiKeyword.IMPLEMENTS), TailType.HUMBLE_SPACE_BEFORE_WORD));
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

  private void addUnfinishedMethodTypeParameters() {
    final ProcessingContext context = new ProcessingContext();
    if (psiElement().inside(
      psiElement(PsiTypeElement.class).afterLeaf(
        psiElement().withText(">").withParent(
          psiElement(PsiTypeParameterList.class).withParent(PsiErrorElement.class).save("typeParameterList")))).accepts(myPosition, context)) {
      final PsiTypeParameterList list = (PsiTypeParameterList)context.get("typeParameterList");
      PsiElement current = list.getParent().getParent();
      if (current instanceof PsiField) {
        current = current.getParent();
      }
      if (current instanceof PsiClass) {
        for (PsiTypeParameter typeParameter : list.getTypeParameters()) {
          addKeyword(new JavaPsiClassReferenceElement(typeParameter));
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

  static void addPrimitiveTypes(Consumer<LookupElement> result, PsiElement position, JavaCompletionSession session) {
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
        if (!session.isKeywordAlreadyProcessed(primitiveType)) {
          result.consume(BasicExpressionCompletionContributor.createKeywordLookupItem(position, primitiveType));
        }
      }
    }
    if (declaration) {
      result.consume(new OverrideableSpace(BasicExpressionCompletionContributor.createKeywordLookupItem(position, PsiKeyword.VOID), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
    else if (typeFragment && ((PsiTypeCodeFragment)position.getContainingFile()).isVoidValid()) {
      result.consume(BasicExpressionCompletionContributor.createKeywordLookupItem(position, PsiKeyword.VOID));
    }
  }

  private static boolean expectsClassLiteral(PsiElement position) {
    return ContainerUtil.find(JavaSmartCompletionContributor.getExpectedTypes(position, false),
                              info -> InheritanceUtil.isInheritor(info.getType(), CommonClassNames.JAVA_LANG_CLASS)) != null;
  }

  private static boolean isAtResourceVariableStart(PsiElement position) {
    return psiElement().insideStarting(psiElement(PsiTypeElement.class).withParent(PsiResourceList.class)).accepts(position);
  }

  private static boolean isAtCatchVariableStart(PsiElement position) {
    return psiElement().insideStarting(psiElement(PsiTypeElement.class).withParent(PsiCatchSection.class)).accepts(position);
  }

  private void addBreakContinue() {
    PsiLoopStatement loop = PsiTreeUtil.getParentOfType(myPosition, PsiLoopStatement.class, true, PsiLambdaExpression.class, PsiMember.class);

    LookupElement br = createKeyword(PsiKeyword.BREAK);
    LookupElement cont = createKeyword(PsiKeyword.CONTINUE);
    TailType tailType;
    if (psiElement().insideSequence(true, psiElement(PsiLabeledStatement.class),
                                    or(psiElement(PsiFile.class), psiElement(PsiMethod.class),
                                       psiElement(PsiClassInitializer.class))).accepts(myPosition)) {
      tailType = TailType.HUMBLE_SPACE_BEFORE_WORD;
    }
    else {
      tailType = TailType.SEMICOLON;
    }
    br = TailTypeDecorator.withTail(br, tailType);
    cont = TailTypeDecorator.withTail(cont, tailType);

    if (loop != null && PsiTreeUtil.isAncestor(loop.getBody(), myPosition, false)) {
      addKeyword(br);
      addKeyword(cont);
    }
    if (psiElement().inside(PsiSwitchStatement.class).accepts(myPosition)) {
      addKeyword(br);
    }

    for (PsiLabeledStatement labeled : psiApi().parents(myPosition).takeWhile(notInstanceOf(PsiMember.class)).filter(PsiLabeledStatement.class)) {
      addKeyword(TailTypeDecorator.withTail(LookupElementBuilder.create("break " + labeled.getName()).bold(), TailType.SEMICOLON));
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

  private LookupElement createKeyword(String keyword) {
    return BasicExpressionCompletionContributor.createKeywordLookupItem(myPosition, keyword);
  }

  private boolean isInsideQualifierClass() {
    if (myPosition.getParent() instanceof PsiJavaCodeReferenceElement) {
      final PsiElement qualifier = ((PsiJavaCodeReferenceElement)myPosition.getParent()).getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiElement qualifierClass = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        if (qualifierClass instanceof PsiClass) {
          PsiElement parent = myPosition;
          final PsiManager psiManager = myPosition.getManager();
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

  private boolean isInsideInheritorClass() {
    if (myPosition.getParent() instanceof PsiJavaCodeReferenceElement) {
      final PsiElement qualifier = ((PsiJavaCodeReferenceElement)myPosition.getParent()).getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiElement qualifierClass = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        if (qualifierClass instanceof PsiClass && ((PsiClass)qualifierClass).isInterface()) {
          PsiElement parent = myPosition;
          while ((parent = PsiTreeUtil.getParentOfType(parent, PsiClass.class, true)) != null) {
            if (PsiUtil.getEnclosingStaticElement(myPosition, (PsiClass)parent) == null &&
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
