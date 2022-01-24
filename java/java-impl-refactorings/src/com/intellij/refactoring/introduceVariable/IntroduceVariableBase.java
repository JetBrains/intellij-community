// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.*;
import com.intellij.refactoring.chainCall.ChainCallExtractor;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser.ReplaceChoice;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.NotInSuperCallOccurrenceFilter;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author dsl
 */
public abstract class IntroduceVariableBase extends IntroduceHandlerBase {
  public static class JavaReplaceChoice implements OccurrencesChooser.BaseReplaceChoice {
    public static final JavaReplaceChoice NO = new JavaReplaceChoice(ReplaceChoice.NO, null, false);
    public static final JavaReplaceChoice NO_WRITE = new JavaReplaceChoice(ReplaceChoice.NO_WRITE, null, false);
    public static final JavaReplaceChoice ALL = new JavaReplaceChoice(ReplaceChoice.ALL, null, false);

    private final @Nls String myDescription;
    private final boolean myChain;
    private final ReplaceChoice myChoice;

    JavaReplaceChoice(@NotNull ReplaceChoice choice, @Nullable @Nls String description, boolean chain) {
      myChoice = choice;
      myDescription = description;
      myChain = chain;
    }

    @Override
    public boolean isAll() {
      return myChoice.isAll();
    }

    public boolean isChain() {
      return myChain;
    }

    public PsiExpression[] filter(ExpressionOccurrenceManager manager) {
      switch (myChoice) {
        case NO:
          return new PsiExpression[]{manager.getMainOccurence()};
        case NO_WRITE:
          return StreamEx.of(manager.getOccurrences()).filter(expr -> !PsiUtil.isAccessedForWriting(expr)).toArray(PsiExpression.EMPTY_ARRAY);
        case ALL:
          return manager.getOccurrences();
        default:
          throw new IllegalStateException("Unexpected value: " + myChoice);
      }
    }

    @Override
    public String formatDescription(int occurrencesCount) {
      return myDescription == null ? myChoice.formatDescription(occurrencesCount) : myDescription;
    }

    @Override
    public String toString() {
      // For debug/test purposes
      return formatDescription(0);
    }

    @NotNull
    private static IntroduceVariableBase.JavaReplaceChoice allOccurrencesInside(PsiElement parent,
                                                                                int sameKeywordCount,
                                                                                String finalKeyword) {
      return new JavaReplaceChoice(ReplaceChoice.ALL, null, false) {
        @Override
        public PsiExpression[] filter(ExpressionOccurrenceManager manager) {
          return StreamEx.of(manager.getOccurrences())
            .filter(expr -> PsiTreeUtil.isAncestor(parent, getPhysicalElement(expr), true))
            .toArray(PsiExpression.EMPTY_ARRAY);
        }

        @Override
        public String formatDescription(int occurrencesCount) {
          return JavaRefactoringBundle.message("replace.occurrences.inside.statement", occurrencesCount, finalKeyword, sameKeywordCount);
        }
      };
    }
  }

  @NonNls private static final String REFACTORING_ID = "refactoring.extractVariable";

