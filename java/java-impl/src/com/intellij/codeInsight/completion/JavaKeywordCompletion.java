// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.util.CompletionStyleUtil;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.lookup.*;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SealedUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.completion.JavaCompletionContributor.IN_CASE_LABEL_ELEMENT_LIST;
import static com.intellij.openapi.util.Conditions.notInstanceOf;
import static com.intellij.patterns.PsiJavaPatterns.*;
import static com.intellij.psi.SyntaxTraverser.psiApi;

public class JavaKeywordCompletion {
  public static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".");

  static final ElementPattern<PsiElement> VARIABLE_AFTER_FINAL =
    psiElement().afterLeaf(JavaKeywords.FINAL).inside(PsiDeclarationStatement.class);

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
        not(psiElement().afterLeaf(JavaKeywords.CASE)),
        not(psiElement().afterLeaf(psiElement().withText(".").afterLeaf(JavaKeywords.THIS, JavaKeywords.SUPER))),
        not(psiElement().inside(PsiAnnotation.class)),
        not(START_SWITCH),
        not(JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN));

  static final Set<String> PRIMITIVE_TYPES = ContainerUtil.newLinkedHashSet(
    JavaKeywords.SHORT, JavaKeywords.BOOLEAN,
    JavaKeywords.DOUBLE, JavaKeywords.LONG,
    JavaKeywords.INT, JavaKeywords.FLOAT,
    JavaKeywords.CHAR, JavaKeywords.BYTE
  );

  static final PsiElementPattern<PsiElement, ?> START_FOR = psiElement().afterLeaf(psiElement().withText("(").afterLeaf("for"));
  private static final ElementPattern<PsiElement> CLASS_REFERENCE =
    psiElement().withParent(psiReferenceExpression().referencing(psiClass().andNot(psiElement(PsiTypeParameter.class))));

  private final CompletionParameters myParameters;
  private final JavaCompletionSession mySession;
  private final PsiElement myPosition;
  private final PrefixMatcher myKeywordMatcher;
  private final List<LookupElement> myResults = new ArrayList<>();
  private final PsiElement myPrevLeaf;

  JavaKeywordCompletion(CompletionParameters parameters, JavaCompletionSession session, boolean isSmart) {
    myParameters = parameters;
    mySession = session;
    myKeywordMatcher = new StartOnlyMatcher(session.getMatcher());
    myPosition = parameters.getPosition();
    myPrevLeaf = prevSignificantLeaf(myPosition);
    if (!isSmart) {
      addKeywords();
    }
    addEnumCases();
    addEnhancedCases();
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
    while (true) {
      if (scope instanceof PsiFile || scope instanceof PsiClassInitializer) {
        return TailTypes.noneType();
      }

      if (scope instanceof PsiMethod method) {
        if (method.isConstructor() || PsiTypes.voidType().equals(method.getReturnType())) {
          return TailTypes.semicolonType();
        }

        return TailTypes.humbleSpaceBeforeWordType();
      }
      if (scope instanceof PsiLambdaExpression lambda) {
        final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
        if (PsiTypes.voidType().equals(returnType)) {
          return TailTypes.semicolonType();
        }
        return TailTypes.humbleSpaceBeforeWordType();
      }
      scope = scope.getParent();
    }
  }

  private void addStatementKeywords() {
    if (psiElement()
      .withText("}")
      .withParent(psiElement(PsiCodeBlock.class).withParent(or(psiElement(PsiTryStatement.class), psiElement(PsiCatchSection.class))))
      .accepts(myPrevLeaf)) {
      addKeyword(new OverridableSpace(createKeyword(JavaKeywords.CATCH), JavaTailTypes.CATCH_LPARENTH));
      addKeyword(new OverridableSpace(createKeyword(JavaKeywords.FINALLY), JavaTailTypes.FINALLY_LBRACE));
      List<LookupElement> elements = CatchLookupElement.create(myPrevLeaf);
      for (LookupElement element : elements) {
        addKeyword(element);
      }
      if (myPrevLeaf.getParent().getNextSibling() instanceof PsiErrorElement) {
        return;
      }
    }

    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.SWITCH), JavaTailTypes.SWITCH_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.WHILE), JavaTailTypes.WHILE_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.DO), JavaTailTypes.DO_LBRACE));
    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.FOR), JavaTailTypes.FOR_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.IF), JavaTailTypes.IF_LPARENTH));
    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.TRY), JavaTailTypes.TRY_LBRACE));
    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.THROW), TailTypes.insertSpaceType()));
    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.NEW), TailTypes.insertSpaceType()));
    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.SYNCHRONIZED), JavaTailTypes.SYNCHRONIZED_LPARENTH));

    if (PsiUtil.isAvailable(JavaFeature.ASSERTIONS, myPosition)) {
      addKeyword(new OverridableSpace(createKeyword(JavaKeywords.ASSERT), TailTypes.insertSpaceType()));
    }

    if (!psiElement().inside(PsiSwitchExpression.class).accepts(myPosition) ||
        psiElement().inside(PsiLambdaExpression.class).accepts(myPosition)) {
      addKeyword(createReturnKeyword());
    }

    if (psiElement().withText(";").withSuperParent(2, PsiIfStatement.class).accepts(myPrevLeaf) ||
        psiElement().withText("}").withSuperParent(3, PsiIfStatement.class).accepts(myPrevLeaf)) {
      LookupElement elseKeyword = new OverridableSpace(createKeyword(JavaKeywords.ELSE), TailTypes.humbleSpaceBeforeWordType());
      CharSequence text = myParameters.getEditor().getDocument().getCharsSequence();
      int offset = myParameters.getOffset();
      while (text.length() > offset && Character.isWhitespace(text.charAt(offset))) {
        offset++;
      }
      if (text.length() > offset + JavaKeywords.ELSE.length() &&
          text.subSequence(offset, offset + JavaKeywords.ELSE.length()).toString().equals(JavaKeywords.ELSE) &&
          Character.isWhitespace(text.charAt(offset + JavaKeywords.ELSE.length()))) {
        elseKeyword = PrioritizedLookupElement.withPriority(elseKeyword, -1);
      }
      addKeyword(elseKeyword);
    }
  }

  private @NotNull LookupElement createReturnKeyword() {
    TailType returnTail = getReturnTail(myPosition);
    LookupElement ret = createKeyword(JavaKeywords.RETURN);
    if (returnTail != TailTypes.noneType()) {
      ret = new OverridableSpace(ret, returnTail);
    }
    return ret;
  }

  void addKeywords() {
    if (!canAddKeywords()) return;

    PsiFile file = myPosition.getContainingFile();
    if (PsiJavaModule.MODULE_INFO_FILE.equals(file.getName()) && PsiUtil.isAvailable(JavaFeature.MODULES, file)) {
      addModuleKeywords();
      return;
    }

    addFinal();
    addWhen();
    boolean statementPosition = isStatementPosition(myPosition);
    if (statementPosition) {

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
    else {
      PsiSwitchLabeledRuleStatement rule = findEnclosingSwitchRule(myPosition);
      if (rule != null) {
        addSwitchRuleKeywords(rule);
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

  void addEnhancedCases() {
    if (!canAddKeywords()) return;

    boolean statementPosition = isStatementPosition(myPosition);
    if (statementPosition) {
      addCaseDefault();

      addPatternMatchingInSwitchCases();
    }
    if (IN_CASE_LABEL_ELEMENT_LIST.accepts(myPosition)) {
      addCaseAfterNullDefault();
    }
  }

  private void addCaseAfterNullDefault() {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, myPosition)) return;
    PsiCaseLabelElementList labels = PsiTreeUtil.getParentOfType(myPosition, PsiCaseLabelElementList.class);
    if (labels == null || labels.getElementCount() != 2 ||
        !(labels.getElements()[0] instanceof PsiLiteralExpression literalExpression &&
          ExpressionUtils.isNullLiteral(literalExpression))) {
      return;
    }

    PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(labels, PsiSwitchBlock.class);
    if (switchBlock == null) return;
    List<PsiSwitchLabelStatementBase> allBranches =
      PsiTreeUtil.getChildrenOfTypeAsList(switchBlock.getBody(), PsiSwitchLabelStatementBase.class);
    if (allBranches.isEmpty() || allBranches.get(allBranches.size() - 1).getCaseLabelElementList() != labels) {
      return;
    }
    if (JavaPsiSwitchUtil.findDefaultElement(switchBlock) != null) {
      return;
    }

    final OverridableSpace defaultCaseRule =
      new OverridableSpace(createKeyword(JavaKeywords.DEFAULT), JavaTailTypes.forSwitchLabel(switchBlock));
    addKeyword(prioritizeForRule(LookupElementDecorator.withInsertHandler(defaultCaseRule, ADJUST_LINE_OFFSET), switchBlock));
  }

  private boolean canAddKeywords() {
    if (PsiTreeUtil.getNonStrictParentOfType(myPosition, PsiLiteralExpression.class, PsiComment.class) != null) {
      return false;
    }

    if (psiElement().afterLeaf("::").accepts(myPosition)) {
      return false;
    }
    return true;
  }

  private void addWhen() {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, myPosition)) {
      return;
    }
    PsiElement element = PsiTreeUtil.skipWhitespacesAndCommentsForward(myPrevLeaf);
    if (element instanceof PsiErrorElement) {
      return;
    }

    element = PsiTreeUtil.skipWhitespacesAndCommentsBackward(PsiTreeUtil.prevLeaf(myPosition));
    if (element instanceof PsiErrorElement) {
      return;
    }

    PsiPattern psiPattern =
      PsiTreeUtil.getParentOfType(myPrevLeaf, PsiPattern.class, true, PsiStatement.class, PsiMember.class, PsiClass.class);
    if (psiPattern == null ||
        (psiPattern instanceof PsiTypeTestPattern testPattern &&
         testPattern.getPatternVariable() != null &&
         testPattern.getPatternVariable().getNameIdentifier() == myPosition)) {
      return;
    }
    PsiElement parentOfPattern = PsiTreeUtil.skipParentsOfType(psiPattern, PsiPattern.class, PsiDeconstructionList.class);
    if (!(parentOfPattern instanceof PsiCaseLabelElementList)) {
      return;
    }
    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.WHEN), TailTypes.insertSpaceType()));
  }

  private void addSwitchRuleKeywords(@NotNull PsiSwitchLabeledRuleStatement rule) {
    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.THROW), TailTypes.insertSpaceType()));
    addKeyword(wrapRuleIntoBlock(new OverridableSpace(createKeyword(JavaKeywords.ASSERT), TailTypes.insertSpaceType())));
    addKeyword(wrapRuleIntoBlock(new OverridableSpace(createKeyword(JavaKeywords.WHILE), JavaTailTypes.WHILE_LPARENTH)));
    addKeyword(wrapRuleIntoBlock(new OverridableSpace(createKeyword(JavaKeywords.DO), JavaTailTypes.DO_LBRACE)));
    addKeyword(wrapRuleIntoBlock(new OverridableSpace(createKeyword(JavaKeywords.FOR), JavaTailTypes.FOR_LPARENTH)));
    addKeyword(wrapRuleIntoBlock(new OverridableSpace(createKeyword(JavaKeywords.IF), JavaTailTypes.IF_LPARENTH)));
    addKeyword(wrapRuleIntoBlock(new OverridableSpace(createKeyword(JavaKeywords.TRY), JavaTailTypes.TRY_LBRACE)));
    if (rule.getEnclosingSwitchBlock() instanceof PsiSwitchStatement) {
      addKeyword(wrapRuleIntoBlock(createReturnKeyword()));
    }
    else {
      addKeyword(wrapRuleIntoBlock(new OverridableSpace(createKeyword(JavaKeywords.YIELD), TailTypes.insertSpaceType())));
    }
  }

  private static LookupElement wrapRuleIntoBlock(LookupElement element) {
    return new LookupElementDecorator<>(element) {
      @Override
      public void handleInsert(@NotNull InsertionContext context) {
        PsiStatement statement = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(context.getStartOffset()), PsiStatement.class);
        boolean isAfterArrow = false;
        if (statement != null) {
          if (statement.getParent() instanceof PsiCodeBlock) {
            PsiElement prevLeaf = PsiTreeUtil.prevCodeLeaf(statement);
            if (PsiUtil.isJavaToken(prevLeaf, JavaTokenType.ARROW) && prevLeaf.getParent() instanceof PsiSwitchLabeledRuleStatement) {
              isAfterArrow = true;
            }
          }
          else if (statement.getParent() instanceof PsiSwitchLabeledRuleStatement) {
            isAfterArrow = true;
          }
        }
        if (isAfterArrow) {
          CaretModel model = context.getEditor().getCaretModel();
          int origPos = model.getOffset();
          int start = statement.getTextRange().getStartOffset();
          PsiStatement updatedStatement = BlockUtils.expandSingleStatementToBlockStatement(statement);
          int updatedStart = updatedStatement.getTextRange().getStartOffset();
          model.moveToOffset(origPos + updatedStart - start);
          PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());
          context.setTailOffset(model.getOffset());
        }
        super.handleInsert(context);
      }
    };
  }

  private static PsiSwitchLabeledRuleStatement findEnclosingSwitchRule(PsiElement position) {
    PsiElement parent = position.getParent();
    return parent.getParent() instanceof PsiExpressionStatement stmt &&
           stmt.getParent() instanceof PsiSwitchLabeledRuleStatement rule ? rule : null;
  }

  private void addCaseNullToSwitch() {
    if (!isInsideCaseLabel()) return;

    final PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(myPosition, PsiSwitchBlock.class, false, PsiMember.class);
    if (switchBlock == null) return;

    final PsiType selectorType = getSelectorType(switchBlock);
    if (selectorType instanceof PsiPrimitiveType) return;

    addKeyword(createKeyword(JavaKeywords.NULL));
  }

  private boolean isInsideCaseLabel() {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, myPosition)) return false;
    return psiElement().withSuperParent(2, PsiCaseLabelElementList.class).accepts(myPosition);
  }

  private void addVar() {
    if (isVarAllowed()) {
      addKeyword(createKeyword(JavaKeywords.VAR));
    }
  }

  private boolean isVarAllowed() {
    if (PsiUtil.isAvailable(JavaFeature.VAR_LAMBDA_PARAMETER, myPosition) && isLambdaParameterType()) {
      return true;
    }

    if (!PsiUtil.isAvailable(JavaFeature.LVTI, myPosition)) return false;

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
      PsiTypeElement type = param == null ? null : param.getTypeElement();
      return type == null || PsiTreeUtil.isAncestor(type, position, false);
    }
    return false;
  }

  boolean addWildcardExtendsSuper(CompletionResultSet result, PsiElement position) {
    if (JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) {
      for (String keyword : ContainerUtil.ar(JavaKeywords.EXTENDS, JavaKeywords.SUPER)) {
        if (myKeywordMatcher.isStartMatch(keyword)) {
          LookupElement item = BasicExpressionCompletionContributor.createKeywordLookupItem(position, keyword);
          result.addElement(new OverridableSpace(item, TailTypes.humbleSpaceBeforeWordType()));
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
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.DEFAULT), TailTypes.humbleSpaceBeforeWordType()));
      }
      else {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.THROWS), TailTypes.humbleSpaceBeforeWordType()));
      }
    }
  }

  private void addCaseDefault() {
    PsiSwitchBlock switchBlock = getSwitchFromLabelPosition(myPosition);
    if (switchBlock == null) return;
    PsiElement defaultElement = JavaPsiSwitchUtil.findDefaultElement(switchBlock);
    if (defaultElement != null && defaultElement.getTextRange().getStartOffset() < myPosition.getTextRange().getStartOffset()) return;
    addKeyword(new OverridableSpace(createKeyword(JavaKeywords.CASE), TailTypes.insertSpaceType()));
    if (defaultElement != null) {
      return;
    }
    final OverridableSpace defaultCaseRule =
      new OverridableSpace(createKeyword(JavaKeywords.DEFAULT), JavaTailTypes.forSwitchLabel(switchBlock));
    addKeyword(prioritizeForRule(LookupElementDecorator.withInsertHandler(defaultCaseRule, ADJUST_LINE_OFFSET), switchBlock));
  }

  private void addPatternMatchingInSwitchCases() {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, myPosition)) return;

    PsiSwitchBlock switchBlock = getSwitchFromLabelPosition(myPosition);
    if (switchBlock == null) return;

    final PsiType selectorType = getSelectorType(switchBlock);
    if (selectorType == null || selectorType instanceof PsiPrimitiveType) return;

    PsiElement defaultElement = JavaPsiSwitchUtil.findDefaultElement(switchBlock);
    if (defaultElement != null && defaultElement.getTextRange().getStartOffset() < myPosition.getTextRange().getStartOffset()) return;

    final TailType caseRuleTail = JavaTailTypes.forSwitchLabel(switchBlock);
    Set<String> containedLabels = getSwitchCoveredLabels(switchBlock, myPosition);
    if (!containedLabels.contains(JavaKeywords.NULL)) {
      addKeyword(createCaseRule(JavaKeywords.NULL, caseRuleTail, switchBlock));
      if (!containedLabels.contains(JavaKeywords.DEFAULT)) {
        addKeyword(createCaseRule(JavaKeywords.NULL + ", " + JavaKeywords.DEFAULT, caseRuleTail, switchBlock));
      }
    }
    addSealedHierarchyCases(selectorType, containedLabels);
  }

  private static @NotNull Set<String> getSwitchCoveredLabels(@Nullable PsiSwitchBlock block, PsiElement position) {
    HashSet<String> labels = new HashSet<>();
    if (block == null) {
      return labels;
    }
    PsiCodeBlock body = block.getBody();
    if (body == null) {
      return labels;
    }
    int offset = position.getTextRange().getStartOffset();
    for (PsiStatement statement : body.getStatements()) {
      if (!(statement instanceof PsiSwitchLabelStatementBase labelStatement)) continue;
      if (labelStatement.isDefaultCase()) {
        labels.add(JavaKeywords.DEFAULT);
        continue;
      }
      if (labelStatement.getGuardExpression() != null) {
        continue;
      }
      PsiCaseLabelElementList list = labelStatement.getCaseLabelElementList();
      if (list == null) {
        continue;
      }
      for (PsiCaseLabelElement element : list.getElements()) {
        if (element instanceof PsiExpression expr &&
            ExpressionUtils.isNullLiteral(expr)) {
          labels.add(JavaKeywords.NULL);
          continue;
        }
        if (element instanceof PsiDefaultCaseLabelElement) {
          labels.add(JavaKeywords.DEFAULT);
        }
        if (element.getTextRange().getStartOffset() >= offset) {
          break;
        }
        PsiType patternType = JavaPsiPatternUtil.getPatternType(element);
        if (patternType != null && JavaPsiPatternUtil.isUnconditionalForType(element, patternType)) {
          PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(patternType);
          if (psiClass == null) {
            continue;
          }
          labels.add(psiClass.getQualifiedName());
        }
      }
    }
    return labels;
  }

  @Contract(pure = true)
  private static @Nullable PsiType getSelectorType(@NotNull PsiSwitchBlock switchBlock) {

    final PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return null;

    return selector.getType();
  }

  private void addSealedHierarchyCases(@NotNull PsiType type, Set<String> containedLabels) {
    final PsiResolveHelper resolver = JavaPsiFacade.getInstance(myPosition.getProject()).getResolveHelper();
    PsiClass aClass = resolver.resolveReferencedClass(type.getCanonicalText(), null);
    if (aClass == null) {
      aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    }
    if (aClass == null || aClass.isEnum() || !aClass.hasModifierProperty(PsiModifier.SEALED)) return;

    for (PsiClass inheritor : SealedUtils.findSameFileInheritorsClasses(aClass)) {
      //we don't check hierarchy here, because it is time-consuming
      if (containedLabels.contains(inheritor.getQualifiedName())) {
        continue;
      }
      final JavaPsiClassReferenceElement item = AllClassesGetter.createLookupItem(inheritor, AllClassesGetter.TRY_SHORTENING);
      item.setForcedPresentableName("case " + inheritor.getName());
      addKeyword(PrioritizedLookupElement.withPriority(item, 1));
    }
  }

  private static @NotNull LookupElement createCaseRule(@NotNull String caseRuleName,
                                                       TailType tailType,
                                                       @Nullable PsiSwitchBlock switchBlock) {
    final String prefix = "case ";

    final LookupElement lookupElement = LookupElementBuilder
      .create(prefix + caseRuleName)
      .bold()
      .withPresentableText(prefix)
      .withTailText(caseRuleName)
      .withLookupString(caseRuleName);

    JavaCompletionContributor.IndentingDecorator decorator =
      new JavaCompletionContributor.IndentingDecorator(TailTypeDecorator.withTail(lookupElement, tailType));
    return prioritizeForRule(decorator, switchBlock);
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
    TailType tailType = JavaTailTypes.forSwitchLabel(switchBlock);
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
      LookupElement withPriority = prioritizeForRule(
        new JavaCompletionContributor.IndentingDecorator(TailTypeDecorator.withTail(caseConst, tailType)),
        switchBlock);
      myResults.add(withPriority);
    }
  }

  private static @NotNull LookupElement prioritizeForRule(@NotNull LookupElement decorator, @Nullable PsiSwitchBlock switchBlock) {
    if (switchBlock == null) {
      return decorator;
    }
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) {
      return decorator;
    }
    PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      return decorator;
    }
    if (statements[0] instanceof PsiSwitchLabeledRuleStatement) {
      return PrioritizedLookupElement.withPriority(decorator, 1);
    }
    return decorator;
  }

  private void addFinal() {
    PsiStatement statement = PsiTreeUtil.getParentOfType(myPosition, PsiExpressionStatement.class, PsiDeclarationStatement.class);
    if (statement != null && statement.getTextRange().getStartOffset() == myPosition.getTextRange().getStartOffset()) {
      if (!psiElement().withSuperParent(2, PsiSwitchBlock.class).afterLeaf("{").accepts(statement)) {
        PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(myPrevLeaf, PsiTryStatement.class);
        if (tryStatement == null ||
            tryStatement.getCatchSections().length > 0 ||
            tryStatement.getFinallyBlock() != null || tryStatement.getResourceList() != null) {
          LookupElement finalKeyword = new OverridableSpace(createKeyword(JavaKeywords.FINAL), TailTypes.humbleSpaceBeforeWordType());
          if (statement.getParent() instanceof PsiSwitchLabeledRuleStatement) {
            finalKeyword = wrapRuleIntoBlock(finalKeyword);
          }
          addKeyword(finalKeyword);
          return;
        }
      }
    }

    if ((isInsideParameterList(myPosition) || isAtCatchOrResourceVariableStart(myPosition)) &&
        !psiElement().afterLeaf(JavaKeywords.FINAL).accepts(myPosition) &&
        !AFTER_DOT.accepts(myPosition)) {
      addKeyword(TailTypeDecorator.withTail(createKeyword(JavaKeywords.FINAL), TailTypes.humbleSpaceBeforeWordType()));
    }
  }

  private void addThisSuper() {
    if (SUPER_OR_THIS_PATTERN.accepts(myPosition)) {
      final boolean afterDot = AFTER_DOT.accepts(myPosition);
      final boolean insideQualifierClass = isInsideQualifierClass();
      final boolean insideInheritorClass = PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, myPosition) && isInsideInheritorClass();
      if (!afterDot || insideQualifierClass || insideInheritorClass) {
        if (!afterDot || insideQualifierClass) {
          addKeyword(createKeyword(JavaKeywords.THIS));
        }

        final LookupElement superItem = createKeyword(JavaKeywords.SUPER);
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
          addKeyword(TailTypeDecorator.withTail(createKeyword(JavaKeywords.NEW), TailTypes.insertSpaceType()));
          if (PsiUtil.isAvailable(JavaFeature.ENHANCED_SWITCH, myPosition)) {
            addKeyword(new OverridableSpace(createKeyword(JavaKeywords.SWITCH), JavaTailTypes.SWITCH_LPARENTH));
          }
        }
        if (allowExprKeywords) {
          addKeyword(createKeyword(JavaKeywords.NULL));
        }
      }
      if (allowExprKeywords && mayExpectBoolean(myParameters)) {
        addKeyword(createKeyword(JavaKeywords.TRUE));
        addKeyword(createKeyword(JavaKeywords.FALSE));
      }
    }

    if (isQualifiedNewContext()) {
      addKeyword(createKeyword(JavaKeywords.NEW));
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
      PsiMember parentMember = PsiTreeUtil.getParentOfType(myPosition, PsiMember.class);
      boolean bogusDeclarationInImplicitClass =
        parentMember instanceof PsiField field &&
        field.getParent() instanceof PsiImplicitClass implicitClass &&
        StreamEx.of(implicitClass.getChildren()).select(PsiMember.class).findFirst().orElse(null) == field;
      if (myPrevLeaf == null ||
          bogusDeclarationInImplicitClass && file instanceof PsiJavaFile javaFile && javaFile.getPackageStatement() == null &&
          javaFile.getImportList() != null && javaFile.getImportList().getAllImportStatements().length == 0) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.PACKAGE), TailTypes.humbleSpaceBeforeWordType()));
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.IMPORT), TailTypes.humbleSpaceBeforeWordType()));
      }
      else if (psiElement().inside(psiAnnotation().withParents(PsiModifierList.class, PsiFile.class)).accepts(myPrevLeaf)
               && PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.PACKAGE), TailTypes.humbleSpaceBeforeWordType()));
      }
      else if (isEndOfBlock(myPosition) && (parentMember == null || bogusDeclarationInImplicitClass)) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.IMPORT), TailTypes.humbleSpaceBeforeWordType()));
      }
    }

    if (PsiUtil.isAvailable(JavaFeature.STATIC_IMPORTS, file) && myPrevLeaf != null && myPrevLeaf.textMatches(JavaKeywords.IMPORT)) {
      addKeyword(new OverridableSpace(createKeyword(JavaKeywords.STATIC), TailTypes.humbleSpaceBeforeWordType()));
    }

    if (PsiUtil.isAvailable(JavaFeature.MODULE_IMPORT_DECLARATIONS, file) && myPrevLeaf != null && myPrevLeaf.textMatches(JavaKeywords.IMPORT)) {
      addKeyword(new OverridableSpace(createKeyword(JavaKeywords.MODULE), TailTypes.humbleSpaceBeforeWordType()));
    }
  }

  private void addInstanceof() {
    if (isInstanceofPlace(myPosition)) {
      addKeyword(LookupElementDecorator.withInsertHandler(
        createKeyword(JavaKeywords.INSTANCEOF),
        (context, item) -> {
          TailType tailType = TailTypes.humbleSpaceBeforeWordType();
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
        addKeyword(new OverridableSpace(createKeyword(s), TailTypes.humbleSpaceBeforeWordType()));
      }

      if (psiElement().insideStarting(psiElement(PsiLocalVariable.class, PsiExpressionStatement.class)).accepts(myPosition)) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.CLASS), TailTypes.humbleSpaceBeforeWordType()));
        addKeyword(new OverridableSpace(LookupElementBuilder.create("abstract class").bold(), TailTypes.humbleSpaceBeforeWordType()));
        if (PsiUtil.isAvailable(JavaFeature.RECORDS, myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(JavaKeywords.RECORD), TailTypes.humbleSpaceBeforeWordType()));
        }
        if (PsiUtil.isAvailable(JavaFeature.LOCAL_ENUMS, myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(JavaKeywords.ENUM), TailTypes.humbleSpaceBeforeWordType()));
        }
        if (PsiUtil.isAvailable(JavaFeature.LOCAL_INTERFACES, myPosition)) {
          addKeyword(new OverridableSpace(createKeyword(JavaKeywords.INTERFACE), TailTypes.humbleSpaceBeforeWordType()));
        }
      }
      if (PsiTreeUtil.getParentOfType(myPosition, PsiExpression.class, true, PsiMember.class) == null &&
          PsiTreeUtil.getParentOfType(myPosition, PsiCodeBlock.class, true, PsiMember.class) == null) {
        List<String> keywords = new ArrayList<>();
        keywords.add(JavaKeywords.CLASS);
        keywords.add(JavaKeywords.INTERFACE);
        if (PsiUtil.isAvailable(JavaFeature.RECORDS, myPosition)) {
          keywords.add(JavaKeywords.RECORD);
        }
        if (PsiUtil.isAvailable(JavaFeature.ENUMS, myPosition)) {
          keywords.add(JavaKeywords.ENUM);
        }
        String className = recommendClassName();
        for (String keyword : keywords) {
          if (className == null) {
            addKeyword(new OverridableSpace(createKeyword(keyword), TailTypes.humbleSpaceBeforeWordType()));
          }
          else {
            addKeyword(createTypeDeclaration(keyword, className));
          }
        }
      }
    }

    if (psiElement().withText("@").andNot(psiElement().inside(PsiParameterList.class)).andNot(psiElement().inside(psiNameValuePair()))
      .accepts(myPrevLeaf)) {
      addKeyword(new OverridableSpace(createKeyword(JavaKeywords.INTERFACE), TailTypes.humbleSpaceBeforeWordType()));
    }
  }

  private @NotNull LookupElement createTypeDeclaration(String keyword, String className) {
    LookupElement element;
    PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(PsiTreeUtil.nextLeaf(myPosition));
    IElementType nextToken;
    if (nextElement instanceof PsiJavaToken) {
      nextToken = ((PsiJavaToken)nextElement).getTokenType();
    }
    else {
      if (nextElement instanceof PsiParameterList l && l.getFirstChild() instanceof PsiJavaToken t) {
        nextToken = t.getTokenType();
      }
      else if (nextElement instanceof PsiCodeBlock b && b.getFirstChild() instanceof PsiJavaToken t) {
        nextToken = t.getTokenType();
      }
      else {
        nextToken = null;
      }
    }
    element = LookupElementBuilder.create(keyword + " " + className).withPresentableText(keyword).bold()
      .withTailText(" " + className, false)
      .withIcon(CreateClassKind.valueOf(keyword.toUpperCase(Locale.ROOT)).getKindIcon())
      .withInsertHandler((context, item) -> {
        Document document = context.getDocument();
        int offset = context.getTailOffset();
        String suffix = " ";
        if (keyword.equals(JavaKeywords.RECORD)) {
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

  private @Nullable String recommendClassName() {
    if (myPrevLeaf == null) return null;
    if (!myPrevLeaf.textMatches(JavaKeywords.PUBLIC) || !(myPrevLeaf.getParent() instanceof PsiModifierList)) return null;

    if (nextIsIdentifier(myPosition)) return null;

    PsiJavaFile file = getFileForDeclaration(myPrevLeaf);
    if (file == null) return null;
    String name = file.getName();
    if (!StringUtil.endsWithIgnoreCase(name, JavaFileType.DOT_DEFAULT_EXTENSION)) return null;
    String candidate = name.substring(0, name.length() - JavaFileType.DOT_DEFAULT_EXTENSION.length());
    if (StringUtil.isJavaIdentifier(candidate)
        && !ContainerUtil.exists(file.getClasses(), c -> !(c instanceof PsiImplicitClass) && candidate.equals(c.getName()))) {
      return candidate;
    }
    return null;
  }

  private static boolean nextIsIdentifier(@NotNull PsiElement position) {
    PsiElement nextLeaf = PsiTreeUtil.nextLeaf(position);
    if (nextLeaf == null) return false;
    PsiElement parent = nextLeaf.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) return false;
    PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiTypeElement)) return false;
    return PsiTreeUtil.skipWhitespacesAndCommentsForward(grandParent) instanceof PsiIdentifier;
  }

  private static @Nullable PsiJavaFile getFileForDeclaration(@NotNull PsiElement elementBeforeName) {
    PsiElement parent = elementBeforeName.getParent();
    if (parent == null) return null;
    PsiElement grandParent = parent.getParent();
    if (grandParent == null) return null;
    if (grandParent instanceof PsiJavaFile f) {
      return f;
    }
    PsiElement grandGrandParent = grandParent.getParent();
    if (grandGrandParent == null) return null;
    return ObjectUtils.tryCast(grandGrandParent.getParent(), PsiJavaFile.class);
  }

  private void addClassLiteral() {
    if (isAfterTypeDot(myPosition)) {
      addKeyword(createKeyword(JavaKeywords.CLASS));
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
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.EXTENDS), TailTypes.humbleSpaceBeforeWordType()));
        if (PsiUtil.isAvailable(JavaFeature.SEALED_CLASSES, psiClass)) {
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
            LookupElement element =
              new OverridableSpace(LookupElementDecorator.withInsertHandler(createKeyword(JavaKeywords.PERMITS), handler),
                                   TailTypes.humbleSpaceBeforeWordType());
            addKeyword(element);
          }
        }
      }
      if (!psiClass.isInterface() && !(psiClass instanceof PsiTypeParameter)) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.IMPLEMENTS), TailTypes.humbleSpaceBeforeWordType()));
      }
    }
  }

  private static boolean mayExpectBoolean(CompletionParameters parameters) {
    for (ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
      PsiType type = info.getType();
      if (type instanceof PsiClassType || PsiTypes.booleanType().equals(type)) return true;
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
      return previous == null || previous.getLastChild() == null ||
             !(PsiTreeUtil.getDeepestLast(previous.getLastChild()) instanceof PsiErrorElement);
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
        PsiElement grandParent = position.getParent().getParent();
        return !(grandParent instanceof PsiDeclarationStatement) || !(grandParent.getParent() instanceof PsiForStatement) ||
               ((PsiForStatement)grandParent.getParent()).getInitialization() != grandParent;
      }
    }

    return false;
  }

  public static boolean isSuitableForClass(PsiElement position) {
    if (psiElement().afterLeaf("@").accepts(position) ||
        PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class, PsiExpressionCodeFragment.class) !=
        null) {
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
          not(psiElement().withText(JavaKeywords.CLASS))))).accepts(element);
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

    if (JavaPatternCompletionUtil.insideDeconstructionList(position)) {
      JavaPatternCompletionUtil.suggestPrimitivesInsideDeconstructionListPattern(position, result);
      return;
    }

    if (afterInstanceofForType(position)) {
      PsiInstanceOfExpression instanceOfExpression = PsiTreeUtil.getParentOfType(position, PsiInstanceOfExpression.class);
      if (instanceOfExpression != null) {
        JavaPatternCompletionUtil.suggestPrimitiveTypesForPattern(position, instanceOfExpression.getOperand().getType(), result);
      }
      return;
    }

    if (afterCaseForType(position)) {
      PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(position, PsiSwitchBlock.class);
      if (switchBlock != null && switchBlock.getExpression() != null) {
        JavaPatternCompletionUtil.suggestPrimitiveTypesForPattern(position, switchBlock.getExpression().getType(), result);
      }
      if (switchBlock != null && switchBlock.getExpression() != null) {
        PsiType type = switchBlock.getExpression().getType();
        if (PsiTypes.booleanType().equals(PsiPrimitiveType.getOptionallyUnboxedType(type))) {
          Set<String> branches = JavaPsiSwitchUtil.getSwitchBranches(switchBlock).stream()
            .map(branch -> branch instanceof PsiExpression expression ? ExpressionUtils.computeConstantExpression(expression) : null)
            .filter(constant -> constant instanceof Boolean)
            .map(branch -> branch.toString())
            .collect(Collectors.toSet());
          TailType tailType = JavaTailTypes.forSwitchLabel(switchBlock);
          for (String keyword : List.of(JavaKeywords.TRUE, JavaKeywords.FALSE)) {
            if(branches.contains(keyword)) continue;
            result.accept(new JavaKeywordCompletion.OverridableSpace(
              BasicExpressionCompletionContributor.createKeywordLookupItem(position, keyword), tailType));
          }
        }
      }
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
    if ((isVariableTypePosition(position) ||
        inGenerics ||
        inCast ||
        declaration ||
        typeFragment ||
        expressionPosition) && primitivesAreExpected(position)) {
      for (String primitiveType : PRIMITIVE_TYPES) {
        if (!session.isKeywordAlreadyProcessed(primitiveType)) {
          result.consume(BasicExpressionCompletionContributor.createKeywordLookupItem(position, primitiveType));
        }
      }
      if (expressionPosition && !session.isKeywordAlreadyProcessed(JavaKeywords.VOID)) {
        result.consume(BasicExpressionCompletionContributor.createKeywordLookupItem(position, JavaKeywords.VOID));
      }
    }
    if (declaration) {
      LookupElement item = BasicExpressionCompletionContributor.createKeywordLookupItem(position, JavaKeywords.VOID);
      result.consume(new OverridableSpace(item, TailTypes.humbleSpaceBeforeWordType()));
    }
    else if (typeFragment && ((PsiTypeCodeFragment)position.getContainingFile()).isVoidValid()) {
      result.consume(BasicExpressionCompletionContributor.createKeywordLookupItem(position, JavaKeywords.VOID));
    }
  }

  private static boolean primitivesAreExpected(@Nullable PsiElement position) {
    if (position == null) return false;
    PsiElement parent = position.getParent();
    //example: stream.map(i-> i <caret>)
    if (parent.getParent() instanceof PsiExpressionList) {
      PsiElement previous = PsiTreeUtil.prevVisibleLeaf(parent);
      if (previous != null) {
        PsiExpression expression = PsiTreeUtil.getParentOfType(previous, PsiExpression.class, true);
        if (expression != null && !PsiTreeUtil.isAncestor(expression, parent, true)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Checks if the given PsiElement is in a position where it occurs after a case keyword for a specific type.
   * <p>
   * Example:
   * <pre><code>
   *   switch(i){
   *     case <caret>
   *   }
   * </code></pre>
   * @param position the PsiElement to check
   * @return true if the position occurs after a case keyword for a specific type, false otherwise
   */
  private static boolean afterCaseForType(@Nullable PsiElement position) {
    if (position == null) return false;
    return psiElement().afterLeaf(JavaKeywords.CASE).accepts(position) &&
           ((position.getParent() instanceof PsiReferenceExpression referenceExpression &&
             referenceExpression.getParent() instanceof PsiCaseLabelElementList)
            || (position.getParent() instanceof PsiJavaCodeReferenceElement referenceElement &&
                referenceElement.getParent() instanceof PsiTypeElement typeElement &&
                typeElement.getParent() instanceof PsiPatternVariable patternVariable &&
                patternVariable.getParent() instanceof PsiTypeTestPattern typeTestPattern &&
                typeTestPattern.getParent() instanceof PsiCaseLabelElementList));
  }

  /**
   * Checks if the given PsiElement is in a position where it occurs after an instanceof keyword for a specific type.
   * Example:
   * <pre><code>
   *   if(i instanceof <caret>)
   * </code></pre>
   * @param position the PsiElement to check
   * @return true if the position occurs after an instanceof keyword for a specific type, false otherwise
   */
  private static boolean afterInstanceofForType(@Nullable PsiElement position) {
    if (position == null) return false;
    return (InstanceofTypeProvider.AFTER_INSTANCEOF.accepts(position)) &&
           position.getParent() instanceof PsiJavaCodeReferenceElement referenceElement &&
           referenceElement.getParent() instanceof PsiTypeElement typeElement &&
           (typeElement.getParent() instanceof PsiInstanceOfExpression ||
            (typeElement.getParent() instanceof PsiPatternVariable variable &&
             (variable.getParent() instanceof PsiInstanceOfExpression ||
              variable.getParent() instanceof PsiTypeTestPattern typeTestPattern &&
              typeTestPattern.getParent() instanceof PsiInstanceOfExpression)));
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
     return typeHolder instanceof PsiMember || typeHolder instanceof PsiClassLevelDeclarationStatement ||
             (typeHolder instanceof PsiJavaFile javaFile &&
              PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, position) &&
              javaFile.getPackageStatement() == null);
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
    PsiLoopStatement loop =
      PsiTreeUtil.getParentOfType(myPosition, PsiLoopStatement.class, true, PsiLambdaExpression.class, PsiMember.class);

    LookupElement br = createKeyword(JavaKeywords.BREAK);
    LookupElement cont = createKeyword(JavaKeywords.CONTINUE);
    TailType tailType;
    if (psiElement().insideSequence(true, psiElement(PsiLabeledStatement.class),
                                    or(psiElement(PsiFile.class), psiElement(PsiMethod.class),
                                       psiElement(PsiClassInitializer.class))).accepts(myPosition)) {
      tailType = TailTypes.humbleSpaceBeforeWordType();
    }
    else {
      tailType = TailTypes.semicolonType();
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
             PsiUtil.isAvailable(JavaFeature.SWITCH_EXPRESSION, myPosition)) {
      addKeyword(TailTypeDecorator.withTail(createKeyword(JavaKeywords.YIELD), TailTypes.insertSpaceType()));
    }

    for (PsiLabeledStatement labeled : psiApi().parents(myPosition).takeWhile(notInstanceOf(PsiMember.class))
      .filter(PsiLabeledStatement.class)) {
      addKeyword(TailTypeDecorator.withTail(LookupElementBuilder.create("break " + labeled.getName()).bold(), TailTypes.semicolonType()));
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
    PsiElement context =
      PsiTreeUtil.skipParentsOfType(myPosition.getParent(), PsiErrorElement.class, PsiJavaCodeReferenceElement.class, PsiTypeElement.class);
    PsiElement prevElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(myPosition.getParent());

    if (context instanceof PsiField && context.getParent() instanceof PsiImplicitClass) {
      addKeyword(new OverridableSpace(createKeyword(JavaKeywords.MODULE), TailTypes.humbleSpaceBeforeWordType()));
      if (prevElement == null) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.IMPORT), TailTypes.humbleSpaceBeforeWordType()));
      }
    }

    if (context instanceof PsiJavaFile && !(prevElement instanceof PsiJavaModule) || context instanceof PsiImportList) {
      if (myPrevLeaf == null || PsiUtil.isJavaToken(myPrevLeaf, JavaTokenType.SEMICOLON)) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.IMPORT), TailTypes.humbleSpaceBeforeWordType()));
      }
      addKeyword(new OverridableSpace(createKeyword(JavaKeywords.MODULE), TailTypes.humbleSpaceBeforeWordType()));
      if (myPrevLeaf == null || !myPrevLeaf.textMatches(JavaKeywords.OPEN)) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.OPEN), TailTypes.humbleSpaceBeforeWordType()));
      }
    }
    else if (context instanceof PsiJavaModule) {
      if (prevElement instanceof PsiPackageAccessibilityStatement && !myPrevLeaf.textMatches(";")) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.TO), TailTypes.humbleSpaceBeforeWordType()));
      }
      else if (!PsiUtil.isJavaToken(prevElement, JavaTokenType.MODULE_KEYWORD)) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.REQUIRES), TailTypes.humbleSpaceBeforeWordType()));
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.EXPORTS), TailTypes.humbleSpaceBeforeWordType()));
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.OPENS), TailTypes.humbleSpaceBeforeWordType()));
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.USES), TailTypes.humbleSpaceBeforeWordType()));
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.PROVIDES), TailTypes.humbleSpaceBeforeWordType()));
      }
    }
    else if (context instanceof PsiRequiresStatement) {
      if (!myPrevLeaf.textMatches(JavaKeywords.TRANSITIVE)) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.TRANSITIVE), TailTypes.humbleSpaceBeforeWordType()));
      }
      if (!myPrevLeaf.textMatches(JavaKeywords.STATIC)) {
        addKeyword(new OverridableSpace(createKeyword(JavaKeywords.STATIC), TailTypes.humbleSpaceBeforeWordType()));
      }
    }
    else if (context instanceof PsiProvidesStatement && prevElement instanceof PsiJavaCodeReferenceElement) {
      addKeyword(new OverridableSpace(createKeyword(JavaKeywords.WITH), TailTypes.humbleSpaceBeforeWordType()));
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
      return context.shouldAddCompletionChar() ? TailTypes.noneType() : myTail;
    }
  }
}
