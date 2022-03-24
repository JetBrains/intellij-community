// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.util.CompletionStyleUtil;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.lookup.*;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.intellij.openapi.util.Conditions.notInstanceOf;
import static com.intellij.patterns.PsiJavaPatterns.*;
import static com.intellij.psi.SyntaxTraverser.psiApi;

public class JavaKeywordCompletion {
  public static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".");

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
    psiElement().afterLeaf(psiElement().withText("{").withParents(PsiCodeBlock.class, PsiSwitchBlock.class));

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
    if (psiElement()
      .withText("}")
      .withParent(psiElement(PsiCodeBlock.class).withParent(or(psiElement(PsiTryStatement.class), psiElement(PsiCatchSection.class))))
      .accepts(myPrevLeaf)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.CATCH), TailTypes.CATCH_LPARENTH));
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.FINALLY), TailTypes.FINALLY_LBRACE));
      if (myPrevLeaf.getParent().getNextSibling() instanceof PsiErrorElement) {
        return;
      }
    }

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

    if (!psiElement().inside(PsiSwitchExpression.class).accepts(myPosition) || psiElement().inside(PsiLambdaExpression.class).accepts(myPosition)) {
      TailType returnTail = getReturnTail(myPosition);
      LookupElement ret = createKeyword(PsiKeyword.RETURN);
      if (returnTail != TailType.NONE) {
        ret = new OverridableSpace(ret, returnTail);
      }
      addKeyword(ret);
    }

    if (psiElement().withText(";").withSuperParent(2, PsiIfStatement.class).accepts(myPrevLeaf) ||
        psiElement().withText("}").withSuperParent(3, PsiIfStatement.class).accepts(myPrevLeaf)) {
      LookupElement elseKeyword = new OverridableSpace(createKeyword(PsiKeyword.ELSE), TailType.HUMBLE_SPACE_BEFORE_WORD);
      CharSequence text = myParameters.getEditor().getDocument().getCharsSequence();
      int offset = myParameters.getOffset();
      while (text.length() > offset && Character.isWhitespace(text.charAt(offset))) {
        offset++;
      }
      if (text.length() > offset + PsiKeyword.ELSE.length() &&
          text.subSequence(offset, offset + PsiKeyword.ELSE.length()).toString().equals(PsiKeyword.ELSE) &&
          Character.isWhitespace(text.charAt(offset + PsiKeyword.ELSE.length()))) {
        elseKeyword = PrioritizedLookupElement.withPriority(elseKeyword, -1);
      }
      addKeyword(elseKeyword);
    }
  }

  void addKeywords() {
    if (PsiTreeUtil.getNonStrictParentOfType(myPosition, PsiLiteralExpression.class, PsiComment.class) != null) {
      return;
    }

    if (psiElement().afterLeaf("::").accepts(myPosition)) {
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

      addPatternMatchingInSwitchCases();
      if (START_SWITCH.accepts(myPosition)) {
        return;
      }

      addBreakContinue();
      addStatementKeywords();

      if (myPrevLeaf.textMatches("}") &&
          myPrevLeaf.getParent() instanceof PsiCodeBlock &&
          myPrevLeaf.getParent().getParent() instanceof PsiTryStatement &&
          myPrevLeaf.getParent().getNextSibling() instanceof PsiErrorElement) {
        return;
      }
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

    addCaseNullToSwitch();
  }

  private void addCaseNullToSwitch() {
    if (!isInsideCaseLabel()) return;

    final PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(myPosition, PsiSwitchBlock.class, false, PsiMember.class);
    if (switchBlock == null) return;

    final PsiType selectorType = getSelectorType(switchBlock);
    if (selectorType instanceof PsiPrimitiveType) return;

    addKeyword(createKeyword(PsiKeyword.NULL));
    addKeyword(createKeyword(PsiKeyword.DEFAULT));
  }

  private boolean isInsideCaseLabel() {
    if (!HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(myPosition)) return false;
    return psiElement().withSuperParent(2, PsiCaseLabelElementList.class).accepts(myPosition);

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
    if (switchBlock == null) return;

    addKeyword(new OverridableSpace(createKeyword(PsiKeyword.CASE), TailType.INSERT_SPACE));

    final OverridableSpace defaultCaseRule = new OverridableSpace(createKeyword(PsiKeyword.DEFAULT), TailTypes.forSwitchLabel(switchBlock));
    addKeyword(LookupElementDecorator.withInsertHandler(defaultCaseRule, ADJUST_LINE_OFFSET));
  }

  private void addPatternMatchingInSwitchCases() {
    if (!HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(myPosition)) return;

    PsiSwitchBlock switchBlock = getSwitchFromLabelPosition(myPosition);
    if (switchBlock == null) return;

    final PsiType selectorType = getSelectorType(switchBlock);
    if (selectorType == null || selectorType instanceof PsiPrimitiveType) return;

    final TailType caseRuleTail = TailTypes.forSwitchLabel(switchBlock);
    addKeyword(createCaseRule(PsiKeyword.NULL, caseRuleTail));
    addKeyword(createCaseRule(PsiKeyword.DEFAULT, caseRuleTail));

    addSealedHierarchyCases(selectorType);
  }

  @Contract(pure = true)
  private static @Nullable PsiType getSelectorType(@NotNull PsiSwitchBlock switchBlock) {

    final PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return null;

    return selector.getType();
  }

  private void addSealedHierarchyCases(@NotNull PsiType type) {
    final PsiResolveHelper resolver = JavaPsiFacade.getInstance(myPosition.getProject()).getResolveHelper();

    final PsiClass aClass = resolver.resolveReferencedClass(type.getCanonicalText(), null);
    if (aClass == null || aClass.isEnum() || !aClass.hasModifierProperty(PsiModifier.SEALED)) return;

    for (PsiClass inheritor : SealedUtils.findSameFileInheritorsClasses(aClass)) {
      final JavaPsiClassReferenceElement item = AllClassesGetter.createLookupItem(inheritor, AllClassesGetter.TRY_SHORTENING);
      item.setForcedPresentableName("case " + inheritor.getName());
      addKeyword(item);
    }
  }

  private static @NotNull LookupElement createCaseRule(@NotNull String caseRuleName, TailType tailType) {
    final String prefix = "case ";

    final LookupElement lookupElement = LookupElementBuilder
      .create(prefix + caseRuleName)
      .bold()
      .withPresentableText(prefix)
      .withTailText(caseRuleName)
      .withLookupString(caseRuleName);

    return new JavaCompletionContributor.IndentingDecorator(TailTypeDecorator.withTail(lookupElement, tailType));
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
      if (!psiElement().withSuperParent(2, PsiSwitchBlock.class).afterLeaf("{").accepts(statement)) {
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

  private boolean isQualifiedNewContext() {
    if (myPosition.getParent() instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)myPosition.getParent()).getQualifierExpression();
      PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifier == null ? null : qualifier.getType());
      return qualifierClass != null &&
             ContainerUtil.exists(qualifierClass.getAllInnerClasses(), inner -> canBeCreatedInQualifiedNew(qualifierClass, inner));
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
        addKeyword(new OverridableSpace(LookupElementBuilder.create("abstract class").bold(), TailType.HUMBLE_SPACE_BEFORE_WORD));
        if (HighlightingFeature.RECORDS.isAvailable(myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(PsiKeyword.RECORD), TailType.HUMBLE_SPACE_BEFORE_WORD));
        }
        if (HighlightingFeature.LOCAL_ENUMS.isAvailable(myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(PsiKeyword.ENUM), TailType.HUMBLE_SPACE_BEFORE_WORD));
        }
        if (HighlightingFeature.LOCAL_INTERFACES.isAvailable(myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(PsiKeyword.INTERFACE), TailType.HUMBLE_SPACE_BEFORE_WORD));
        }
      }
      if (PsiTreeUtil.getParentOfType(myPosition, PsiExpression.class, true, PsiMember.class) == null &&
          PsiTreeUtil.getParentOfType(myPosition, PsiCodeBlock.class, true, PsiMember.class) == null) {
        List<String> keywords = new ArrayList<>();
        keywords.add(PsiKeyword.CLASS);
        keywords.add(PsiKeyword.INTERFACE);
        if (HighlightingFeature.RECORDS.isAvailable(myPosition)) {
          keywords.add(PsiKeyword.RECORD);
        }
        if (PsiUtil.isLanguageLevel5OrHigher(myPosition)) {
          keywords.add(PsiKeyword.ENUM);
        }
        String className = recommendClassName();
        for (String keyword : keywords) {
          if (className == null) {
            addKeyword(new OverridableSpace(createKeyword(keyword), TailType.HUMBLE_SPACE_BEFORE_WORD));
          } else {
            addKeyword(createTypeDeclaration(keyword, className));
          }
        }
      }
    }

    if (psiElement().withText("@").andNot(psiElement().inside(PsiParameterList.class)).andNot(psiElement().inside(psiNameValuePair()))
      .accepts(myPrevLeaf)) {
      addKeyword(new OverridableSpace(createKeyword(PsiKeyword.INTERFACE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  @NotNull
  private LookupElement createTypeDeclaration(String keyword, String className) {
    LookupElement element;
    PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(PsiTreeUtil.nextLeaf(myPosition));
    IElementType nextToken = nextElement instanceof PsiJavaToken ? ((PsiJavaToken)nextElement).getTokenType() : null;
    element = LookupElementBuilder.create(keyword + " " + className).withPresentableText(keyword).bold()
      .withTailText(" " + className, false)
      .withIcon(CreateClassKind.valueOf(keyword.toUpperCase(Locale.ROOT)).getKindIcon())
      .withInsertHandler((context, item) -> {
        Document document = context.getDocument();
        int offset = context.getTailOffset();
        String suffix = " ";
        if (keyword.equals(PsiKeyword.RECORD)) {
          if (JavaTokenType.LPARENTH.equals(nextToken)) {
            suffix = "";
          }
          else if (JavaTokenType.LBRACE.equals(nextToken)) {
            suffix = "() ";
          }
          else {
            suffix = "() {\n}";
          }
        }
        else if (!JavaTokenType.LBRACE.equals(nextToken)) {
          suffix = " {\n}";
        }
        if (offset < document.getTextLength() && document.getCharsSequence().charAt(offset) == ' ') {
          suffix = suffix.trim();
        }
        document.insertString(offset, suffix);
        context.getEditor().getCaretModel().moveToOffset(offset + 1);
      });
    return element;
  }

  @Nullable
  private String recommendClassName() {
    if (myPrevLeaf == null) return null;
    if (!myPrevLeaf.textMatches(PsiKeyword.PUBLIC) || !(myPrevLeaf.getParent() instanceof PsiModifierList)) return null;
    if (PsiTreeUtil.skipWhitespacesAndCommentsForward(PsiTreeUtil.nextLeaf(myPosition)) instanceof PsiIdentifier) return null;
    PsiJavaFile file = ObjectUtils.tryCast(myPrevLeaf.getParent().getParent(), PsiJavaFile.class);
    if (file == null) return null;
    String name = file.getName();
    if (!StringUtil.endsWithIgnoreCase(name, JavaFileType.DOT_DEFAULT_EXTENSION)) return null;
    String candidate = name.substring(0, name.length() - JavaFileType.DOT_DEFAULT_EXTENSION.length());
    if (StringUtil.isJavaIdentifier(candidate) && !ContainerUtil.exists(file.getClasses(), c -> candidate.equals(c.getName()))) {
      return candidate;
    }
    return null;
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
        if (HighlightingFeature.SEALED_CLASSES.isAvailable(psiClass)) {
          PsiModifierList modifiers = psiClass.getModifierList();
          if (myParameters.getInvocationCount() > 1 ||
              (modifiers != null &&
               !modifiers.hasExplicitModifier(PsiModifier.FINAL) &&
               !modifiers.hasExplicitModifier(PsiModifier.NON_SEALED))) {
            InsertHandler<LookupElement> handler = (context, item) -> {
              PsiClass aClass = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiClass.class, false);
              if (aClass != null) {
                PsiModifierList modifierList = aClass.getModifierList();
                if (modifierList != null) {
                  modifierList.setModifierProperty(PsiModifier.SEALED, true);
                }
              }
            };
            LookupElement element = new OverridableSpace(LookupElementDecorator.withInsertHandler(createKeyword(PsiKeyword.PERMITS), handler),
                                                         TailType.HUMBLE_SPACE_BEFORE_WORD);
            addKeyword(element);
          }
        }
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
      PsiType type = ((PsiLocalVariable) position.getParent()).getType();
      if (type instanceof PsiClassType && ((PsiClassType) type).resolve() == null) {
        PsiElement grandParent = position.getParent().getParent();
        return !(grandParent instanceof PsiDeclarationStatement) || !(grandParent.getParent() instanceof PsiForStatement) ||
               ((PsiForStatement)grandParent.getParent()).getInitialization() != grandParent;
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
        psiElement().inside(psiAnnotation()).accepts(position) && !expectsClassLiteral(position)) {
      return;
    }

    boolean afterNew = JavaSmartCompletionContributor.AFTER_NEW.accepts(position) &&
                       !psiElement().afterLeaf(psiElement().afterLeaf(".")).accepts(position);
    if (afterNew) {
      Set<PsiType> expected = ContainerUtil.map2Set(JavaSmartCompletionContributor.getExpectedTypes(position, false),
                                                    ExpectedTypeInfo::getDefaultType);
      boolean addAll = expected.isEmpty() || ContainerUtil.exists(expected, t ->
        t.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || t.equalsToText(CommonClassNames.JAVA_IO_SERIALIZABLE));
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(position.getProject());
      for (String primitiveType : PRIMITIVE_TYPES) {
        PsiType array = factory.createTypeFromText(primitiveType + "[]", null);
        if (addAll || expected.contains(array)) {
          result.consume(PsiTypeLookupItem.createLookupItem(array, null));
        }
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
    PsiElement parent = position.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement && parent.getParent() instanceof PsiTypeElement &&
        parent.getParent().getParent() instanceof PsiDeclarationStatement) {
      return true;
    }
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
             PsiUtil.getLanguageLevel(myPosition).isAtLeast(LanguageLevel.JDK_14)) {
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
      return ifStatement.getElseBranch() == stmt || ifStatement.getThenBranch() == stmt;
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
