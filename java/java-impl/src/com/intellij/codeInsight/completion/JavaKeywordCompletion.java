// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.util.CompletionStyleUtil;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.LambdaHighlightingUtil;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.util.Conditions;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.Conditions.notInstanceOf;
import static com.intellij.patterns.PsiJavaPatterns.*;
import static com.intellij.psi.SyntaxTraverser.psiApi;

public class JavaKeywordCompletion {
  public static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".");
  private static final ElementPattern<PsiElement> AFTER_DOUBLE_COLON = psiElement().afterLeaf("::");

  static final ElementPattern<PsiElement> VARIABLE_AFTER_FINAL = psiElement().afterLeaf(PsiKeyword.FINAL).inside(PsiDeclarationStatement.class);

  private static final ElementPattern<PsiElement> INSIDE_PARAMETER_LIST =
    psiElement().withParent(
      psiElement(PsiJavaCodeReferenceElement.class).insideStarting(
        psiElement().withTreeParent(
          psiElement(PsiParameterList.class).andNot(psiElement(PsiAnnotationParameterList.class)))));
  private static final ElementPattern<PsiElement> INSIDE_RECORD_HEADER =
    psiElement().withParent(
      psiElement(PsiJavaCodeReferenceElement.class).insideStarting(
        or(
          psiElement().withTreeParent(
            psiElement(PsiRecordComponent.class)),
          psiElement().withTreeParent(
            psiElement(PsiRecordHeader.class)
          )
        )
      ));
  private static final InsertHandler<LookupElementDecorator<?>> ADJUST_LINE_OFFSET = (context, item) -> {
    item.getDelegate().handleInsert(context);
    context.commitDocument();
    CodeStyleManager.getInstance(context.getProject()).adjustLineIndent(context.getFile(), context.getStartOffset());
  };

  private static boolean isStatementCodeFragment(PsiFile file) {
    return file instanceof JavaCodeFragment &&
           !(file instanceof PsiExpressionCodeFragment ||
             file instanceof PsiJavaCodeReferenceCodeFragment ||
             file instanceof PsiTypeCodeFragment);
  }

  static boolean isEndOfBlock(@NotNull PsiElement element) {
    PsiElement prev = prevSignificantLeaf(element);
    if (prev == null) {
      PsiFile file = element.getContainingFile();
      return !(file instanceof PsiCodeFragment) || isStatementCodeFragment(file);
    }

    if (psiElement().inside(psiAnnotation()).accepts(prev)) return false;

    if (prev instanceof OuterLanguageElement) return true;
    if (psiElement().withText(string().oneOf("{", "}", ";", ":", "else")).accepts(prev)) return true;
    if (prev.textMatches(")")) {
      PsiElement parent = prev.getParent();
      if (parent instanceof PsiParameterList) {
        return PsiTreeUtil.getParentOfType(PsiTreeUtil.prevVisibleLeaf(element), PsiDocComment.class) != null;
      }

      return !(parent instanceof PsiExpressionList || parent instanceof PsiTypeCastExpression
               || parent instanceof PsiRecordHeader);
    }

    return false;
  }

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

  static final PsiElementPattern<PsiElement,?> START_FOR = psiElement().afterLeaf(psiElement().withText("(").afterLeaf("for"));
  private static final ElementPattern<PsiElement> CLASS_REFERENCE =
    psiElement().withParent(psiReferenceExpression().referencing(psiClass().andNot(psiElement(PsiTypeParameter.class))));

  private final CompletionParameters myParameters;
  private final JavaCompletionSession mySession;
  private final PsiElement myPosition;
  private final PrefixMatcher myKeywordMatcher;
  private final List<LookupElement> myResults = new ArrayList<>();
  private final PsiElement myPrevLeaf;

  JavaKeywordCompletion(CompletionParameters parameters, JavaCompletionSession session) {
    myParameters = parameters;
    mySession = session;
    myKeywordMatcher = new StartOnlyMatcher(session.getMatcher());
    myPosition = parameters.getPosition();
    myPrevLeaf = prevSignificantLeaf(myPosition);

    addKeywords();
    addEnumCases();
  }

  private static PsiElement prevSignificantLeaf(PsiElement position) {
    return FilterPositionUtil.searchNonSpaceNonCommentBack(position);
  }

  private void addKeyword(LookupElement element) {
    if (myKeywordMatcher.isStartMatch(element.getLookupString())) {
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
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.SWITCH), TailTypes.SWITCH_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.WHILE), TailTypes.WHILE_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.DO), TailTypes.DO_LBRACE));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.FOR), TailTypes.FOR_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.IF), TailTypes.IF_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.TRY), TailTypes.TRY_LBRACE));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.THROW), TailType.INSERT_SPACE));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.NEW), TailType.INSERT_SPACE));
    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.SYNCHRONIZED), TailTypes.SYNCHRONIZED_LPARENTH));

    if (PsiUtil.getLanguageLevel(myPosition).isAtLeast(LanguageLevel.JDK_1_4)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.ASSERT), TailType.INSERT_SPACE));
    }

    TailType returnTail = getReturnTail(myPosition);
    LookupElement ret = createKeyword(PsiKeyword.RETURN);
    if (returnTail != TailType.NONE) {
      ret = new OverridableSpace(ret, returnTail);
    }
    addKeyword(ret);

    if (psiElement().withText(";").withSuperParent(2, PsiIfStatement.class).accepts(myPrevLeaf) ||
        psiElement().withText("}").withSuperParent(3, PsiIfStatement.class).accepts(myPrevLeaf)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.ELSE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }

    if (psiElement()
        .withText("}")
        .withParent(psiElement(PsiCodeBlock.class).withParent(or(psiElement(PsiTryStatement.class), psiElement(PsiCatchSection.class))))
        .accepts(myPrevLeaf)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.CATCH), TailTypes.CATCH_LPARENTH));
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.FINALLY), TailTypes.FINALLY_LBRACE));
    }

  }

  void addKeywords() {
    if (PsiTreeUtil.getNonStrictParentOfType(myPosition, PsiLiteralExpression.class, PsiComment.class) != null) {
      return;
    }

    PsiFile file = myPosition.getContainingFile();
    if (PsiJavaModule.MODULE_INFO_FILE.equals(file.getName()) && PsiUtil.isLanguageLevel9OrHigher(file)) {
      addModuleKeywords();
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

    addVar();

    addClassLiteral();

    addExtendsImplements();
  }

  private void addVar() {
    if (isVarAllowed()) {
      addKeyword(createKeyword(PsiKeyword.VAR));
    }
  }

  private boolean isVarAllowed() {
    if (PsiUtil.isLanguageLevel11OrHigher(myPosition) && isLambdaParameterType()) {
      return true;
    }

    if (!PsiUtil.isLanguageLevel10OrHigher(myPosition)) return false;

    if (isAtCatchOrResourceVariableStart(myPosition) && PsiTreeUtil.getParentOfType(myPosition, PsiCatchSection.class) == null) {
      return true;
    }

    return isVariableTypePosition(myPosition) &&
           PsiTreeUtil.getParentOfType(myPosition, PsiCodeBlock.class, true, PsiMember.class, PsiLambdaExpression.class) != null;
  }

  private boolean isLambdaParameterType() {
    PsiElement position = myParameters.getOriginalPosition();
    PsiParameterList paramList = PsiTreeUtil.getParentOfType(position, PsiParameterList.class);
    if (paramList != null && paramList.getParent() instanceof PsiLambdaExpression) {
      PsiParameter param = PsiTreeUtil.getParentOfType(position, PsiParameter.class);
      PsiTypeElement type = param == null ? null :param.getTypeElement();
      return type == null || PsiTreeUtil.isAncestor(type, position, false);
    }
    return false;
  }

  boolean addWildcardExtendsSuper(CompletionResultSet result, PsiElement position) {
    if (JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) {
      for (String keyword : ContainerUtil.ar(PsiKeyword.EXTENDS, PsiKeyword.SUPER)) {
        if (myKeywordMatcher.isStartMatch(keyword)) {
          LookupElement item = BasicExpressionCompletionContributor.createKeywordLookupItem(position, keyword);
          result.addElement(new OverridableSpace(item, TailType.HUMBLE_SPACE_BEFORE_WORD));
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
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.DEFAULT), TailType.HUMBLE_SPACE_BEFORE_WORD));
      } else {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.THROWS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }
  }

  private void addCaseDefault() {
    PsiSwitchBlock switchBlock = getSwitchFromLabelPosition(myPosition);
    if (switchBlock != null) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.CASE), TailType.INSERT_SPACE));
      addKeyword(LookupElementDecorator.withInsertHandler(
        new OverridableSpace(createKeyword(PsiKeyword.DEFAULT), TailTypes.forSwitchLabel(switchBlock)),
        ADJUST_LINE_OFFSET));
    }
  }

  private static PsiSwitchBlock getSwitchFromLabelPosition(PsiElement position) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiStatement.class, false, PsiMember.class);
    if (statement == null || statement.getTextRange().getStartOffset() != position.getTextRange().getStartOffset()) {
      return null;
    }

    if (!(statement instanceof PsiSwitchLabelStatementBase) && statement.getParent() instanceof PsiCodeBlock) {
      return ObjectUtils.tryCast(statement.getParent().getParent(), PsiSwitchBlock.class);
    }
    return null;
  }

  void addEnumCases() {
    PsiSwitchBlock switchBlock = getSwitchFromLabelPosition(myPosition);
    PsiExpression expression = switchBlock == null ? null : switchBlock.getExpression();
    PsiClass switchType = expression == null ? null : PsiUtil.resolveClassInClassTypeOnly(expression.getType());
    if (switchType == null || !switchType.isEnum()) return;

    Set<PsiField> used = ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch(switchBlock);
    TailType tailType = TailTypes.forSwitchLabel(switchBlock);
    for (PsiField field : switchType.getAllFields()) {
      String name = field.getName();
      if (!(field instanceof PsiEnumConstant) || used.contains(CompletionUtil.getOriginalOrSelf(field))) {
        continue;
      }
      String prefix = "case ";
      LookupElementBuilder caseConst = LookupElementBuilder
        .create(field, prefix + name)
        .bold()
        .withPresentableText(prefix)
        .withTailText(name)
        .withLookupString(name);
      myResults.add(new JavaCompletionContributor.IndentingDecorator(TailTypeDecorator.withTail(caseConst, tailType)));
    }
  }

  private void addFinal() {
    PsiStatement statement = PsiTreeUtil.getParentOfType(myPosition, PsiExpressionStatement.class, PsiDeclarationStatement.class);
    if (statement != null && statement.getTextRange().getStartOffset() == myPosition.getTextRange().getStartOffset()) {
      if (!psiElement().withSuperParent(2, PsiSwitchStatement.class).afterLeaf("{").accepts(statement)) {
        PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(myPrevLeaf, PsiTryStatement.class);
        if (tryStatement == null ||
            tryStatement.getCatchSections().length > 0 ||
            tryStatement.getFinallyBlock() != null || tryStatement.getResourceList() != null) {
          addKeyword(new OverridableSpace(createKeyword(PsiKeyword.FINAL), TailType.HUMBLE_SPACE_BEFORE_WORD));
          return;
        }
      }
    }

    if ((isInsideParameterList(myPosition) || isAtCatchOrResourceVariableStart(myPosition)) &&
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
            public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
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
    if (AFTER_DOUBLE_COLON.accepts(myPosition)) {
      PsiMethodReferenceExpression parent = PsiTreeUtil.getParentOfType(myPosition, PsiMethodReferenceExpression.class);
      if (parent != null && canUseConstructorReference(parent)) {
        TailType tail = !LambdaHighlightingUtil.insertSemicolon(parent.getParent()) ? TailType.SEMICOLON : TailType.NONE;
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.NEW), tail));
      }
      return;
    }

    if (isExpressionPosition(myPosition)) {
      PsiElement parent = myPosition.getParent();
      PsiElement grandParent = parent == null ? null : parent.getParent();
      boolean allowExprKeywords = !(grandParent instanceof PsiExpressionStatement) && !(grandParent instanceof PsiUnaryExpression);
      if (PsiTreeUtil.getParentOfType(myPosition, PsiAnnotation.class) == null) {
        if (!statementPosition) {
          addKeyword(TailTypeDecorator.withTail(createKeyword(PsiKeyword.NEW), TailType.INSERT_SPACE));
          if (HighlightingFeature.ENHANCED_SWITCH.isAvailable(myPosition)) {
            addKeyword(new OverridableSpace(createKeyword(PsiKeyword.SWITCH), TailTypes.SWITCH_LPARENTH));
          }
        }
        if (allowExprKeywords) {
          addKeyword(createKeyword(PsiKeyword.NULL));
        }
      }
      if (allowExprKeywords && mayExpectBoolean(myParameters)) {
        addKeyword(createKeyword(PsiKeyword.TRUE));
        addKeyword(createKeyword(PsiKeyword.FALSE));
      }
    }

    if (isQualifiedNewContext()) {
      addKeyword(createKeyword(PsiKeyword.NEW));
    }
  }

  private static boolean canUseConstructorReference(PsiMethodReferenceExpression ref) {
    PsiTypeElement qualifierType = ref.getQualifierType();
    if (qualifierType == null) {
      PsiElement qualifier = ref.getQualifier();
      return qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifier).resolve() != null;
    }

    if (qualifierType instanceof PsiClassType) return ((PsiClassType)qualifierType).resolve() != null;
    return qualifierType instanceof PsiArrayType;
  }

  private boolean isQualifiedNewContext() {
    if (myPosition.getParent() instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)myPosition.getParent()).getQualifierExpression();
      PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifier == null ? null : qualifier.getType());
      if (qualifierClass != null &&
          ContainerUtil.exists(qualifierClass.getAllInnerClasses(), inner -> canBeCreatedInQualifiedNew(qualifierClass, inner))) {
        return true;
      }
    }
    return false;
  }

  private boolean canBeCreatedInQualifiedNew(PsiClass outer, PsiClass inner) {
    PsiMethod[] constructors = inner.getConstructors();
    return !inner.hasModifierProperty(PsiModifier.STATIC) &&
           PsiUtil.isAccessible(inner, myPosition, outer) &&
           (constructors.length == 0 || ContainerUtil.exists(constructors, c -> PsiUtil.isAccessible(c, myPosition, outer)));
  }

  private void addFileHeaderKeywords() {
    PsiFile file = myPosition.getContainingFile();
    assert file != null;

    if (!(file instanceof PsiExpressionCodeFragment) &&
        !(file instanceof PsiJavaCodeReferenceCodeFragment) &&
        !(file instanceof PsiTypeCodeFragment)) {
      if (myPrevLeaf == null) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.PACKAGE), TailType.HUMBLE_SPACE_BEFORE_WORD));
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.IMPORT), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      else if (psiElement().inside(psiAnnotation().withParents(PsiModifierList.class, PsiFile.class)).accepts(myPrevLeaf)
               && PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.PACKAGE), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      else if (isEndOfBlock(myPosition) && PsiTreeUtil.getParentOfType(myPosition, PsiMember.class) == null) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.IMPORT), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }

    if (PsiUtil.isLanguageLevel5OrHigher(file) && myPrevLeaf != null && myPrevLeaf.textMatches(PsiKeyword.IMPORT)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.STATIC), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private void addInstanceof() {
    if (isInstanceofPlace(myPosition)) {
      addKeyword(LookupElementDecorator.withInsertHandler(
        createKeyword(PsiKeyword.INSTANCEOF),
        (context, item) -> {
          TailType tailType = TailType.HUMBLE_SPACE_BEFORE_WORD;
          if (tailType.isApplicable(context)) {
            tailType.processTail(context.getEditor(), context.getTailOffset());
          }

          if ('!' == context.getCompletionChar()) {
            context.setAddCompletionChar(false);
          }
          context.commitDocument();
          PsiInstanceOfExpression expr =
            PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiInstanceOfExpression.class, false);
          if (expr != null) {
            PsiExpression operand = expr.getOperand();
            if (operand instanceof PsiPrefixExpression && 
                ((PsiPrefixExpression)operand).getOperationTokenType().equals(JavaTokenType.EXCL)) {
              PsiExpression negated = ((PsiPrefixExpression)operand).getOperand();
              if (negated != null) {
                String space = CompletionStyleUtil.getCodeStyleSettings(context).SPACE_WITHIN_PARENTHESES ? " " : "";
                context.getDocument().insertString(negated.getTextRange().getStartOffset(), "(" + space);
                context.getDocument().insertString(context.getTailOffset(), space + ")");
              }
            }
            else if ('!' == context.getCompletionChar()) {
              String space = CompletionStyleUtil.getCodeStyleSettings(context).SPACE_WITHIN_PARENTHESES ? " " : "";
              context.getDocument().insertString(expr.getTextRange().getStartOffset(), "!(" + space);
              context.getDocument().insertString(context.getTailOffset(), space + ")");
            }
          }
        }));
    }
  }

  private void addClassKeywords() {
    if (isSuitableForClass(myPosition)) {
      for (String s : ModifierChooser.getKeywords(myPosition)) {
        addKeyword(new OverridableSpace(createKeyword(s), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }

      if (psiElement().insideStarting(psiElement(PsiLocalVariable.class, PsiExpressionStatement.class)).accepts(myPosition)) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.CLASS), TailType.HUMBLE_SPACE_BEFORE_WORD));
        if (HighlightingFeature.RECORDS.isAvailable(myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(PsiKeyword.RECORD), TailType.HUMBLE_SPACE_BEFORE_WORD));
        }
      }
      if (PsiTreeUtil.getParentOfType(myPosition, PsiExpression.class, true, PsiMember.class) == null &&
          PsiTreeUtil.getParentOfType(myPosition, PsiCodeBlock.class, true, PsiMember.class) == null) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.CLASS), TailType.HUMBLE_SPACE_BEFORE_WORD));
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.INTERFACE), TailType.HUMBLE_SPACE_BEFORE_WORD));
        if (HighlightingFeature.RECORDS.isAvailable(myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(PsiKeyword.RECORD), TailType.HUMBLE_SPACE_BEFORE_WORD));
        }
        if (PsiUtil.isLanguageLevel5OrHigher(myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(PsiKeyword.ENUM), TailType.INSERT_SPACE));
        }
      }
    }

    if (psiElement().withText("@").andNot(psiElement().inside(PsiParameterList.class)).andNot(psiElement().inside(psiNameValuePair()))
      .accepts(myPrevLeaf)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.INTERFACE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private void addClassLiteral() {
    if (isAfterTypeDot(myPosition)) {
      addKeyword(createKeyword(PsiKeyword.CLASS));
    }
  }

  private void addExtendsImplements() {
    if (myPrevLeaf == null ||
        !(myPrevLeaf instanceof PsiIdentifier || myPrevLeaf.textMatches(">") || myPrevLeaf.textMatches(")"))) {
      return;
    }

    PsiClass psiClass = null;
    PsiElement prevParent = myPrevLeaf.getParent();
    if (myPrevLeaf instanceof PsiIdentifier && prevParent instanceof PsiClass) {
      psiClass = (PsiClass)prevParent;
    }
    else {
      PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(myPrevLeaf, PsiReferenceList.class);
      if (referenceList != null && referenceList.getParent() instanceof PsiClass) {
        psiClass = (PsiClass)referenceList.getParent();
      }
      else if ((prevParent instanceof PsiTypeParameterList || prevParent instanceof PsiRecordHeader)
               && prevParent.getParent() instanceof PsiClass) {
        psiClass = (PsiClass)prevParent.getParent();
      }
    }

    if (psiClass != null) {
      if (!psiClass.isEnum() && !psiClass.isRecord()) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.EXTENDS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      if (!psiClass.isInterface()) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.IMPLEMENTS), TailType.HUMBLE_SPACE_BEFORE_WORD));
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
    if (psiElement().insideStarting(psiElement(PsiClassObjectAccessExpression.class)).accepts(position)) return true;

    PsiElement parent = position.getParent();
    if (!(parent instanceof PsiReferenceExpression) ||
        ((PsiReferenceExpression)parent).isQualified() ||
        JavaCompletionContributor.IN_SWITCH_LABEL.accepts(position)) {
      return false;
    }
    if (parent.getParent() instanceof PsiExpressionStatement) {
      PsiElement previous = PsiTreeUtil.skipWhitespacesBackward(parent.getParent());
      return previous == null || !(previous.getLastChild() instanceof PsiErrorElement);
    }
    return true;
  }

  public static boolean isInstanceofPlace(PsiElement position) {
    PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
    if (prev == null) return false;

    PsiElement expr = PsiTreeUtil.getParentOfType(prev, PsiExpression.class);
    if (expr != null && expr.getTextRange().getEndOffset() == prev.getTextRange().getEndOffset() &&
        PsiTreeUtil.getParentOfType(expr, PsiAnnotation.class) == null) {
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

    PsiElement prev = prevSignificantLeaf(position);
    if (prev == null) {
      return true;
    }
    if (psiElement().withoutText(".").inside(
      psiElement(PsiModifierList.class).withParent(
        not(psiElement(PsiParameter.class)).andNot(psiElement(PsiParameterList.class)))).accepts(prev) &&
        (!psiElement().inside(PsiAnnotationParameterList.class).accepts(prev) || prev.textMatches(")"))) {
      return true;
    }

    if (psiElement().withParents(PsiErrorElement.class, PsiFile.class).accepts(position)) {
      return true;
    }

    return isEndOfBlock(position);
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

  static void addPrimitiveTypes(Consumer<? super LookupElement> result, PsiElement position, JavaCompletionSession session) {
    if (AFTER_DOT.accepts(position) ||
        AFTER_DOUBLE_COLON.accepts(position) ||
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
      return;
    }

    boolean inCast = psiElement()
      .afterLeaf(psiElement().withText("(").withParent(psiElement(PsiParenthesizedExpression.class, PsiTypeCastExpression.class)))
      .accepts(position);

    boolean typeFragment = position.getContainingFile() instanceof PsiTypeCodeFragment && PsiTreeUtil.prevVisibleLeaf(position) == null;
    boolean declaration = isDeclarationStart(position);
    boolean expressionPosition = isExpressionPosition(position);
    boolean inGenerics = PsiTreeUtil.getParentOfType(position, PsiReferenceParameterList.class) != null;
    if (isVariableTypePosition(position) ||
        inGenerics ||
        inCast ||
        declaration ||
        typeFragment ||
        expressionPosition) {
      for (String primitiveType : PRIMITIVE_TYPES) {
        if (!session.isKeywordAlreadyProcessed(primitiveType)) {
          result.consume(BasicExpressionCompletionContributor.createKeywordLookupItem(position, primitiveType));
        }
      }
      if (expressionPosition && !session.isKeywordAlreadyProcessed(PsiKeyword.VOID)) {
        result.consume(BasicExpressionCompletionContributor.createKeywordLookupItem(position, PsiKeyword.VOID));
      }
    }
    if (declaration) {
      LookupElement item = BasicExpressionCompletionContributor.createKeywordLookupItem(position, PsiKeyword.VOID);
      result.consume(new OverridableSpace(item, TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
    else if (typeFragment && ((PsiTypeCodeFragment)position.getContainingFile()).isVoidValid()) {
      result.consume(BasicExpressionCompletionContributor.createKeywordLookupItem(position, PsiKeyword.VOID));
    }
  }

  private static boolean isVariableTypePosition(PsiElement position) {
    return START_FOR.accepts(position) ||
           isInsideParameterList(position) ||
           INSIDE_RECORD_HEADER.accepts(position) ||
           VARIABLE_AFTER_FINAL.accepts(position) ||
           isStatementPosition(position);
  }

  static boolean isDeclarationStart(@NotNull PsiElement position) {
    if (psiElement().afterLeaf("@", ".").accepts(position)) return false;

    PsiElement parent = position.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement && parent.getParent() instanceof PsiTypeElement) {
      PsiElement typeHolder = psiApi().parents(parent.getParent()).skipWhile(Conditions.instanceOf(PsiTypeElement.class)).first();
      return typeHolder instanceof PsiMember || typeHolder instanceof PsiClassLevelDeclarationStatement;
    }

    return false;
  }

  private static boolean expectsClassLiteral(PsiElement position) {
    return ContainerUtil.find(JavaSmartCompletionContributor.getExpectedTypes(position, false),
                              info -> InheritanceUtil.isInheritor(info.getType(), CommonClassNames.JAVA_LANG_CLASS)) != null;
  }

  private static boolean isAtCatchOrResourceVariableStart(PsiElement position) {
    PsiElement type = PsiTreeUtil.getParentOfType(position, PsiTypeElement.class);
    if (type != null && type.getTextRange().getStartOffset() == position.getTextRange().getStartOffset()) {
      PsiElement parent = type.getParent();
      if (parent instanceof PsiVariable) parent = parent.getParent();
      return parent instanceof PsiCatchSection || parent instanceof PsiResourceList;
    }
    return psiElement().insideStarting(psiElement(PsiResourceExpression.class)).accepts(position);
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
    else if (psiElement().inside(PsiSwitchExpression.class).accepts(myPosition) &&
             PsiUtil.getLanguageLevel(myPosition).isAtLeast(LanguageLevel.JDK_13_PREVIEW)) {
      addKeyword(TailTypeDecorator.withTail(createKeyword(PsiKeyword.YIELD), TailType.INSERT_SPACE));
    }

    for (PsiLabeledStatement labeled : psiApi().parents(myPosition).takeWhile(notInstanceOf(PsiMember.class)).filter(PsiLabeledStatement.class)) {
      addKeyword(TailTypeDecorator.withTail(LookupElementBuilder.create("break " + labeled.getName()).bold(), TailType.SEMICOLON));
    }
  }

  private static boolean isStatementPosition(PsiElement position) {
    if (psiElement()
        .withSuperParent(2, PsiConditionalExpression.class)
        .andNot(psiElement().insideStarting(psiElement(PsiConditionalExpression.class)))
        .accepts(position)) {
      return false;
    }

    if (isEndOfBlock(position) &&
        PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, true, PsiMember.class) != null) {
      return !isForLoopMachinery(position);
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

  private static boolean isForLoopMachinery(PsiElement position) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiStatement.class);
    if (statement == null) return false;

    return statement instanceof PsiForStatement ||
           statement.getParent() instanceof PsiForStatement && statement != ((PsiForStatement)statement.getParent()).getBody();
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
        if (resolveHelper.isAccessible(psiMethod, method, null) && !psiMethod.getParameterList().isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private void addModuleKeywords() {
    PsiElement context = PsiTreeUtil.skipParentsOfType(myPosition.getParent(), PsiErrorElement.class);
    PsiElement prevElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(myPosition.getParent());

    if (context instanceof PsiJavaFile && !(prevElement instanceof  PsiJavaModule) || context instanceof PsiImportList) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.MODULE), TailType.HUMBLE_SPACE_BEFORE_WORD));
      if (myPrevLeaf == null || !myPrevLeaf.textMatches(PsiKeyword.OPEN)) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.OPEN), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }
    else if (context instanceof PsiJavaModule) {
      if (prevElement instanceof PsiPackageAccessibilityStatement && !myPrevLeaf.textMatches(";")) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.TO), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      else if (!PsiUtil.isJavaToken(prevElement, JavaTokenType.MODULE_KEYWORD)) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.REQUIRES), TailType.HUMBLE_SPACE_BEFORE_WORD));
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.EXPORTS), TailType.HUMBLE_SPACE_BEFORE_WORD));
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.OPENS), TailType.HUMBLE_SPACE_BEFORE_WORD));
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.USES), TailType.HUMBLE_SPACE_BEFORE_WORD));
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.PROVIDES), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }
    else if (context instanceof PsiRequiresStatement) {
      if (!myPrevLeaf.textMatches(PsiKeyword.TRANSITIVE)) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.TRANSITIVE), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      if (!myPrevLeaf.textMatches(PsiKeyword.STATIC)) {
        addKeyword(new OverridableSpace(createKeyword(PsiKeyword.STATIC), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }
    else if (context instanceof PsiProvidesStatement && prevElement instanceof PsiJavaCodeReferenceElement) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.WITH), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  public static class OverridableSpace extends TailTypeDecorator<LookupElement> {
    private final TailType myTail;

    public OverridableSpace(LookupElement keyword, TailType tail) {
      super(keyword);
      myTail = tail;
    }

    @Override
    protected TailType computeTailType(InsertionContext context) {
      return context.shouldAddCompletionChar() ? TailType.NONE : myTail;
    }
  }
}