  private JavaVariableInplaceIntroducer myInplaceIntroducer;

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();
      Pair<TextRange, List<PsiExpression>> rangeAndExpressions = getExpressionsAndSelectionRange(project, editor, file, offset);
      TextRange suggestedSelection = rangeAndExpressions.getFirst();
      if (suggestedSelection != null) {
        selectionModel.setSelection(suggestedSelection.getStartOffset(), suggestedSelection.getEndOffset());
      }
      else {
        final PsiElement[] statementsInRange = IntroduceVariableUtil.findStatementsAtOffset(editor, file, offset);
        List<PsiExpression> expressions = rangeAndExpressions.getSecond();
        IntroduceTargetChooser.showChooser(editor, expressions,
                                           new Pass<>() {
                                             @Override
                                             public void pass(final PsiExpression selectedValue) {
                                               invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(),
                                                      selectedValue.getTextRange().getEndOffset());
                                             }
                                           },
                                           new PsiExpressionTrimRenderer.RenderFunction(),
                                           RefactoringBundle.message("introduce.target.chooser.expressions.title"),
                                           IntroduceVariableUtil.preferredSelection(statementsInRange, expressions), ScopeHighlighter.NATURAL_RANGER);
        return;
      }
    }
    if (invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()) &&
        LookupManager.getActiveLookup(editor) == null) {
      selectionModel.removeSelection();
    }
  }

  @NotNull
  public static Pair<TextRange, List<PsiExpression>> getExpressionsAndSelectionRange(@NotNull final Project project,
                                                                                     final Editor editor,
                                                                                     final PsiFile file,
                                                                                     int offset) {
    final PsiElement[] statementsInRange = IntroduceVariableUtil.findStatementsAtOffset(editor, file, offset);

    Document document = editor.getDocument();
    int line = document.getLineNumber(offset);
    TextRange lineRange =
      TextRange.create(document.getLineStartOffset(line), Math.min(document.getLineEndOffset(line) + 1, document.getTextLength()));

    //try line selection
    if (statementsInRange.length == 1 && IntroduceVariableUtil.selectLineAtCaret(offset, statementsInRange)) {
      final PsiExpression expressionInRange =
        findExpressionInRange(project, file, lineRange.getStartOffset(), lineRange.getEndOffset());
      if (expressionInRange != null && IntroduceVariableUtil.getErrorMessage(expressionInRange) == null) {
        return Pair.create(lineRange, Collections.singletonList(expressionInRange));
      }
    }

    final List<PsiExpression> expressions = ContainerUtil
      .filter(CommonJavaRefactoringUtil.collectExpressions(file, editor, offset), expression ->
        CommonJavaRefactoringUtil.getParentStatement(expression, false) != null ||
        PsiTreeUtil.getParentOfType(expression, PsiField.class, true, PsiStatement.class) != null);
    if (expressions.isEmpty()) {
      return Pair.create(lineRange, Collections.emptyList());
    }
    else if (!IntroduceVariableUtil.isChooserNeeded(expressions)) {
      return Pair.create(expressions.get(0).getTextRange(), expressions);
    }
    else {
      return Pair.create(null, expressions);
    }
  }

  /**
   * @deprecated use CommonJavaRefactoringUtil.collectExpressions
   */
  @Deprecated
  public static List<PsiExpression> collectExpressions(final PsiFile file,
                                                       final Editor editor,
                                                       final int offset) {
    return CommonJavaRefactoringUtil.collectExpressions(file, editor, offset);
  }

  private boolean invoke(final Project project, final Editor editor, PsiFile file, int startOffset, int endOffset) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.REFACTORING_INTRODUCE_VARIABLE);
    PsiDocumentManager.getInstance(project).commitAllDocuments();


    return invokeImpl(project, findExpressionInRange(project, file, startOffset, endOffset), editor);
  }

  private static PsiExpression findExpressionInRange(Project project, PsiFile file, int startOffset, int endOffset) {
    PsiExpression tempExpr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (tempExpr == null) {
      PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
      if (statements.length == 1) {
        if (statements[0] instanceof PsiExpressionStatement) {
          tempExpr = ((PsiExpressionStatement) statements[0]).getExpression();
        }
        else if (statements[0] instanceof PsiReturnStatement) {
          tempExpr = ((PsiReturnStatement)statements[0]).getReturnValue();
        }
        else if (statements[0] instanceof PsiSwitchStatement) {
          PsiExpression expr = JavaPsiFacade.getElementFactory(project).createExpressionFromText(statements[0].getText(), statements[0]);
          TextRange range = statements[0].getTextRange();
          final RangeMarker rangeMarker = FileDocumentManager.getInstance().getDocument(file.getVirtualFile()).createRangeMarker(range);
          expr.putUserData(ElementToWorkOn.TEXT_RANGE, rangeMarker);
          expr.putUserData(ElementToWorkOn.PARENT, statements[0]);
          return expr;
        }
      }
    }

    if (tempExpr == null) {
      tempExpr = IntroduceVariableUtil.getSelectedExpression(project, file, startOffset, endOffset);
    }
    return CommonJavaRefactoringUtil.isExtractable(tempExpr) ? tempExpr : null;
  }

  @NotNull
  public Pair<List<PsiElement>, List<PsiExpression>> getPossibleAnchorsAndOccurrences(final Project project, final PsiExpression expr) {
    OccurrencesInfo occurrencesInfo = buildOccurrencesInfo(project, expr);

    final LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap = occurrencesInfo.buildOccurrencesMap(expr);
    List<PsiElement> anchors = occurrencesMap.values().stream()
      .map(o -> getAnchor(o.toArray(PsiExpression.EMPTY_ARRAY)))
      .filter(Objects::nonNull)
      .flatMap(anchor -> IntroduceVariableTargetBlockChooser.getContainers(anchor, expr).stream())
      .distinct()
      .collect(Collectors.toList());
    return Pair.create(anchors, occurrencesInfo.myOccurrences);
  }

  @NotNull
  public Map<String, JavaReplaceChoice> getPossibleReplaceChoices(final Project project, final PsiExpression expr) {
    OccurrencesInfo occurrencesInfo = buildOccurrencesInfo(project, expr);
    final LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap = occurrencesInfo.buildOccurrencesMap(expr);
    return occurrencesMap.entrySet().stream().collect(Collectors.toMap(
      entry -> entry.getKey().formatDescription(entry.getValue().size()),
      entry -> entry.getKey()
    ));
  }

  public void invoke(final Project project,
                     final PsiExpression expr,
                     final PsiElement target,
                     final JavaReplaceChoice replaceChoice,
                     final Editor editor) {
    OccurrencesInfo info = buildOccurrencesInfo(project, expr);
    LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap = info.buildOccurrencesMap(expr);
  }

  @NotNull
  private OccurrencesInfo buildOccurrencesInfo(Project project, PsiExpression expr) {
    final PsiElement anchorStatement = getAnchor(expr);
    PsiElement tempContainer = checkAnchorStatement(project, null, anchorStatement);

    final ExpressionOccurrenceManager occurrenceManager = createOccurrenceManager(expr, tempContainer);
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();

    return new OccurrencesInfo(occurrences);
  }

  @Nullable
  private static JavaReplaceChoice findChoice(@NotNull LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap,
                                              @NotNull JavaReplaceChoice replaceChoice) {
    return ContainerUtil.find(occurrencesMap.entrySet(), entry -> {
      return entry.getKey().formatDescription(0) == replaceChoice.formatDescription(0);
    }).getKey();
  }


  @Override
  protected boolean invokeImpl(final Project project, final PsiExpression expr, final Editor editor) {
    return invokeImpl(project, expr, null, null, editor);
  }

  public boolean invokeImpl(final Project project,
                            final PsiExpression expr,
                            @Nullable PsiElement targetContainer,
                            @Nullable JavaReplaceChoice replaceChoice,
                            final Editor editor) {
    if (expr != null) {
      final String errorMessage = IntroduceVariableUtil.getErrorMessage(expr);
      if (errorMessage != null) {
        showErrorMessage(project, editor, RefactoringBundle.getCannotRefactorMessage(errorMessage));
        return false;
      }
    }

    if (expr != null && expr.getParent() instanceof PsiExpressionStatement) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.introduceVariable.incompleteStatement");
    }
    if (IntroduceVariableUtil.LOG.isDebugEnabled()) {
      IntroduceVariableUtil.LOG.debug("expression:" + expr);
    }

    if (expr == null || !expr.isPhysical()) {
      if (ReassignVariableUtil.reassign(editor)) return false;
      if (expr == null) {
        String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("selected.block.should.represent.an.expression"));
        showErrorMessage(project, editor, message);
        return false;
      }
    }

    String enumInSwitchError = RefactoringUtil.checkEnumConstantInSwitchLabel(expr);
    if (enumInSwitchError != null) {
      showErrorMessage(project, editor, enumInSwitchError);
      return false;
    }


    final PsiType originalType = CommonJavaRefactoringUtil.getTypeByExpressionWithExpectedType(expr);
    if (originalType == null || LambdaUtil.notInferredType(originalType)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("unknown.expression.type"));
      showErrorMessage(project, editor, message);
      return false;
    }

    if (PsiType.VOID.equals(originalType)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(project, editor, message);
      return false;
    }

    try {
      JavaPsiFacade.getElementFactory(project).createTypeElementFromText(
        GenericsUtil.getVariableTypeByExpressionType(originalType).getCanonicalText(), expr);
    }
    catch (IncorrectOperationException ignore) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("unknown.expression.type"));
      showErrorMessage(project, editor, message);
      return false;
    }

    for (PsiPatternVariable variable : JavaPsiPatternUtil.getExposedPatternVariables(expr)) {
      if (VariableAccessUtils.getVariableReferences(variable, variable.getDeclarationScope()).stream()
        .anyMatch(ref -> !PsiTreeUtil.isAncestor(expr, ref, true))) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          JavaRefactoringBundle.message("selected.expression.introduces.pattern.variable", variable.getName()));
        showErrorMessage(project, editor, message);
        return false;
      }
    }

    final PsiElement anchorStatement = getAnchor(expr);

    PsiElement tempContainer = checkAnchorStatement(project, editor, anchorStatement);
    if (tempContainer == null) return false;

    final PsiFile file = anchorStatement.getContainingFile();
    IntroduceVariableUtil.LOG.assertTrue(file != null, "expr.getContainingFile() == null");
    final PsiElement nameSuggestionContext = editor == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
    final RefactoringSupportProvider supportProvider = LanguageRefactoringSupport.INSTANCE.forContext(expr);
    final boolean isInplaceAvailableOnDataContext =
      supportProvider != null &&
      editor.getSettings().isVariableInplaceRenameEnabled() &&
      supportProvider.isInplaceIntroduceAvailable(expr, nameSuggestionContext) &&
      (!ApplicationManager.getApplication().isUnitTestMode() || isInplaceAvailableInTestMode()) &&
      !isInJspHolderMethod(expr);

    if (isInplaceAvailableOnDataContext) {
      final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
      checkInLoopCondition(expr, conflicts);
      if (!conflicts.isEmpty()) {
        showErrorMessage(project, editor, StringUtil.join(conflicts.values(), "<br>"));
        return false;
      }
    }

    final ExpressionOccurrenceManager occurrenceManager = createOccurrenceManager(expr, tempContainer);
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();

    OccurrencesInfo occurrencesInfo = new OccurrencesInfo(occurrences);

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return false;

    final LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap = occurrencesInfo.buildOccurrencesMap(expr);

    class IntroduceVariablePass extends Pass<JavaReplaceChoice> {
      boolean wasSucceed = true;

      @Override
      public void pass(final JavaReplaceChoice choice) {
        Consumer<JavaReplaceChoice> dialogIntroduce = c -> CommandProcessor.getInstance().executeCommand(project, () -> introduce(c), getRefactoringName(), null);
        if (choice == null) {
          dialogIntroduce.accept(null);
        }
        else {
          SlowOperations.allowSlowOperations(
            () -> inplaceIntroduce(project, editor, choice, targetContainer, occurrenceManager, originalType, dialogIntroduce));
        }
      }

      private void introduce(@Nullable JavaReplaceChoice choice) {
        if (!anchorStatement.isValid()) {
          return;
        }
        final Editor topLevelEditor;
        if (!InjectedLanguageManager.getInstance(project).isInjectedFragment(anchorStatement.getContainingFile())) {
          topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(editor);
        }
        else {
          topLevelEditor = editor;
        }

        PsiVariable variable = null;
        try {
          boolean hasWriteAccess = occurrencesInfo.myHasWriteAccess;
          final InputValidator validator = new InputValidator(IntroduceVariableBase.this, project, occurrenceManager);

          final TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(project, originalType, expr, occurrences);
          boolean inFinalContext = occurrenceManager.isInFinalContext();
          final IntroduceVariableSettings settings =
            getSettings(project, topLevelEditor, expr, occurrences, typeSelectorManager, inFinalContext, hasWriteAccess, validator,
                        anchorStatement, choice);
          if (!settings.isOK()) {
            wasSucceed = false;
            return;
          }
          JavaReplaceChoice finalChoice = settings.getReplaceChoice();
          PsiExpression[] selectedOccurrences = finalChoice.filter(occurrenceManager);
          if (selectedOccurrences.length == 0) {
            showErrorMessage(project, editor, JavaRefactoringBundle.message("introduce.variable.no.matching.occurrences"));
            wasSucceed = false;
            return;
          }
          final PsiElement chosenAnchor = getAnchor(selectedOccurrences);
          if (chosenAnchor == null) {
            String text = file.getText();
            String textWithOccurrences = StreamEx.of(selectedOccurrences)
              .map(e -> getPhysicalElement(e).getTextRange())
              .flatMapToEntry(range -> EntryStream.of(range.getStartOffset(), "[", range.getEndOffset(), "]").toMap())
              .sortedBy(Map.Entry::getKey)
              .prepend(0, "")
              .append(text.length(), "")
              .map(Function.identity())
              .pairMap((prev, next) -> text.substring(prev.getKey(), next.getKey()) + next.getValue())
              .joining();
            IntroduceVariableUtil.LOG.error("Unable to find anchor for a new variable; selectedOccurrences.length = " + selectedOccurrences.length,
                                            new Attachment("source.java", textWithOccurrences));
            return;
          }

          final RefactoringEventData beforeData = new RefactoringEventData();
          beforeData.addElement(expr);
          project.getMessageBus()
            .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(REFACTORING_ID, beforeData);

          variable = VariableExtractor.introduce(project, expr, topLevelEditor, chosenAnchor, selectedOccurrences, settings);
        }
        finally {
          final RefactoringEventData afterData = new RefactoringEventData();
          afterData.addElement(variable);
          project.getMessageBus()
            .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(REFACTORING_ID, afterData);
        }
      }
    }
    final IntroduceVariablePass callback = new IntroduceVariablePass();

    if (replaceChoice != null) {
      callback.pass(findChoice(occurrencesMap, replaceChoice));
    }
    else if (!isInplaceAvailableOnDataContext) {
      callback.pass(null);
    }
    else {
      String title = occurrencesInfo.myChainMethodName != null && occurrences.length == 1
                     ? JavaRefactoringBundle.message("replace.lambda.chain.detected")
                     : RefactoringBundle.message("replace.multiple.occurrences.found");
      createOccurrencesChooser(editor).showChooser(callback, occurrencesMap, title);
    }
    return callback.wasSucceed;
  }

  @NotNull
  public static OccurrencesChooser<PsiExpression> createOccurrencesChooser(Editor editor) {
    return new OccurrencesChooser<>(editor) {
      @Override
      protected TextRange getOccurrenceRange(PsiExpression occurrence) {
        RangeMarker rangeMarker = occurrence.getUserData(ElementToWorkOn.TEXT_RANGE);
        if (rangeMarker != null) {
          return new TextRange(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
        }
        return occurrence.getTextRange();
      }
    };
  }

  private void inplaceIntroduce(@NotNull Project project,
                                Editor editor,
                                @NotNull JavaReplaceChoice choice,
                                @Nullable PsiElement targetContainer,
                                @NotNull ExpressionOccurrenceManager occurrenceManager,
                                @NotNull PsiType originalType,
                                @NotNull Consumer<JavaReplaceChoice> dialogIntroduce) {
    boolean inFinalContext = occurrenceManager.isInFinalContext();
    PsiExpression expr = occurrenceManager.getMainOccurence();
    PsiExpression[] selectedOccurrences = choice.filter(occurrenceManager);
    final InputValidator validator = new InputValidator(this, project, occurrenceManager);
    final TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(project, originalType, expr, selectedOccurrences);
    typeSelectorManager.setAllOccurrences(true);

    boolean hasWriteAccess = ContainerUtil.exists(selectedOccurrences, occ -> PsiUtil.isAccessedForWriting(occ));
    final PsiElement chosenAnchor = getAnchor(selectedOccurrences);
    final IntroduceVariableSettings settings =
      getSettings(project, editor, expr, selectedOccurrences, typeSelectorManager, inFinalContext,
                  hasWriteAccess, validator, chosenAnchor, choice);

    if (choice.isChain()) {
      myInplaceIntroducer = new ChainCallInplaceIntroducer(project,
                                                           settings,
                                                           chosenAnchor,
                                                           editor, expr,
                                                           selectedOccurrences,
                                                           typeSelectorManager,
                                                           getRefactoringName());
      if (!myInplaceIntroducer.startInplaceIntroduceTemplate()) {
        dialogIntroduce.accept(choice);
      }
    }
    else {
      final boolean cantChangeFinalModifier = hasWriteAccess ||
                                              inFinalContext && choice.isAll() ||
                                              chosenAnchor instanceof PsiSwitchLabelStatementBase;
      Pass<PsiElement> callback = new Pass<>() {
        @Override
        public void pass(final PsiElement container) {
          PsiElement anchor = container instanceof PsiLambdaExpression ? getAnchor(container) : container;
          if (checkAnchorStatement(project, editor, anchor) == null) {
            return;
          }
          myInplaceIntroducer = new JavaVariableInplaceIntroducer(project, settings, anchor, editor, expr,
                                                                  cantChangeFinalModifier, selectedOccurrences, typeSelectorManager,
                                                                  getRefactoringName());
          if (!myInplaceIntroducer.startInplaceIntroduceTemplate()) {
            dialogIntroduce.accept(choice);
          }
        }
      };
      if (targetContainer != null) {
        callback.pass(targetContainer);
      }
      else {
        IntroduceVariableTargetBlockChooser.chooseTargetAndPerform(editor, chosenAnchor, expr, callback);
      }
    }
  }

  public static boolean canBeExtractedWithoutExplicitType(PsiExpression expr) {
    if (PsiUtil.isLanguageLevel10OrHigher(expr)) {
      PsiType type = getNormalizedType(expr);
      if (type != null && !PsiType.NULL.equals(type) && PsiTypesUtil.isDenotableType(type, expr)) {
        PsiExpression copy =
          (PsiExpression)(type instanceof PsiDisjunctionType ? expr.copy() : LambdaUtil.copyWithExpectedType(expr, type));
        if (type.equals(getNormalizedType(copy))) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private static PsiType getNormalizedType(PsiExpression expr) {
    PsiType type = expr.getType();
    PsiClass refClass = PsiUtil.resolveClassInType(type);
    if (refClass instanceof PsiAnonymousClass) {
      return ((PsiAnonymousClass)refClass).getBaseClassType();
    }
    return type;
  }

  @Nullable
  public static PsiElement getAnchor(PsiElement place) {
    place = getPhysicalElement(place);
    PsiElement anchorStatement = CommonJavaRefactoringUtil.getParentStatement(place, false);
    if (anchorStatement == null) {
      PsiField field = PsiTreeUtil.getParentOfType(place, PsiField.class, true, PsiStatement.class);
      if (field != null && !(field instanceof PsiEnumConstant)) {
        PsiExpression initializer = field.getInitializer();
        // Could be also an annotation argument
        if (PsiTreeUtil.isAncestor(initializer, place, false)) {
          anchorStatement = initializer;
        }
      }
    }
    return anchorStatement;
  }

  static @Nullable PsiElement getAnchor(PsiExpression[] places) {
    if (places.length == 1) {
      return getAnchor(places[0]);
    }
    PsiElement anchor = CommonJavaRefactoringUtil.getAnchorElementForMultipleExpressions(places, null);
    return anchor instanceof PsiField && !(anchor instanceof PsiEnumConstant) ? ((PsiField)anchor).getInitializer() : anchor;
  }

  private static @NotNull PsiElement getPhysicalElement(PsiElement place) {
    PsiElement physicalElement = place.getUserData(ElementToWorkOn.PARENT);
    return physicalElement != null ? physicalElement : place;
  }

  @Contract("_, _, null -> null")
  protected PsiElement checkAnchorStatement(Project project, Editor editor, PsiElement anchorStatement) {
    if (anchorStatement == null) {
      String message = JavaRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", getRefactoringName());
      showErrorMessage(project, editor, message);
      return null;
    }
    if (checkAnchorBeforeThisOrSuper(project, editor, anchorStatement, getRefactoringName(), HelpID.INTRODUCE_VARIABLE)) return null;

    final PsiElement tempContainer = anchorStatement.getParent();

    if (!(tempContainer instanceof PsiCodeBlock) && !CommonJavaRefactoringUtil.isLoopOrIf(tempContainer) && !(tempContainer instanceof PsiLambdaExpression) && (tempContainer.getParent() instanceof PsiLambdaExpression)) {
      String message = JavaRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", getRefactoringName());
      showErrorMessage(project, editor, message);
      return null;
    }
    return tempContainer;
  }

  protected boolean isInplaceAvailableInTestMode() {
    return false;
  }

  private static ExpressionOccurrenceManager createOccurrenceManager(PsiExpression expr, PsiElement tempContainer) {
    Set<PsiVariable> vars = new HashSet<>();
    SyntaxTraverser.psiTraverser().withRoot(expr)
      .filter(element -> element instanceof PsiReferenceExpression)
      .forEach(element -> {
        final PsiElement resolve = ((PsiReferenceExpression)element).resolve();
        if (resolve instanceof PsiVariable) {
          vars.add((PsiVariable)resolve);
        }
      });

    PsiElement containerParent = tempContainer;
    PsiElement lastScope = tempContainer;
    while (true) {
      if (containerParent instanceof PsiFile) break;
      if (containerParent instanceof PsiMethod) {
        PsiClass containingClass = ((PsiMethod)containerParent).getContainingClass();
        if (containingClass == null || !PsiUtil.isLocalOrAnonymousClass(containingClass)) break;
        if (vars.stream().anyMatch(variable -> PsiTreeUtil.isAncestor(containingClass, variable, true))) {
          break;
        }
      }
      if (containerParent instanceof PsiLambdaExpression) {
        PsiParameter[] parameters = ((PsiLambdaExpression)containerParent).getParameterList().getParameters();
        if (ContainerUtil.exists(parameters, vars::contains)) {
          break;
        }
      }
      if (containerParent instanceof PsiForStatement) {
        PsiForStatement forStatement = (PsiForStatement)containerParent;
        if (vars.stream().anyMatch(variable -> PsiTreeUtil.isAncestor(forStatement.getInitialization(), variable, true))) {
          break;
        }
      }
      containerParent = containerParent.getParent();
      if (containerParent instanceof PsiCodeBlock) {
        lastScope = containerParent;
      }
    }
    return new ExpressionOccurrenceManager(expr, lastScope, NotInSuperCallOccurrenceFilter.INSTANCE);
  }

  private static boolean isInJspHolderMethod(PsiExpression expr) {
    final PsiElement parent1 = expr.getParent();
    if (parent1 == null) {
      return false;
    }
    final PsiElement parent2 = parent1.getParent();
    if (!(parent2 instanceof JspCodeBlock)) return false;
    final PsiElement parent3 = parent2.getParent();
    return parent3 instanceof JspHolderMethod;
  }

  static boolean isFinalVariableOnLHS(PsiExpression expr) {
    if (expr instanceof PsiReferenceExpression && RefactoringUtil.isAssignmentLHS(expr)) {
      final PsiElement resolve = ((PsiReferenceExpression)expr).resolve();
      if (resolve instanceof PsiVariable &&
          ((PsiVariable)resolve).hasModifierProperty(PsiModifier.FINAL)) { //should be inserted after assignment
        return true;
      }
    }
    return false;
  }

  public static PsiExpression simplifyVariableInitializer(final PsiExpression initializer,
                                                        final PsiType expectedType) {
    return simplifyVariableInitializer(initializer, expectedType, true);
  }

  public static PsiExpression simplifyVariableInitializer(final PsiExpression initializer,
                                                          final PsiType expectedType,
                                                          final boolean inDeclaration) {

    if (initializer instanceof PsiTypeCastExpression) {
      PsiExpression operand = ((PsiTypeCastExpression)initializer).getOperand();
      if (operand != null) {
        PsiType operandType = operand.getType();
        if (operandType != null && TypeConversionUtil.isAssignable(expectedType, operandType)) {
          return operand;
        }
      }
    }
    else if (initializer instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)initializer;
      if (newExpression.getArrayInitializer() != null) {
        if (inDeclaration) {
          return newExpression.getArrayInitializer();
        }
      }
      else {
        PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
        if (ref != null) {
          final PsiExpression tryToDetectDiamondNewExpr = ((PsiVariable)JavaPsiFacade.getElementFactory(initializer.getProject())
            .createVariableDeclarationStatement("x", expectedType, initializer, initializer).getDeclaredElements()[0])
            .getInitializer();
          if (tryToDetectDiamondNewExpr instanceof PsiNewExpression &&
              PsiDiamondTypeUtil.canCollapseToDiamond((PsiNewExpression)tryToDetectDiamondNewExpr,
                                                      (PsiNewExpression)tryToDetectDiamondNewExpr,
                                                      expectedType)) {
            final PsiElement paramList = RemoveRedundantTypeArgumentsUtil.replaceExplicitWithDiamond(ref.getParameterList());
            return PsiTreeUtil.getParentOfType(paramList, PsiNewExpression.class);
          }
        }
      }
    }
    return initializer;
  }

  @Override
  protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    throw new UnsupportedOperationException();
  }

  protected static void highlightReplacedOccurrences(Project project, Editor editor, PsiElement[] replacedOccurrences){
    if (editor == null) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    highlightManager.addOccurrenceHighlights(editor, replacedOccurrences, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
    WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
  }

  protected abstract void showErrorMessage(Project project, Editor editor, String message);


  protected boolean reportConflicts(MultiMap<PsiElement,String> conflicts, Project project, IntroduceVariableSettings settings){
    return false;
  }

  public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                               PsiExpression expr, PsiExpression[] occurrences,
                                               final TypeSelectorManagerImpl typeSelectorManager,
                                               boolean declareFinalIfAll,
                                               boolean anyAssignmentLHS,
                                               final InputValidator validator,
                                               PsiElement anchor,
                                               final JavaReplaceChoice replaceChoice) {
    final boolean replaceAll = replaceChoice.isAll();
    final SuggestedNameInfo suggestedName = CommonJavaRefactoringUtil.getSuggestedName(typeSelectorManager.getDefaultType(), expr, anchor);
    final String variableName = suggestedName.names.length > 0 ? suggestedName.names[0] : "v";
    final boolean declareFinal = replaceAll && declareFinalIfAll || !anyAssignmentLHS && createFinals(anchor.getContainingFile()) ||
                                 anchor instanceof PsiSwitchLabelStatementBase;
    final boolean declareVarType = canBeExtractedWithoutExplicitType(expr) && createVarType() && !replaceChoice.isChain();
    final boolean replaceWrite = anyAssignmentLHS && replaceAll;
    return new IntroduceVariableSettings() {
      @Override
      public @NlsSafe String getEnteredName() {
        return variableName;
      }

      @Override
      public boolean isReplaceAllOccurrences() {
        return replaceAll;
      }

      @Override
      public boolean isDeclareFinal() {
        return declareFinal;
      }

      @Override
      public boolean isDeclareVarType() {
        return declareVarType;
      }

      @Override
      public boolean isReplaceLValues() {
        return replaceWrite;
      }

      @Override
      public PsiType getSelectedType() {
        final PsiType selectedType = typeSelectorManager.getTypeSelector().getSelectedType();
        return selectedType != null ? selectedType : typeSelectorManager.getDefaultType();
      }

      @Override
      public JavaReplaceChoice getReplaceChoice() {
        return replaceChoice;
      }

      @Override
      public boolean isOK() {
        return true;
      }
    };
  }

  public static boolean createFinals(@NotNull PsiFile file) {
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS;
    return createFinals == null ?
           JavaCodeStyleSettings.getInstance(file).GENERATE_FINAL_LOCALS :
           createFinals.booleanValue();
  }

  public static boolean createVarType() {
    final Boolean createVarType = JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE;
    return createVarType != null && createVarType.booleanValue();
  }

  public static boolean checkAnchorBeforeThisOrSuper(final Project project,
                                                     final Editor editor,
                                                     final PsiElement tempAnchorElement,
                                                     final @NlsContexts.DialogTitle String refactoringName,
                                                     final String helpID) {
    if (tempAnchorElement instanceof PsiExpressionStatement) {
      PsiExpression enclosingExpr = ((PsiExpressionStatement)tempAnchorElement).getExpression();
      if (enclosingExpr instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression)enclosingExpr).resolveMethod();
        if (method != null && method.isConstructor()) {
          //This is either 'this' or 'super', both must be the first in the respective constructor
          String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("invalid.expression.context"));
          CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpID);
          return true;
        }
      }
    }
    return false;
  }

  public interface Validator {
    boolean isOK(IntroduceVariableSettings dialog);
  }

  public static void checkInLoopCondition(PsiExpression occurence, MultiMap<PsiElement, String> conflicts) {
    final PsiElement loopForLoopCondition = RefactoringUtil.getLoopForLoopCondition(occurence);
    if (loopForLoopCondition == null || loopForLoopCondition instanceof PsiWhileStatement) return;
    final List<PsiVariable> referencedVariables = RefactoringUtil.collectReferencedVariables(occurence);
    final List<PsiVariable> modifiedInBody = new ArrayList<>();
    for (PsiVariable psiVariable : referencedVariables) {
      if (RefactoringUtil.isModifiedInScope(psiVariable, loopForLoopCondition)) {
        modifiedInBody.add(psiVariable);
      }
    }

    if (!modifiedInBody.isEmpty()) {
      for (PsiVariable variable : modifiedInBody) {
        final String message = JavaRefactoringBundle.message("is.modified.in.loop.body", RefactoringUIUtil.getDescription(variable, false));
        conflicts.putValue(variable, StringUtil.capitalize(message));
      }
      conflicts.putValue(occurence, JavaRefactoringBundle.message("introducing.variable.may.break.code.logic"));
    }
  }

  @Override
  public AbstractInplaceIntroducer getInplaceIntroducer() {
    return myInplaceIntroducer;
  }

  public static class OccurrencesInfo {
    List<PsiExpression> myOccurrences;
    List<PsiExpression> myNonWrite;
    boolean myCantReplaceAll;
    boolean myCantReplaceAllButWrite;
    boolean myHasWriteAccess;
    final String myChainMethodName;

    public OccurrencesInfo(PsiExpression[] occurrences) {
      this(occurrences, true);
    }

    public OccurrencesInfo(PsiExpression[] occurrences, boolean chainCallPossible) {
      myOccurrences = Arrays.asList(occurrences);
      myNonWrite = new ArrayList<>();
      myCantReplaceAll = false;
      myCantReplaceAllButWrite = false;
      for (PsiExpression occurrence : myOccurrences) {
        if (!RefactoringUtil.isAssignmentLHS(occurrence)) {
          myNonWrite.add(occurrence);
        } else if (isFinalVariableOnLHS(occurrence)) {
          myCantReplaceAll = true;
        } else if (!myNonWrite.isEmpty()){
          myCantReplaceAllButWrite = true;
          myCantReplaceAll = true;
        }
      }
      myHasWriteAccess = myOccurrences.size() > myNonWrite.size() && myOccurrences.size() > 1;
      myChainMethodName = chainCallPossible ? getChainCallExtractor() : null;
    }

    private String getChainCallExtractor() {
      if (myHasWriteAccess || myOccurrences.isEmpty()) return null;
      PsiExpression expression = myOccurrences.get(0);
      // The whole lambda body selected
      if (myOccurrences.size() == 1 && expression.getParent() instanceof PsiLambdaExpression) return null;
      PsiElement parent = PsiTreeUtil.findCommonParent(myOccurrences);
      if (parent == null) return null;
      PsiType type = expression.getType();
      PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, true, PsiStatement.class);
      ChainCallExtractor extractor = ChainCallExtractor.findExtractor(lambda, expression, type);
      if (extractor == null) return null;
      PsiParameter parameter = lambda.getParameterList().getParameters()[0];
      if (!ReferencesSearch.search(parameter).forEach((Processor<PsiReference>)ref ->
        myOccurrences.stream().anyMatch(expr -> PsiTreeUtil.isAncestor(expr, ref.getElement(), false)))) {
        return null;
      }
      return extractor.getMethodName(parameter, expression, type);
    }

    @NotNull
    public LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> buildOccurrencesMap(PsiExpression expr) {
      final LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap = new LinkedHashMap<>();
      if (myChainMethodName != null) {
        if (myOccurrences.size() > 1 && !myCantReplaceAll) {
          occurrencesMap.put(JavaReplaceChoice.NO, Collections.singletonList(expr));
          occurrencesMap.put(JavaReplaceChoice.ALL, myOccurrences);
          occurrencesMap.put(new JavaReplaceChoice(ReplaceChoice.ALL, null, true) {
              @Override
              public String formatDescription(int occurrencesCount) {
                return JavaRefactoringBundle.message("replace.all.and.extract", occurrencesCount, myChainMethodName);
              }
            }, myOccurrences);
        } else {
          JavaReplaceChoice noChain =
            new JavaReplaceChoice(ReplaceChoice.NO, JavaRefactoringBundle.message("replace.inside.current.lambda"), false);
          JavaReplaceChoice chain =
            new JavaReplaceChoice(ReplaceChoice.NO, JavaRefactoringBundle.message("replace.as.separate.operation", myChainMethodName), true);
          occurrencesMap.put(noChain, Collections.singletonList(expr));
          occurrencesMap.put(chain, Collections.singletonList(expr));
        }
      } else {
        occurrencesMap.put(JavaReplaceChoice.NO, Collections.singletonList(expr));
        boolean hasWrite = myHasWriteAccess && !myCantReplaceAllButWrite;
        if (hasWrite && myNonWrite.contains(expr)) {
          occurrencesMap.put(JavaReplaceChoice.NO_WRITE, myNonWrite);
        }

        if (myOccurrences.size() > 1 && !myCantReplaceAll) {
          if (hasWrite) {
            JavaReplaceChoice choice = new JavaReplaceChoice(
              ReplaceChoice.ALL,
              myNonWrite.isEmpty() ? JavaRefactoringBundle.message("replace.all.occurrences.changes.semantics", myOccurrences.size())
              : JavaRefactoringBundle.message("replace.all.read.and.write"), false);
            occurrencesMap.put(choice, myOccurrences);
          }
          else {
            generateScopeBasedChoices(expr, occurrencesMap);
            occurrencesMap.put(JavaReplaceChoice.ALL, myOccurrences);
          }
        }
      }
      return occurrencesMap;
    }

    private void generateScopeBasedChoices(PsiExpression expr,
                                           LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap) {
      // This comparator can correctly compare only elements that represent a single ancestor chain
      // i.e. for two compared elements a and b either a is ancestor of b or vice versa
      Comparator<PsiElement> treeOrder = (e1, e2) -> {
        if (PsiTreeUtil.isAncestor(e1, e2, true)) return 1;
        if (PsiTreeUtil.isAncestor(e2, e1, true)) return -1;
        return 0;
      };
      PsiElement physical = getPhysicalElement(expr);
      TreeMap<PsiElement, List<PsiExpression>> groupByBlock =
        StreamEx.of(myOccurrences)
          .map(place -> (PsiExpression)getPhysicalElement(place))
          .groupingBy(e -> PsiTreeUtil.findCommonParent(e, physical),
                      () -> new TreeMap<>(treeOrder), Collectors.toList());
      IntroduceVariableUtil.LOG.assertTrue(!groupByBlock.isEmpty());
      List<PsiExpression> currentOccurrences = new ArrayList<>();
      Map<String, Integer> counts = new HashMap<>();
      groupByBlock.forEach((parent, occurrences) -> {
        PsiElement nextParent = groupByBlock.higherKey(parent);
        if (nextParent == null) return;
        currentOccurrences.addAll(occurrences);
        if (currentOccurrences.size() == 1) return;
        PsiElement current = parent.getParent();
        @NonNls String keyword = null;
        while (current != nextParent) {
          if (current instanceof PsiIfStatement || current instanceof PsiWhileStatement || current instanceof PsiForStatement ||
              current instanceof PsiTryStatement) {
            keyword = current.getFirstChild().getText();
          }
          else if (current instanceof PsiDoWhileStatement) {
            keyword = "do-while";
          }
          else if (current instanceof PsiForeachStatement) {
            keyword = "for-each";
          }
          else if (current instanceof PsiLambdaExpression) {
            keyword = "lambda";
          }
          if (keyword != null) {
            break;
          }
          current = current.getParent();
        }
        if (keyword == null && nextParent instanceof PsiIfStatement) {
          PsiStatement thenBranch = ((PsiIfStatement)nextParent).getThenBranch();
          PsiStatement elseBranch = ((PsiIfStatement)nextParent).getElseBranch();
          if (PsiTreeUtil.isAncestor(thenBranch, parent, false)) {
            keyword = "if-then";
          } else if (PsiTreeUtil.isAncestor(elseBranch, parent, false)) {
            keyword = "else";
          }
        }
        if (keyword != null) {
          int sameKeywordCount = counts.merge(keyword, 1, Integer::sum);
          if (sameKeywordCount <= 2) {
            JavaReplaceChoice choice = JavaReplaceChoice.allOccurrencesInside(parent, sameKeywordCount, keyword);
            occurrencesMap.put(choice, new ArrayList<>(currentOccurrences));
          }
        }
      });
    }
  }

  protected static @NlsContexts.Command String getRefactoringName() {
    return RefactoringBundle.message("introduce.variable.title");
  }
}
