// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPatternVariable;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.chainCall.ChainCallExtractor;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser.ReplaceChoice;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase.ErrorOrContainer.Container;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.NotInConstructorCallFilter;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.intellij.refactoring.IntroduceVariableUtil.IntroduceVariableCandidates;
import static com.intellij.refactoring.IntroduceVariableUtil.LOG;
import static com.intellij.refactoring.IntroduceVariableUtil.findExpressionInRange;
import static com.intellij.refactoring.IntroduceVariableUtil.findStatementsAtOffset;
import static com.intellij.refactoring.IntroduceVariableUtil.getErrorMessage;
import static com.intellij.refactoring.IntroduceVariableUtil.getIntroduceVariableCandidates;
import static com.intellij.refactoring.IntroduceVariableUtil.preferredSelection;
import static com.intellij.refactoring.introduceVariable.IntroduceVariableBase.IntroduceVariableResult.Context;

@ApiStatus.Internal
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
      return switch (myChoice) {
        case NO -> new PsiExpression[]{manager.getMainOccurence()};
        case NO_WRITE ->
          StreamEx.of(manager.getOccurrences()).filter(expr -> !PsiUtil.isAccessedForWriting(expr)).toArray(PsiExpression.EMPTY_ARRAY);
        case ALL -> manager.getOccurrences();
      };
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

    private static @NotNull IntroduceVariableBase.JavaReplaceChoice allOccurrencesInside(PsiElement parent,
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

  private static final @NonNls String REFACTORING_ID = "refactoring.extractVariable";

  private JavaVariableInplaceIntroducer myInplaceIntroducer;

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();
      IntroduceVariableCandidates
        info = getIntroduceVariableCandidates(project, editor, file, offset);
      TextRange suggestedSelection = info.bestRangeToExtractFrom();
      if (suggestedSelection != null) {
        selectionModel.setSelection(suggestedSelection.getStartOffset(), suggestedSelection.getEndOffset());
      }
      else {
        final PsiElement[] statementsInRange = findStatementsAtOffset(editor, file, offset);
        List<PsiExpression> expressions = info.expressions();
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
                                           preferredSelection(statementsInRange, expressions), ScopeHighlighter.NATURAL_RANGER);
        return;
      }
    }
    if (invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()) &&
        LookupManager.getActiveLookup(editor) == null) {
      selectionModel.removeSelection();
    }
  }


  /**
   * @deprecated use {@link IntroduceVariableUtil#getIntroduceVariableCandidates(Project, Editor, PsiFile, int)} instead.
   */
  @Deprecated(forRemoval = true)
  public static @NotNull Pair<@Nullable TextRange, @NotNull List<PsiExpression>> getExpressionsAndSelectionRange(final @NotNull Project project,
                                                                                              final @NotNull Editor editor,
                                                                                              final @NotNull PsiFile file,
                                                                                              int offset) {
    IntroduceVariableCandidates
      info = getIntroduceVariableCandidates(project, editor, file, offset);
    return new Pair<>(info.bestRangeToExtractFrom(), info.expressions());
  }

  private boolean invoke(final Project project, final Editor editor, PsiFile file, int startOffset, int endOffset) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.REFACTORING_INTRODUCE_VARIABLE);
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    return invokeImpl(project, findExpressionInRange(project, file, startOffset, endOffset), editor);
  }

  public @NotNull Pair<List<PsiElement>, List<PsiExpression>> getPossibleAnchorsAndOccurrences(final Project project, final PsiExpression expr) {
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

  public @NotNull Map<String, JavaReplaceChoice> getPossibleReplaceChoices(final Project project, final PsiExpression expr) {
    OccurrencesInfo occurrencesInfo = buildOccurrencesInfo(project, expr);
    final LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap = occurrencesInfo.buildOccurrencesMap(expr);
    return occurrencesMap.entrySet().stream().collect(Collectors.toMap(
      entry -> entry.getKey().formatDescription(entry.getValue().size()),
      entry -> entry.getKey()
    ));
  }

  private @NotNull OccurrencesInfo buildOccurrencesInfo(Project project, PsiExpression expr) {
    final PsiElement anchorStatement = getAnchor(expr);
    ErrorOrContainer result = getTempContainer(anchorStatement);

    final PsiElement tempContainer = switch (result) {
      case Container container -> {
        yield container.element();
      }
      case ErrorOrContainer.Error error -> {
        showErrorMessage(project, null, error.message());
        yield null;
      }
    };

    final ExpressionOccurrenceManager occurrenceManager = createOccurrenceManager(expr, tempContainer);
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();

    return new OccurrencesInfo(occurrences);
  }

  private static @Nullable JavaReplaceChoice findChoice(@NotNull LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap,
                                                        @NotNull JavaReplaceChoice replaceChoice) {
    return ContainerUtil.find(occurrencesMap.entrySet(), entry -> {
      return entry.getKey().formatDescription(0).equals(replaceChoice.formatDescription(0));
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
    IntroduceVariableResult introduceVariableResult = getIntroduceVariableContext(project, expr, editor);

    switch (introduceVariableResult) {
      case Context context -> {
        return doRefactoring(project, targetContainer, replaceChoice, editor, context);
      }
      case IntroduceVariableResult.Error error -> {
        if (error.message != null) {
          showErrorMessage(project, editor, error.message);
        }
        return false;
      }
    }
  }

  private boolean doRefactoring(Project project,
                                @Nullable PsiElement targetContainer,
                                @Nullable JavaReplaceChoice replaceChoice,
                                Editor editor,
                                @NotNull Context context) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, context.file())) return false;

    final LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap = context.occurrencesInfo.buildOccurrencesMap(context.expression);

    final IntroduceVariablePass callback = new IntroduceVariablePass(project, context, editor, targetContainer);

    if (replaceChoice != null) {
      callback.accept(findChoice(occurrencesMap, replaceChoice));
    }
    else if (!context.isInplaceAvailableOnDataContext) {
      callback.accept(null);
    }
    else {
      String title = context.occurrencesInfo.myChainMethodName != null && context.occurrenceManager.getOccurrences().length == 1
                     ? JavaRefactoringBundle.message("replace.lambda.chain.detected")
                     : RefactoringBundle.message("replace.multiple.occurrences.found");
      createOccurrencesChooser(editor).showChooser(occurrencesMap, title, callback);
    }
    return callback.wasSucceed;
  }

  public static @NotNull OccurrencesChooser<PsiExpression> createOccurrencesChooser(Editor editor) {
    return new OccurrencesChooser<>(editor) {
      @Override
      protected TextRange getOccurrenceRange(PsiExpression occurrence) {
        RangeMarker rangeMarker = occurrence.getUserData(ElementToWorkOn.TEXT_RANGE);
        if (rangeMarker != null) {
          return rangeMarker.getTextRange();
        }
        return occurrence.getTextRange();
      }
    };
  }

  public static boolean canBeExtractedWithoutExplicitType(PsiExpression expr) {
    if (PsiUtil.isAvailable(JavaFeature.LVTI, expr)) {
      PsiType type = GenericsUtil.getVariableTypeByExpressionType(expr.getType());
      if (type != null && !PsiTypes.nullType().equals(type) && PsiTypesUtil.isDenotableType(type, expr)) {
        PsiExpression copy =
          (PsiExpression)(type instanceof PsiDisjunctionType ? expr.copy() : LambdaUtil.copyWithExpectedType(expr, type));
        if (type.equals(GenericsUtil.getVariableTypeByExpressionType(copy.getType()))) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return the context necessary for performing "Introduce Variable" refactoring.
   */
  public static @NotNull IntroduceVariableBase.IntroduceVariableResult getIntroduceVariableContext(@NotNull Project project, @Nullable PsiExpression expr, @Nullable Editor editor) {
    if (expr != null) {
      String message = getErrorMessage(expr);
      if (message != null) {
        return new IntroduceVariableResult.Error(message);
      }
      PsiExpression topLevelExpression = ExpressionUtils.getTopLevelExpression(expr);
      if (topLevelExpression.getParent() instanceof PsiField f) {
        PsiClass containingClass = f.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          message = JavaRefactoringBundle.message("introduce.variable.message.cannot.extract.variable.in.interface");
          return new IntroduceVariableResult.Error(message);
        }
      }
    }

    if (expr != null && expr.getParent() instanceof PsiExpressionStatement) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.introduceVariable.incompleteStatement");
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + expr);
    }

    if (expr == null || !expr.isPhysical()) {
      if (ReassignVariableUtil.reassign(editor)) return new IntroduceVariableResult.Error(null);
      if (expr == null) {
        String message = JavaRefactoringBundle.message("selected.block.should.represent.an.expression");
        return new IntroduceVariableResult.Error(message);
      }
    }

    String enumInSwitchError = RefactoringUtil.checkEnumConstantInSwitchLabel(expr);
    if (enumInSwitchError != null) {
      return new IntroduceVariableResult.Error(enumInSwitchError);
    }


    DumbService dumbService = DumbService.getInstance(project);
    final PsiType originalType =
      dumbService.computeWithAlternativeResolveEnabled(() -> CommonJavaRefactoringUtil.getTypeByExpressionWithExpectedType(expr));
    if (originalType == null || LambdaUtil.notInferredType(originalType)) {
      String message = JavaRefactoringBundle.message("unknown.expression.type");
      return new IntroduceVariableResult.Error(message);
    }

    if (PsiTypes.voidType().equals(originalType)) {
      String message = JavaRefactoringBundle.message("selected.expression.has.void.type");
      return new IntroduceVariableResult.Error(message);
    }

    try {
      String typeText = DumbService.getInstance(project)
        .computeWithAlternativeResolveEnabled(() -> GenericsUtil.getVariableTypeByExpressionType(originalType).getCanonicalText());
      JavaPsiFacade.getElementFactory(project).createTypeElementFromText(typeText, expr);
    }
    catch (IncorrectOperationException ignore) {
      String message = JavaRefactoringBundle.message("unknown.expression.type");
      return new IntroduceVariableResult.Error(message);
    }

    for (PsiPatternVariable variable : JavaPsiPatternUtil.getExposedPatternVariables(expr)) {
      if (ContainerUtil.exists(VariableAccessUtils.getVariableReferences(variable),
                               ref -> !PsiTreeUtil.isAncestor(expr, ref, true))) {
        String message = JavaRefactoringBundle.message("selected.expression.introduces.pattern.variable", variable.getName());
        return new IntroduceVariableResult.Error(message);
      }
    }

    final PsiElement anchorStatement = getAnchor(expr);

    ErrorOrContainer errorOrContainer = getTempContainer(anchorStatement);

    return switch (errorOrContainer) {
      case Container container -> {
        final PsiFile file = Objects.requireNonNull(anchorStatement).getContainingFile();
        LOG.assertTrue(file != null, "expr.getContainingFile() == null");
        final PsiElement nameSuggestionContext = editor == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
        final RefactoringSupportProvider supportProvider = LanguageRefactoringSupport.getInstance().forContext(expr);
        final boolean isInplaceAvailableOnDataContext =
          supportProvider != null &&
          editor != null &&
          editor.getSettings().isVariableInplaceRenameEnabled() &&
          supportProvider.isInplaceIntroduceAvailable(expr, nameSuggestionContext) &&
          !isInJspHolderMethod(expr);

        if (isInplaceAvailableOnDataContext) {
          final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
          checkInLoopCondition(expr, conflicts);
          if (!conflicts.isEmpty()) {
            yield new IntroduceVariableResult.Error(StringUtil.join(new TreeSet<>(conflicts.values()), "<br>"), false);
          }
        }

        final ExpressionOccurrenceManager occurrenceManager = createOccurrenceManager(expr, container.element());
        final PsiExpression[] occurrences = occurrenceManager.getOccurrences();

        OccurrencesInfo occurrencesInfo = new OccurrencesInfo(occurrences);

        yield new Context(expr, originalType, anchorStatement, occurrenceManager, occurrencesInfo, isInplaceAvailableOnDataContext);
      }
      case ErrorOrContainer.Error error -> {
        yield new IntroduceVariableResult.Error(error.message(), false);
      }
    };
  }

  public static @Nullable PsiElement getAnchor(PsiElement place) {
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

  protected static @NotNull ErrorOrContainer getTempContainer(@Nullable PsiElement anchorStatement) {
    if (anchorStatement == null) {
      return new ErrorOrContainer.Error(
        JavaRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", getRefactoringName())
      );
    }

    String anchorMessage = getAnchorBeforeMessage(anchorStatement);
    if (anchorMessage != null) return new ErrorOrContainer.Error(anchorMessage);

    final PsiElement tempContainer = anchorStatement.getParent();

    if (!(tempContainer instanceof PsiCodeBlock) && !CommonJavaRefactoringUtil.isLoopOrIf(tempContainer) && !(tempContainer instanceof PsiLambdaExpression) && (tempContainer.getParent() instanceof PsiLambdaExpression)) {
      return new ErrorOrContainer.Error(
        JavaRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", getRefactoringName())
      );
    }
    return new Container(tempContainer);
  }

  private static ExpressionOccurrenceManager createOccurrenceManager(PsiExpression expr, @Nullable PsiElement tempContainer) {
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
        if (ContainerUtil.exists(vars, variable -> PsiTreeUtil.isAncestor(containingClass, variable, true))) {
          break;
        }
      }
      if (containerParent instanceof PsiLambdaExpression) {
        PsiParameter[] parameters = ((PsiLambdaExpression)containerParent).getParameterList().getParameters();
        if (ContainerUtil.exists(parameters, vars::contains)) {
          break;
        }
      }
      if (containerParent instanceof PsiForStatement forStatement &&
          ContainerUtil.exists(vars, variable -> PsiTreeUtil.isAncestor(forStatement.getInitialization(), variable, true))) {
        break;
      }
      containerParent = containerParent.getParent();
      if (containerParent instanceof PsiCodeBlock) {
        lastScope = containerParent;
      }
    }
    return new ExpressionOccurrenceManager(expr, lastScope, NotInConstructorCallFilter.INSTANCE);
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
    else if (initializer instanceof PsiNewExpression newExpression) {
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

  protected abstract void showErrorMessage(@NotNull Project project, @Nullable Editor editor, @NotNull String message);


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

  private static @Nullable @NlsContexts.DialogMessage String getAnchorBeforeMessage(PsiElement tempAnchorElement) {
    if (PsiUtil.isAvailable(JavaFeature.STATEMENTS_BEFORE_SUPER, tempAnchorElement)) {
      return null;
    }
    if (tempAnchorElement instanceof PsiExpressionStatement) {
      PsiExpression enclosingExpr = ((PsiExpressionStatement)tempAnchorElement).getExpression();
      if (enclosingExpr instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression)enclosingExpr).resolveMethod();
        if (method != null && method.isConstructor()) {
          //This is either 'this' or 'super', both must be the first in the respective constructor
          String message = JavaRefactoringBundle.message("invalid.expression.context");
          return message;
        }
      }
    }
    return null;
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
      PsiExpression expression = myOccurrences.getFirst();
      // The whole lambda body selected
      if (myOccurrences.size() == 1 && expression.getParent() instanceof PsiLambdaExpression) return null;
      PsiElement parent = PsiTreeUtil.findCommonParent(myOccurrences);
      if (parent == null) return null;
      PsiType type = expression.getType();
      PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, true, PsiStatement.class);
      ChainCallExtractor extractor = DumbService.getInstance(expression.getProject())
        .computeWithAlternativeResolveEnabled(() -> ChainCallExtractor.findExtractor(lambda, expression, type));
      if (extractor == null) return null;
      PsiParameter parameter = lambda.getParameterList().getParameters()[0];
      if (!ReferencesSearch.search(parameter).forEach((Processor<PsiReference>)ref ->
        ContainerUtil.exists(myOccurrences, expr -> PsiTreeUtil.isAncestor(expr, ref.getElement(), false)))) {
        return null;
      }
      return extractor.getMethodName(parameter, expression, type);
    }

    public @NotNull LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> buildOccurrencesMap(PsiExpression expr) {
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
      // i.e., for two compared elements a and b either a is ancestor of b or vice versa
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
      LOG.assertTrue(!groupByBlock.isEmpty());
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

  /**
   * Shows UI (inplace or dialog) when introducing the variable
   */
  private class IntroduceVariablePass implements Consumer<JavaReplaceChoice> {
    boolean wasSucceed = true;
    private final @NotNull Context context;
    private final @NotNull Project project;
    private final @NotNull Editor editor;
    private final @Nullable PsiElement targetContainer;

    private IntroduceVariablePass(@NotNull Project project, @NotNull Context context, @NotNull Editor editor, @Nullable PsiElement container) {
      this.context = context;
      this.project = project;
      this.editor = editor;
      targetContainer = container;
    }

    @Override
    public void accept(JavaReplaceChoice choice) {
      Consumer<JavaReplaceChoice> dialogIntroduce = c -> CommandProcessor.getInstance().executeCommand(project, () -> introduce(c), getRefactoringName(), null);
      if (choice == null) {
        dialogIntroduce.accept(null);
      }
      else {
        DumbService.getInstance(project).runWithAlternativeResolveEnabled(
          () -> inplaceIntroduce(project, editor, choice, targetContainer, context.occurrenceManager, context.originalType, dialogIntroduce));
      }
    }

    private void introduce(@Nullable JavaReplaceChoice choice) {
      if (!context.anchorStatement.isValid()) {
        return;
      }
      final Editor topLevelEditor;
      if (!InjectedLanguageManager.getInstance(project).isInjectedFragment(context.file())) {
        topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(editor);
      }
      else {
        topLevelEditor = editor;
      }

      PsiVariable variable = null;
      try {
        boolean hasWriteAccess = context.occurrencesInfo.myHasWriteAccess;
        final InputValidator validator = new InputValidator(IntroduceVariableBase.this, project, context.occurrenceManager);

        final TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(project, context.originalType, context.expression, context.occurrenceManager.getOccurrences());
        boolean inFinalContext = context.occurrenceManager.isInFinalContext();
        final IntroduceVariableSettings settings =
          getSettings(project, topLevelEditor, context.expression, context.occurrenceManager.getOccurrences(), typeSelectorManager, inFinalContext, hasWriteAccess, validator,
                      context.anchorStatement, choice);
        if (!settings.isOK()) {
          wasSucceed = false;
          return;
        }
        JavaReplaceChoice finalChoice = settings.getReplaceChoice();
        PsiExpression[] selectedOccurrences = finalChoice.filter(context.occurrenceManager);
        if (selectedOccurrences.length == 0) {
          showErrorMessage(project, editor, JavaRefactoringBundle.message("introduce.variable.no.matching.occurrences"));
          wasSucceed = false;
          return;
        }
        final PsiElement chosenAnchor = getAnchor(selectedOccurrences);
        if (chosenAnchor == null) {
          String text = context.file().getText();
          String textWithOccurrences = StreamEx.of(selectedOccurrences)
            .map(e -> getPhysicalElement(e).getTextRange())
            .flatMapToEntry(range -> EntryStream.of(range.getStartOffset(), "[", range.getEndOffset(), "]").toMap())
            .sortedBy(Map.Entry::getKey)
            .prepend(0, "")
            .append(text.length(), "")
            .map(Function.identity())
            .pairMap((prev, next) -> text.substring(prev.getKey(), next.getKey()) + next.getValue())
            .joining();
          LOG.error("Unable to find anchor for a new variable; selectedOccurrences.length = " + selectedOccurrences.length,
                                          new Attachment("source.java", textWithOccurrences));
          return;
        }

        final RefactoringEventData beforeData = new RefactoringEventData();
        beforeData.addElement(context.expression);
        project.getMessageBus()
          .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(REFACTORING_ID, beforeData);

        variable = VariableExtractor.introduce(project, context.expression, topLevelEditor, chosenAnchor, selectedOccurrences, settings);
      }
      finally {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(variable);
        project.getMessageBus()
          .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(REFACTORING_ID, afterData);
      }
    }

    private void inplaceIntroduce(@NotNull Project project,
                                  Editor editor,
                                  @NotNull JavaReplaceChoice choice,
                                  @Nullable PsiElement targetContainer,
                                  @NotNull ExpressionOccurrenceManager occurrenceManager,
                                  @NotNull PsiType originalType,
                                  @NotNull Consumer<? super JavaReplaceChoice> dialogIntroduce) {
      boolean inFinalContext = occurrenceManager.isInFinalContext();
      PsiExpression expr = occurrenceManager.getMainOccurence();
      PsiExpression[] selectedOccurrences = choice.filter(occurrenceManager);
      final InputValidator validator = new InputValidator(IntroduceVariableBase.this, project, occurrenceManager);
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
        Consumer<? super PsiElement> callback = container -> {
          PsiElement anchor = container instanceof PsiLambdaExpression ? getAnchor(container) : container;
          ErrorOrContainer errorOrContainer = getTempContainer(anchor);
          if (errorOrContainer instanceof ErrorOrContainer.Error(String message)) {
            showErrorMessage(project, editor, message);
            return;
          }
          myInplaceIntroducer = new JavaVariableInplaceIntroducer(project, settings, anchor, editor, expr,
                                                                  cantChangeFinalModifier, selectedOccurrences, typeSelectorManager,
                                                                  getRefactoringName());
          if (!myInplaceIntroducer.startInplaceIntroduceTemplate()) {
            dialogIntroduce.accept(choice);
          }
        };
        if (targetContainer != null) {
          callback.accept(targetContainer);
        }
        else {
          IntroduceVariableTargetBlockChooser.chooseTargetAndPerform(editor, chosenAnchor, expr, callback);
        }
      }
    }
  }

  /**
   * Represents the result of getting the necessary environment for introducing a variable.
   * @see IntroduceVariablePass#getIntroduceVariableContext(Project, PsiExpression, Editor)
   */
  public sealed interface IntroduceVariableResult permits IntroduceVariableResult.Error, Context {

    /**
     * Represents a message that will be displayed in UI if there is an error during collecting the context for introduced variable.
     * @see IntroduceVariablePass#showErrorMessage(Project, Editor, String)
     */
    final class Error implements IntroduceVariableResult {
      public final @NlsContexts.DialogMessage @Nullable String message;

      Error(@NlsContexts.DialogMessage @Nullable String message) {
        this(message, true);
      }

      Error(@NlsContexts.DialogMessage @Nullable String message, boolean shouldWrap) {
        if (message == null) {
          this.message = null;
        }
        else if (shouldWrap) {
          this.message = RefactoringBundle.getCannotRefactorMessage(message);
        }
        else {
          this.message = message;
        }
      }
    }

    /**
     * Represents all the data necessary to introduce the variable.
     * @param expression - element that should be extracted into the separate variable.
     * @param originalType - type of the expression that should be extracted.
     * @param anchorStatement - statement near which the declared variable will be created.
     * @param occurrenceManager - stores all occurrences of the expression that should be extracted.
     * @param occurrencesInfo - stores additional information about occurrences like whether they are valid for extraction.
     * @param isInplaceAvailableOnDataContext - indicates whether inplace refactoring is available for the current context.
     */
    record Context(
      @NotNull PsiExpression expression,
      @NotNull PsiType originalType,
      @NotNull PsiElement anchorStatement,
      @NotNull ExpressionOccurrenceManager occurrenceManager,
      @NotNull OccurrencesInfo occurrencesInfo,
      boolean isInplaceAvailableOnDataContext
    ) implements IntroduceVariableResult {
      PsiFile file() {
        return anchorStatement.getContainingFile();
      }
    }
  }

  protected sealed interface ErrorOrContainer permits Container, ErrorOrContainer.Error {
    record Container(@NotNull PsiElement element) implements ErrorOrContainer {}
    record Error(@NotNull @NlsContexts.DialogMessage String message) implements ErrorOrContainer {}
  }
}
