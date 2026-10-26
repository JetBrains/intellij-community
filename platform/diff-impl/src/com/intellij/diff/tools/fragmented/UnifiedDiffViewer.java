// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.diff.DiffContext;
import com.intellij.diff.EditorDiffViewer;
import com.intellij.diff.actions.AllLinesIterator;
import com.intellij.diff.actions.BufferedLineIterator;
import com.intellij.diff.actions.impl.OpenInEditorWithMouseAction;
import com.intellij.diff.actions.impl.SetEditorSettingsActionGroup;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.fragmented.UnifiedDiffModel.ChangedBlockData;
import com.intellij.diff.tools.util.DiffChangedRangeProvider;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.DiffTitleHandler;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.diff.tools.util.PrevNextDifferenceIterableBase;
import com.intellij.diff.tools.util.StatusPanel;
import com.intellij.diff.tools.util.base.InitialScrollPositionSupport;
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.breadcrumbs.DiffBreadcrumbsPanel;
import com.intellij.diff.tools.util.side.OnesideContentPanel;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.tools.util.text.TwosideTextDiffProvider;
import com.intellij.diff.util.DiffEditorHighlighterUpdater;
import com.intellij.diff.util.DiffLineNumberConverter;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineCol;
import com.intellij.diff.util.LineRange;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.EditorProjection;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xml.breadcrumbs.NavigatableCrumb;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

public class UnifiedDiffViewer extends ListenerDiffViewerBase implements EditorDiffViewer {
  protected final @NotNull EditorEx myEditor;
  protected final @NotNull Document myDocument;
  protected final @NotNull UnifiedDiffPanel myPanel;
  private final @NotNull OnesideContentPanel myContentPanel;

  private final @NotNull SetEditorSettingsActionGroup myEditorSettingsAction;
  private final @NotNull PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  private final @NotNull MyStatusPanel myStatusPanel;

  private final @NotNull MyInitialScrollHelper myInitialScrollHelper = new MyInitialScrollHelper();
  private final @NotNull UnifiedFoldingModel myFoldingModel;
  private final @NotNull MarkupUpdater myMarkupUpdater;
  private final @Nullable List<UnifiedSideEditor> mySideEditors;

  protected final @NotNull TwosideTextDiffProvider.NoIgnore myTextDiffProvider;

  protected @NotNull Side myMasterSide = Side.RIGHT;

  @ApiStatus.Internal protected final @NotNull UnifiedDiffModel myModel = new UnifiedDiffModel(this);

  private final boolean[] myForceReadOnlyFlags;
  private boolean myReadOnlyLockSet = false;

  private boolean myDuringOnesideDocumentModification;
  private boolean myDuringTwosideDocumentModification;

  private boolean myStateIsOutOfDate; // whether something was changed since last rediff
  private boolean mySuppressEditorTyping; // our state is inconsistent. No typing can be handled correctly

  public UnifiedDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    myStatusPanel = new MyStatusPanel();

    myForceReadOnlyFlags = TextDiffViewerUtil.checkForceReadOnly(myContext, myRequest);

    boolean leftEditable = isEditable(Side.LEFT, false);
    boolean rightEditable = isEditable(Side.RIGHT, false);
    if (leftEditable && !rightEditable) myMasterSide = Side.LEFT;
    if (!leftEditable && rightEditable) myMasterSide = Side.RIGHT;


    myDocument = EditorFactory.getInstance().createDocument("");

    myEditor = DiffUtil.createEditor(myDocument, myProject, true, true);
    mySideEditors = createSideEditors();
    EditorProjection.registerProvider(myEditor, this::getSideEditor);

    myContentPanel = new OnesideContentPanel(myEditor.getComponent());
    if (getProject() != null) {
      myContentPanel.setBreadcrumbs(new UnifiedBreadcrumbsPanel(), getTextSettings());
    }

    myPanel = new UnifiedDiffPanel(myProject, myContentPanel, myContext) {
      @Override
      public void uiDataSnapshot(@NotNull DataSink sink) {
        super.uiDataSnapshot(sink);
        DataSink.uiDataSnapshot(sink, UnifiedDiffViewer.this);
      }
    };

    myFoldingModel = new UnifiedFoldingModel(getProject(), myEditor, this);
    myMarkupUpdater = new MarkupUpdater(getContents());

    myEditorSettingsAction = new SetEditorSettingsActionGroup(getTextSettings(), getEditors());
    myEditorSettingsAction.applyDefaults();

    myTextDiffProvider = DiffUtil.createNoIgnoreTextDiffProvider(getProject(), getRequest(), getTextSettings(), this::rediff, this);

    new MyOpenInEditorWithMouseAction().install(getEditors());

    TextDiffViewerUtil.checkDifferentDocuments(myRequest);

    DiffUtil.registerAction(new AppendSelectedChangesAction(Side.LEFT), myPanel);
    DiffUtil.registerAction(new AppendSelectedChangesAction(Side.RIGHT), myPanel);
  }

  @Override
  @RequiresEdt
  protected void onInit() {
    super.onInit();
    installEditorListeners();
    installTypingSupport();
    myPanel.setLoadingContent(); // We need loading panel only for initial rediff()
    myPanel.setPersistentNotifications(DiffUtil.createCustomNotifications(this, myContext, myRequest));
    DiffTitleHandler.createHandler(() -> createTitles(), myContentPanel, myRequest, this);

    DiffUtil.installShowNotifyListener(getComponent(), new Activatable() {
      @Override
      public void showNotify() {
        myMarkupUpdater.scheduleUpdate();
      }

      @Override
      public void hideNotify() {
        Project project = myProject;
        if (project == null) return;

        //force re-highlighting the document which may be opened in other editor tab and miss highlighters because of com.intellij.openapi.editor.impl.MarkupModelImpl#removeHighlighter
        Document document = getContent2().getDocument();
        DiffEditorHighlighterUpdater.restartHighlighterFor(project, myEditor, document, "UnifiedDiffViewer.hide");
      }
    });
  }

  @Override
  @RequiresEdt
  protected void onDispose() {
    super.onDispose();
    EditorFactory.getInstance().releaseEditor(myEditor);
    EditorActionManager.getInstance().setReadonlyFragmentModificationHandler(myDocument, null);
  }

  @Override
  @RequiresEdt
  protected void processContextHints() {
    super.processContextHints();
    Side side = DiffUtil.getUserData(myRequest, myContext, DiffUserDataKeys.MASTER_SIDE);
    if (side != null) myMasterSide = side;

    myInitialScrollHelper.processContext(myRequest);
  }

  @Override
  @RequiresEdt
  protected void updateContextHints() {
    super.updateContextHints();
    myInitialScrollHelper.updateContext(myRequest);
    myFoldingModel.updateContext(myRequest, getFoldingModelSettings());
  }

  protected @Nullable JComponent createTitles() {
    List<JComponent> titles = DiffUtil.createTextTitles(this, myRequest, Arrays.asList(myEditor, myEditor));
    assert titles.size() == 2;

    titles = ContainerUtil.skipNulls(titles);
    if (titles.isEmpty()) return null;

    return DiffUtil.createStackedTitleComponents(titles);
  }

  @RequiresEdt
  protected void updateEditorCanBeTyped() {
    myEditor.setViewer(mySuppressEditorTyping || !isEditable(myMasterSide, true));
  }

  private void installTypingSupport() {
    if (!isEditable(myMasterSide, false)) return;

    updateEditorCanBeTyped();
    myEditor.getColorsScheme().setColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, null); // guarded blocks
    EditorActionManager.getInstance().setReadonlyFragmentModificationHandler(myDocument, new MyReadonlyFragmentModificationHandler());
    myDocument.putUserData(UndoManager.ORIGINAL_DOCUMENT, getDocument(myMasterSide)); // use undo of master document

    myDocument.addDocumentListener(new MyOnesideDocumentListener(), this);
  }

  @Override
  @RequiresEdt
  public @NotNull List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<>();
    group.add(new MyToggleExpandByDefaultAction());
    group.add(new MyReadOnlyLockAction());
    group.add(Separator.getInstance());
    group.addAll(super.createToolbarActions());

    return group;
  }

  @Override
  @RequiresEdt
  public @NotNull List<AnAction> createRightToolbarActions() {
    List<AnAction> diffActions = new ArrayList<>();
    myEditorSettingsAction.setSettingsActions(diffActions, myTextDiffProvider.getDiffSettingsActions());

    return List.of(myEditorSettingsAction);
  }

  @Override
  @RequiresEdt
  public @NotNull List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<>(myTextDiffProvider.getDiffSettingsActions());

    group.add(Separator.getInstance());
    group.addAll(super.createPopupActions());
    group.add(Separator.getInstance());
    group.add(new MyToggleExpandByDefaultAction());

    return group;
  }

  protected @NotNull List<@NotNull AnAction> createAdditionalEditorGutterActions() {
    List<AnAction> actions = new ArrayList<>();
    actions.add(new MyToggleExpandByDefaultAction());
    return actions;
  }


  private @NotNull List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<>(createEditorPopupChangesActions());
    group.add(Separator.getInstance());
    group.add(ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_POPUP));

    group.add(Separator.getInstance());
    group.add(new MyToggleExpandByDefaultAction());

    return group;
  }

  protected @NotNull List<@NotNull AnAction> createEditorPopupChangesActions() {
    List<AnAction> actions = new ArrayList<>();
    actions.add(new ReplaceSelectedChangesAction(Side.LEFT));
    actions.add(new ReplaceSelectedChangesAction(Side.RIGHT));
    return actions;
  }

  @RequiresEdt
  protected void installEditorListeners() {
    new TextDiffViewerUtil.EditorActionsPopup(createEditorPopupActions()).install(getEditors(), myPanel);
    ActionGroup gutterActionGroup =
      TextDiffViewerUtil.createEditorGutterActionGroup(myEditorSettingsAction, createAdditionalEditorGutterActions());
    TextDiffViewerUtil.installGutterPopup(getEditors(), gutterActionGroup);
  }

  @ApiStatus.Internal
  protected @NotNull UnifiedDiffChangeUi createUi(@NotNull UnifiedDiffChange change) {
    return new UnifiedDiffChangeUi(this, change);
  }

  //
  // Diff
  //

  @Override
  protected void onBeforeDocumentChange(@NotNull DocumentEvent event) {
    super.onBeforeDocumentChange(event);
    myMarkupUpdater.suspendUpdate();
  }

  @Override
  protected void onBeforeRediff() {
    super.onBeforeRediff();
    myMarkupUpdater.suspendUpdate();
  }

  @Override
  @RequiresEdt
  protected void onSlowRediff() {
    super.onSlowRediff();
    myStatusPanel.setBusy(true);
  }

  @Override
  protected @NotNull Runnable performRediff(final @NotNull ProgressIndicator indicator) {
    try {
      return computeDifferences(indicator);
    }
    catch (DiffTooBigException e) {
      return () -> {
        clearDiffPresentation();
        myPanel.setTooBigContent();
      };
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return applyErrorNotification();
    }
  }

  protected @NotNull Runnable applyErrorNotification() {
    return () -> {
      clearDiffPresentation();
      myPanel.setErrorContent();
    };
  }

  protected @NotNull Runnable computeDifferences(@NotNull ProgressIndicator indicator) {
    final Document document1 = getContent1().getDocument();
    final Document document2 = getContent2().getDocument();

    CharSequence[] texts = ReadAction.computeBlocking(
      () -> new CharSequence[]{document1.getImmutableCharSequence(), document2.getImmutableCharSequence()});

    final List<LineFragment> fragments = myTextDiffProvider.compare(texts[0], texts[1], indicator);

    UnifiedDiffState builder = ReadAction.computeBlocking(() -> {
      indicator.checkCanceled();
      return new SimpleUnifiedFragmentBuilder(document1, document2, myMasterSide).exec(fragments);
    });

    return apply(builder, texts, indicator);
  }

  protected void clearDiffPresentation() {
    myPanel.resetNotifications();
    myStatusPanel.setBusy(false);
    destroyChangedBlockData();

    myStateIsOutOfDate = false;
    mySuppressEditorTyping = false;
    updateEditorCanBeTyped();
  }

  @RequiresEdt
  protected void markSuppressEditorTyping() {
    mySuppressEditorTyping = true;
    updateEditorCanBeTyped();
  }

  @RequiresEdt
  protected void markStateIsOutOfDate() {
    myStateIsOutOfDate = true;
    myFoldingModel.disposeLineConvertor();
    myModel.updateGutterActions();
  }

  protected @NotNull Runnable apply(@NotNull UnifiedDiffState builder,
                                    CharSequence @NotNull [] texts,
                                    @NotNull ProgressIndicator indicator) {
    final DocumentContent content1 = getContent1();
    final DocumentContent content2 = getContent2();

    UnifiedDiffHighlightersData unifiedDiffHighlightersData = BackgroundTaskUtil.tryComputeFast(_ -> {
      return ReadAction.computeBlocking(() -> {
        EditorHighlighter highlighter =
          UnifiedEditorHighlighter.buildHighlighter(myProject, myDocument, content1, content2,
                           texts[0], texts[1], builder.getRanges(),
                           builder.getText().length());
        UnifiedEditorRangeHighlighter rangeHighlighter =
          new UnifiedEditorRangeHighlighter(myProject, content1.getDocument(),
                                            content2.getDocument(), builder.getRanges());
        return new UnifiedDiffHighlightersData(highlighter, rangeHighlighter);
      });
    }, 500);

    LineNumberConvertor convertor1 = builder.getConvertor1();
    LineNumberConvertor convertor2 = builder.getConvertor2();
    List<LineRange> changedLines = builder.getChangedLines();
    boolean isContentsEqual = changedLines.isEmpty() && StringUtil.equals(texts[0], texts[1]);

    Side masterSide = builder.getMasterSide();
    FoldingModelSupport.Data foldingState = myFoldingModel.createState(changedLines, getFoldingModelSettings(),
                                                                       getDocument(masterSide), masterSide.select(convertor1, convertor2),
                                                                       StringUtil.countNewLines(builder.getText()) + 1);

    return () -> {
      WriteIntentReadAction.run(() -> {

        myFoldingModel.updateContext(myRequest, getFoldingModelSettings());

        LineCol oldCaretPosition = LineCol.fromOffset(myDocument, myEditor.getCaretModel().getPrimaryCaret().getOffset());
        Pair<int[], Side> oldCaretLineTwoside = transferLineFromOneside(oldCaretPosition.line);


        clearDiffPresentation();


        if (isContentsEqual &&
            !DiffUtil.isUserDataFlagSet(DiffUserDataKeysEx.DISABLE_CONTENTS_EQUALS_NOTIFICATION, myContext, myRequest)) {
          myPanel.addNotification(TextDiffViewerUtil.createEqualContentsNotification(getContents()));
        }

        IntPredicate foldingLinePredicate = myFoldingModel.hideLineNumberPredicate(0);
        IntUnaryOperator merged1 = DiffUtil.mergeLineConverters(DiffUtil.getContentLineConvertor(getContent1()),
                                                                convertor1.createConvertor());
        IntUnaryOperator merged2 = DiffUtil.mergeLineConverters(DiffUtil.getContentLineConvertor(getContent2()),
                                                                convertor2.createConvertor());
        myEditor.getGutter().setLineNumberConverter(new DiffLineNumberConverter(foldingLinePredicate, merged1),
                                                    new DiffLineNumberConverter(foldingLinePredicate, merged2));

        ApplicationManager.getApplication().runWriteAction(() -> {
          myDuringOnesideDocumentModification = true;
          try {
            myDocument.setText(builder.getText());
          }
          finally {
            myDuringOnesideDocumentModification = false;
          }
        });

        DiffUtil.setEditorCodeStyle(myProject, myEditor, getContent(myMasterSide));

        List<RangeMarker> guarderRangeBlocks = new ArrayList<>();
        if (!myEditor.isViewer()) {
          for (UnifiedDiffChange change : builder.getChanges()) {
            LineRange range = myMasterSide.select(change.getInsertedRange(), change.getDeletedRange());
            if (range.isEmpty()) continue;
            TextRange textRange = DiffUtil.getLinesRange(myDocument, range.start, range.end);
            guarderRangeBlocks.add(createGuardedBlock(textRange.getStartOffset(), textRange.getEndOffset()));
          }
          int textLength = myDocument.getTextLength(); // there are 'fake' newline at the very end
          guarderRangeBlocks.add(createGuardedBlock(textLength, textLength));
        }

        myModel.setChanges(builder.getChanges(), isContentsEqual, guarderRangeBlocks, convertor1, convertor2, builder.getRanges());

        int newCaretLine = transferLineToOneside(oldCaretLineTwoside.second,
                                                 oldCaretLineTwoside.second.select(oldCaretLineTwoside.first));
        myEditor.getCaretModel().moveToOffset(LineCol.toOffset(myDocument, newCaretLine, oldCaretPosition.column));

        myFoldingModel.install(foldingState, myRequest, getFoldingModelSettings());

        UnifiedDiffHighlightersData.apply(myProject, myEditor, unifiedDiffHighlightersData);
        myMarkupUpdater.resumeUpdate();

        myInitialScrollHelper.onRediff();

        myStatusPanel.update();
        myPanel.setGoodContent();

        myEditor.getGutterComponentEx().revalidateMarkup();
      });
    };
  }


  private @NotNull RangeMarker createGuardedBlock(int start, int end) {
    RangeMarker block = myDocument.createGuardedBlock(start, end);
    block.setGreedyToLeft(true);
    block.setGreedyToRight(true);
    return block;
  }

  /*
   * This convertor returns -1 if exact matching is impossible
   */
  public int transferLineToOnesideStrict(@NotNull Side side, int line) {
    LineNumberConvertor convertor = myModel.getLineNumberConvertor(side);
    return convertor != null ? convertor.convertInv(line) : -1;
  }

  /*
   * This convertor returns -1 if exact matching is impossible
   */
  public int transferLineFromOnesideStrict(@NotNull Side side, int line) {
    LineNumberConvertor convertor = myModel.getLineNumberConvertor(side);
    return convertor != null ? convertor.convert(line) : -1;
  }

  /*
   * This convertor returns 'good enough' position, even if exact matching is impossible
   */
  public int transferLineToOneside(@NotNull Side side, int line) {
    LineNumberConvertor convertor = myModel.getLineNumberConvertor(side);
    return convertor != null ? convertor.convertApproximateInv(line) : line;
  }

  public int transferLineFromOneside(@NotNull Side side, int line) {
    return side.select(transferLineFromOneside(line).first);
  }

  /*
   * This convertor returns 'good enough' position, even if exact matching is impossible
   */
  public @NotNull Pair<int[], @NotNull Side> transferLineFromOneside(int line) {
    return Objects.requireNonNull(transferLineFromOneside(line, false));
  }

  /*
   * This convertor returns position strictly. Returns null if exact matching is not possible
   */
  public @Nullable Pair<int[], @NotNull Side> transferLineFromOnesideStrict(int line) {
    return transferLineFromOneside(line, true);
  }

  private @Nullable Pair<int[], @NotNull Side> transferLineFromOneside(int line, boolean strictConversion) {
    int[] lines = new int[2];

    ChangedBlockData blockData = myModel.getData();
    if (blockData == null) {
      lines[0] = line;
      lines[1] = line;
      return Pair.create(lines, myMasterSide);
    }

    LineNumberConvertor lineConvertor1 = blockData.getLineNumberConvertor(Side.LEFT);
    LineNumberConvertor lineConvertor2 = blockData.getLineNumberConvertor(Side.RIGHT);

    Side side = myMasterSide;
    lines[0] = lineConvertor1.convert(line);
    lines[1] = lineConvertor2.convert(line);

    if (lines[0] == -1 && lines[1] == -1) {
      if(strictConversion) return null;
      lines[0] = lineConvertor1.convertApproximate(line);
      lines[1] = lineConvertor2.convertApproximate(line);
    }
    else if (lines[0] == -1) {
      lines[0] = lineConvertor1.convertApproximate(line);
      side = Side.RIGHT;
    }
    else if (lines[1] == -1) {
      lines[1] = lineConvertor2.convertApproximate(line);
      side = Side.LEFT;
    }

    return Pair.create(lines, side);
  }

  /*
   * These convertors map an offset by its line and column. They return -1 when the line convertor returns -1.
   * The unified document shows a shared line with the text of the master side. When the comparison ignores whitespace,
   * the line of the other side can differ in it, and the column then follows the words, see transferColumn().
   * A strict convertor also returns -1 when the two lines differ in more than whitespace.
   */
  public int transferOffsetToOnesideStrict(@NotNull Side side, int offset) {
    LineCol position = LineCol.fromOffset(getDocument(side), offset);
    int line = transferLineToOnesideStrict(side, position.line);
    if (line == -1) return -1;
    return toOnesideOffset(side, position, line, true);
  }

  public int transferOffsetToOneside(@NotNull Side side, int offset) {
    LineCol position = LineCol.fromOffset(getDocument(side), offset);
    int line = transferLineToOneside(side, position.line);
    if (line == -1) return -1;
    return toOnesideOffset(side, position, line, false);
  }

  public int transferOffsetFromOnesideStrict(@NotNull Side side, int offset) {
    LineCol position = LineCol.fromOffset(myDocument, offset);
    int line = transferLineFromOnesideStrict(side, position.line);
    if (line == -1) return -1;
    return fromOnesideOffset(side, position, line, true);
  }

  public int transferOffsetFromOneside(@NotNull Side side, int offset) {
    LineCol position = LineCol.fromOffset(myDocument, offset);
    int line = transferLineFromOneside(side, position.line);
    if (line == -1) return -1;
    return fromOnesideOffset(side, position, line, false);
  }

  private int toOnesideOffset(@NotNull Side side, @NotNull LineCol sidePosition, int onesideLine, boolean strict) {
    int column = transferColumn(getDocument(side), myDocument, sidePosition, onesideLine, strict);
    if (column == -1) return -1;
    return LineCol.toOffset(myDocument, onesideLine, column);
  }

  private int fromOnesideOffset(@NotNull Side side, @NotNull LineCol onesidePosition, int sideLine, boolean strict) {
    int column = transferColumn(myDocument, getDocument(side), onesidePosition, sideLine, strict);
    if (column == -1) return -1;
    return LineCol.toOffset(getDocument(side), sideLine, column);
  }

  /**
   * Converts a column between two sides.
   * <p>
   * The line texts may not match if {@link com.intellij.diff.tools.util.base.IgnorePolicy} is set and needs to be adjusted.
   *
   * @param strict if we should return -1 if exact matching is impossible
   */
  private static int transferColumn(@NotNull Document fromDocument,
                                    @NotNull Document toDocument,
                                    @NotNull LineCol fromPosition,
                                    int toLine,
                                    boolean strict) {
    CharSequence fromText = getLineText(fromDocument, fromPosition.line);
    CharSequence toText = getLineText(toDocument, toLine);

    if (StringUtil.equals(fromText, toText)) return fromPosition.column;
    if (fromPosition.column > fromText.length()) {
      return toText.length() + 1; // 'end of this line' offset
    }

    Integer transferred = LineColumnTransfer.transferColumn(fromText, toText, fromPosition.column);
    if (transferred != null) return transferred;

    if (strict) return -1;
    return Math.min(fromPosition.column, toText.length());
  }

  private static @NotNull CharSequence getLineText(@NotNull Document document, int line) {
    if (line >= DiffUtil.getLineCount(document)) return "";
    return DiffUtil.getLinesContent(document, line, line + 1);
  }

  /*
   * This convertor returns 'good enough' position and the side that shows the line, even if exact matching is impossible
   */
  public @Nullable Pair<Integer, Side> transferOffsetFromOneside(int offset) {
    LineCol position = LineCol.fromOffset(myDocument, offset);
    Pair<int[], Side> pair = transferLineFromOneside(position.line);
    Side side = pair.second;
    int line = side.select(pair.first);
    if (line == -1) return null;
    return Pair.create(fromOnesideOffset(side, position, line, false), side);
  }

  /*
   * This convertor returns null if exact matching is impossible, or if the unified document shows lines of the other side inside the range.
   * The end offset is exclusive, so a range that ends with a line break stays on the line of that line break.
   */
  public @Nullable TextRange transferRangeToOnesideStrict(@NotNull Side side, @NotNull TextRange range) {
    Document sideDocument = getDocument(side);
    LineCol start = LineCol.fromOffset(sideDocument, range.getStartOffset());
    // the end is exclusive: the line of the last character, with a column past the line end for a line break
    int endLine = sideDocument.getLineNumber(Math.max(range.getStartOffset(), range.getEndOffset() - 1));
    LineCol end = new LineCol(endLine, range.getEndOffset() - sideDocument.getLineStartOffset(endLine));

    int onesideStartLine = transferLineToOnesideStrict(side, start.line);
    int onesideEndLine = transferLineToOnesideStrict(side, end.line);
    if (onesideStartLine == -1 || onesideEndLine == -1) return null;
    if (onesideEndLine - onesideStartLine != end.line - start.line) return null;

    int onesideStartColumn = transferColumn(sideDocument, myDocument, start, onesideStartLine, true);
    int onesideEndColumn = transferColumn(sideDocument, myDocument, end, onesideEndLine, true);
    if (onesideStartColumn == -1 || onesideEndColumn == -1) return null;

    int onesideStart = new LineCol(onesideStartLine, onesideStartColumn).toOffset(myDocument);
    // LineCol.toOffset would clamp the column to the line end and lose the line break
    int onesideEnd = Math.min(myDocument.getLineStartOffset(onesideEndLine) + onesideEndColumn, myDocument.getTextLength());
    return new TextRange(onesideStart, Math.max(onesideStart, onesideEnd));
  }

  @RequiresEdt
  private void destroyChangedBlockData() {
    myModel.clear();

    UnifiedEditorRangeHighlighter.erase(myProject, myDocument);

    myFoldingModel.destroy();

    myStatusPanel.update();
  }

  //
  // Typing
  //

  private class MyOnesideDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent e) {
      if (myDuringOnesideDocumentModification) return;
      ChangedBlockData blockData = myModel.getData();
      if (blockData == null) {
        LOG.warn("oneside beforeDocumentChange - model is invalid");
        return;
      }
      // TODO: modify Document guard range logic - we can handle case, when whole read-only block is modified (ex: my replacing selection).

      try {
        myDuringTwosideDocumentModification = true;

        Document twosideDocument = getDocument(myMasterSide);

        LineCol onesideStartPosition = LineCol.fromOffset(myDocument, e.getOffset());
        LineCol onesideEndPosition = LineCol.fromOffset(myDocument, e.getOffset() + e.getOldLength());

        int line1 = onesideStartPosition.line;
        int line2 = onesideEndPosition.line + 1;
        int shift = DiffUtil.countLinesShift(e);

        int twosideStartLine = transferLineFromOnesideStrict(myMasterSide, onesideStartPosition.line);
        int twosideEndLine = transferLineFromOnesideStrict(myMasterSide, onesideEndPosition.line);
        if (twosideStartLine == -1 || twosideEndLine == -1) {
          // this should never happen
          logDebugInfo(e, onesideStartPosition, onesideEndPosition, twosideStartLine, twosideEndLine);
          markSuppressEditorTyping();
          return;
        }

        int twosideStartOffset = twosideDocument.getLineStartOffset(twosideStartLine) + onesideStartPosition.column;
        int twosideEndOffset = twosideDocument.getLineStartOffset(twosideEndLine) + onesideEndPosition.column;
        twosideDocument.replaceString(twosideStartOffset, twosideEndOffset, e.getNewFragment());

        for (UnifiedDiffChange change : blockData.getDiffChanges()) {
          change.processChange(line1, line2, shift);
        }

        LineNumberConvertor masterConvertor = blockData.getLineNumberConvertor(myMasterSide);
        LineNumberConvertor slaveConvertor = blockData.getLineNumberConvertor(myMasterSide.other());
        masterConvertor.handleMasterChange(line1, line2, shift, true);
        slaveConvertor.handleMasterChange(line1, line2, shift, false);
      }
      finally {
        // TODO: we can avoid marking state out-of-date in some simple cases (like in SimpleDiffViewer)
        // but this will greatly increase complexity, so let's wait if it's actually required by users
        markStateIsOutOfDate();

        scheduleRediff();

        myDuringTwosideDocumentModification = false;
      }
    }

    private void logDebugInfo(DocumentEvent e,
                              LineCol onesideStartPosition, LineCol onesideEndPosition,
                              int twosideStartLine, int twosideEndLine) {
      @NonNls StringBuilder info = new StringBuilder();
      Document document1 = getDocument(Side.LEFT);
      Document document2 = getDocument(Side.RIGHT);
      info.append("==== UnifiedDiffViewer Debug Info ====");
      info.append("myMasterSide - ").append(myMasterSide).append('\n');
      info.append("myLeftDocument.length() - ").append(document1.getTextLength()).append('\n');
      info.append("myRightDocument.length() - ").append(document2.getTextLength()).append('\n');
      info.append("myDocument.length() - ").append(myDocument.getTextLength()).append('\n');
      info.append("e.getOffset() - ").append(e.getOffset()).append('\n');
      info.append("e.getNewLength() - ").append(e.getNewLength()).append('\n');
      info.append("e.getOldLength() - ").append(e.getOldLength()).append('\n');
      info.append("onesideStartPosition - ").append(onesideStartPosition).append('\n');
      info.append("onesideEndPosition - ").append(onesideEndPosition).append('\n');
      info.append("twosideStartLine - ").append(twosideStartLine).append('\n');
      info.append("twosideEndLine - ").append(twosideEndLine).append('\n');
      Pair<int[], Side> pair1 = transferLineFromOneside(onesideStartPosition.line);
      Pair<int[], Side> pair2 = transferLineFromOneside(onesideEndPosition.line);
      info.append("non-strict transferStartLine - ").append(pair1.first[0]).append("-").append(pair1.first[1])
        .append(":").append(pair1.second).append('\n');
      info.append("non-strict transferEndLine - ").append(pair2.first[0]).append("-").append(pair2.first[1])
        .append(":").append(pair2.second).append('\n');
      info.append("---- UnifiedDiffViewer Debug Info ----");

      LOG.warn(info.toString());
    }
  }

  @Override
  protected void onDocumentChange(@NotNull DocumentEvent e) {
    if (myDuringTwosideDocumentModification) return;

    markStateIsOutOfDate();
    markSuppressEditorTyping();

    scheduleRediff();
  }

  //
  // Modification operations
  //

  private abstract class ApplySelectedChangesActionBase extends AnAction implements DumbAware {
    protected final @NotNull Side myModifiedSide;

    ApplySelectedChangesActionBase(@NotNull Side modifiedSide) {
      myModifiedSide = modifiedSide;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (DiffUtil.isFromShortcut(e)) {
        // consume shortcut even if there are nothing to do - avoid calling some other action
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != getEditor()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      if (!isEditable(myModifiedSide, true) || isStateIsOutOfDate()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(isSomeChangeSelected());
    }

    @Override
    public void actionPerformed(final @NotNull AnActionEvent e) {
      final List<UnifiedDiffChange> selectedChanges = getSelectedChanges();
      if (selectedChanges.isEmpty()) return;

      if (!isEditable(myModifiedSide, true)) return;
      if (isStateIsOutOfDate()) return;

      String title = DiffBundle.message("message.use.selected.changes.command", e.getPresentation().getText());
      DiffUtil.executeWriteCommand(getDocument(myModifiedSide), e.getProject(), title, () -> {
        // state is invalidated during apply(), but changes are in reverse order, so they should not conflict with each other
        apply(ContainerUtil.reverse(selectedChanges));
        scheduleRediff();
      });
    }

    protected boolean isSomeChangeSelected() {
      List<UnifiedDiffChange> changes = myModel.getDiffChanges();
      if (changes == null || changes.isEmpty()) return false;

      return DiffUtil.isSomeRangeSelected(getEditor(), lines -> ContainerUtil.exists(changes, change -> isChangeSelected(change, lines)));
    }

    @RequiresWriteLock
    protected abstract void apply(@NotNull List<? extends UnifiedDiffChange> changes);
  }

  private class ReplaceSelectedChangesAction extends ApplySelectedChangesActionBase {
    ReplaceSelectedChangesAction(@NotNull Side focusedSide) {
      super(focusedSide.other());

      copyShortcutFrom(ActionManager.getInstance().getAction(focusedSide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide")));
      getTemplatePresentation().setText(UnifiedDiffChangeUi.getApplyActionText(UnifiedDiffViewer.this, focusedSide));
      getTemplatePresentation().setIcon(UnifiedDiffChangeUi.getApplyIcon(focusedSide));
    }

    @Override
    protected void apply(@NotNull List<? extends UnifiedDiffChange> changes) {
      for (UnifiedDiffChange change : changes) {
        replaceChange(change, myModifiedSide.other());
      }
    }
  }

  private class AppendSelectedChangesAction extends ApplySelectedChangesActionBase {
    AppendSelectedChangesAction(@NotNull Side focusedSide) {
      super(focusedSide.other());

      copyShortcutFrom(ActionManager.getInstance().getAction(focusedSide.select("Diff.AppendLeftSide", "Diff.AppendRightSide")));
      getTemplatePresentation().setText(DiffBundle.messagePointer("action.presentation.diff.append.text"));
      getTemplatePresentation().setIcon(DiffUtil.getArrowDownIcon(focusedSide));
    }

    @Override
    protected void apply(@NotNull List<? extends UnifiedDiffChange> changes) {
      for (UnifiedDiffChange change : changes) {
        appendChange(change, myModifiedSide.other());
      }
    }
  }

  @RequiresWriteLock
  public void replaceChange(@NotNull UnifiedDiffChange change, @NotNull Side sourceSide) {
    Side outputSide = sourceSide.other();

    Document document1 = getDocument(Side.LEFT);
    Document document2 = getDocument(Side.RIGHT);

    LineFragment lineFragment = change.getLineFragment();

    boolean isLastWithLocal = DiffUtil.isUserDataFlagSet(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, myContext);
    boolean isLocalChangeRevert = sourceSide == Side.LEFT && isLastWithLocal;
    TextDiffViewerUtil.applyModification(outputSide.select(document1, document2),
                                         outputSide.getStartLine(lineFragment), outputSide.getEndLine(lineFragment),
                                         sourceSide.select(document1, document2),
                                         sourceSide.getStartLine(lineFragment), sourceSide.getEndLine(lineFragment),
                                         isLocalChangeRevert);

    // no need to mark myStateIsOutOfDate - it will be made by DocumentListener
    // TODO: we can apply change manually, without marking state out-of-date. But we'll have to schedule rediff anyway.
  }

  @RequiresWriteLock
  public void appendChange(@NotNull UnifiedDiffChange change, final @NotNull Side sourceSide) {
    Side outputSide = sourceSide.other();

    Document document1 = getDocument(Side.LEFT);
    Document document2 = getDocument(Side.RIGHT);

    LineFragment lineFragment = change.getLineFragment();
    if (sourceSide.getStartLine(lineFragment) == sourceSide.getEndLine(lineFragment)) return;

    DiffUtil.applyModification(outputSide.select(document1, document2),
                               outputSide.getEndLine(lineFragment), outputSide.getEndLine(lineFragment),
                               sourceSide.select(document1, document2),
                               sourceSide.getStartLine(lineFragment), sourceSide.getEndLine(lineFragment));
  }

  @RequiresEdt
  protected @NotNull List<UnifiedDiffChange> getSelectedChanges() {
    final BitSet lines = DiffUtil.getSelectedLines(myEditor);
    List<UnifiedDiffChange> changes = ContainerUtil.notNullize(myModel.getDiffChanges());
    return ContainerUtil.filter(changes, change -> isChangeSelected(change, lines));
  }

  private static boolean isChangeSelected(@NotNull UnifiedDiffChange change, @NotNull BitSet lines) {
    return DiffUtil.isSelectedByLine(lines, change.getLine1(), change.getLine2());
  }

  //
  // Impl
  //


  public @NotNull TextDiffSettings getTextSettings() {
    return TextDiffViewerUtil.getTextSettings(myContext);
  }

  public @NotNull FoldingModelSupport.Settings getFoldingModelSettings() {
    return TextDiffViewerUtil.getFoldingModelSettings(myContext);
  }

  public @NotNull FoldingModelSupport getFoldingModel() {
    return myFoldingModel;
  }

  @RequiresEdt
  public void scrollToLine(@NotNull Side side, int line) {
    int onesideLine = transferLineToOneside(side, line);
    DiffUtil.scrollEditor(myEditor, onesideLine, 0, false);
  }

  @RequiresEdt
  public boolean scrollToChange(@NotNull ScrollToPolicy scrollToChangePolicy) {
    List<UnifiedDiffChange> changes = myModel.getDiffChanges();
    if (changes == null) return false;

    UnifiedDiffChange targetChange = scrollToChangePolicy.select(ContainerUtil.filter(changes, it -> !it.isSkipped()));
    if (targetChange == null) targetChange = scrollToChangePolicy.select(changes);
    if (targetChange == null) return false;

    DiffUtil.scrollEditor(myEditor, targetChange.getLine1(), false);
    return true;
  }

  //
  // Getters
  //


  public @NotNull Side getMasterSide() {
    return myMasterSide;
  }

  public @NotNull EditorEx getEditor() {
    return myEditor;
  }

  @Override
  public @NotNull List<? extends EditorEx> getEditors() {
    return Collections.singletonList(myEditor);
  }

  public @NotNull List<? extends DocumentContent> getContents() {
    //noinspection unchecked,rawtypes
    return (List)myRequest.getContents();
  }

  public @NotNull DocumentContent getContent(@NotNull Side side) {
    return side.select(getContents());
  }

  public @NotNull DocumentContent getContent1() {
    return getContent(Side.LEFT);
  }

  public @NotNull DocumentContent getContent2() {
    return getContent(Side.RIGHT);
  }

  public @Nullable List<UnifiedDiffChange> getDiffChanges() {
    return myModel.getDiffChanges();
  }

  @ApiStatus.Internal
  public @Nullable List<Range> getLineNumberMapping(@NotNull Side side) {
    LineNumberConvertor convertor = myModel.getLineNumberConvertor(side);
    return convertor != null ? convertor.getRanges() : null;
  }

  private @NotNull List<UnifiedDiffChange> getNonSkippedDiffChanges() {
    return ContainerUtil.filter(ContainerUtil.notNullize(getDiffChanges()), it -> !it.isSkipped());
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    if (!myPanel.isGoodContent()) return null;
    return myEditor.getContentComponent();
  }

  @Override
  protected @NotNull StatusPanel getStatusPanel() {
    return myStatusPanel;
  }

  @RequiresEdt
  public boolean isEditable(@NotNull Side side, boolean respectReadOnlyLock) {
    if (myReadOnlyLockSet && respectReadOnlyLock) return false;
    if (side.select(myForceReadOnlyFlags)) return false;
    return DiffUtil.canMakeWritable(getDocument(side));
  }

  public @NotNull Document getDocument(@NotNull Side side) {
    return getContent(side).getDocument();
  }

  public boolean isStateIsOutOfDate() {
    return myStateIsOutOfDate;
  }

  //
  // Misc
  //

  @Override
  public @Nullable Navigatable getNavigatable() {
    return getNavigatable(LineCol.fromCaret(myEditor));
  }

  @RequiresEdt
  protected @Nullable UnifiedDiffChange getCurrentChange() {
    List<UnifiedDiffChange> changes = myModel.getDiffChanges();
    if (changes == null) return null;

    int caretLine = myEditor.getCaretModel().getLogicalPosition().line;

    for (UnifiedDiffChange change : changes) {
      if (DiffUtil.isSelectedByLine(caretLine, change.getLine1(), change.getLine2())) return change;
    }
    return null;
  }

  @RequiresEdt
  protected @Nullable Navigatable getNavigatable(@NotNull LineCol position) {
    Pair<int[], Side> pair = transferLineFromOneside(position.line);
    int line1 = pair.first[0];
    int line2 = pair.first[1];

    Navigatable navigatable1 = getContent1().getNavigatable(new LineCol(line1, position.column));
    Navigatable navigatable2 = getContent2().getNavigatable(new LineCol(line2, position.column));
    if (navigatable1 == null) return navigatable2;
    if (navigatable2 == null) return navigatable1;
    return pair.second.select(navigatable1, navigatable2);
  }

  public boolean isContentGood() {
    return myPanel.isGoodContent() && myModel.isValid();
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return TwosideTextDiffViewer.canShowRequest(context, request);
  }

  //
  // Actions
  //

  private class MyPrevNextDifferenceIterable extends PrevNextDifferenceIterableBase<UnifiedDiffChange> {
    @Override
    protected @NotNull List<UnifiedDiffChange> getChanges() {
      return getNonSkippedDiffChanges();
    }

    @Override
    protected @NotNull EditorEx getEditor() {
      return myEditor;
    }

    @Override
    protected int getStartLine(@NotNull UnifiedDiffChange change) {
      return change.getLine1();
    }

    @Override
    protected int getEndLine(@NotNull UnifiedDiffChange change) {
      return change.getLine2();
    }
  }

  private class MyOpenInEditorWithMouseAction extends OpenInEditorWithMouseAction {
    @Override
    protected Navigatable getNavigatable(@NotNull Editor editor, int line) {
      if (editor != myEditor) return null;

      return UnifiedDiffViewer.this.getNavigatable(new LineCol(line));
    }
  }

  private class MyToggleExpandByDefaultAction extends TextDiffViewerUtil.ToggleExpandByDefaultAction {
    MyToggleExpandByDefaultAction() {
      super(getTextSettings(), myFoldingModel);
    }
  }

  private class MyReadOnlyLockAction extends TextDiffViewerUtil.ReadOnlyLockAction {
    MyReadOnlyLockAction() {
      super(getContext());
      applyDefaults();
    }

    @Override
    protected void doApply(boolean readOnly) {
      myReadOnlyLockSet = readOnly;
      myModel.updateGutterActions();
      updateEditorCanBeTyped();
      putEditorHint(myEditor, readOnly && isEditable(myMasterSide, false));
    }

    @Override
    protected boolean canEdit() {
      return !myForceReadOnlyFlags[0] && DiffUtil.canMakeWritable(getContent1().getDocument()) ||
             !myForceReadOnlyFlags[1] && DiffUtil.canMakeWritable(getContent2().getDocument());
    }
  }

  //
  // Scroll from annotate
  //

  private final class ChangedLinesIterator extends BufferedLineIterator {
    private final @NotNull List<? extends UnifiedDiffChange> myChanges;

    private int myIndex = 0;

    private ChangedLinesIterator(@NotNull List<? extends UnifiedDiffChange> changes) {
      myChanges = changes;
      init();
    }

    @Override
    public boolean hasNextBlock() {
      return myIndex < myChanges.size();
    }

    @Override
    public void loadNextBlock() {
      LOG.assertTrue(!myStateIsOutOfDate);

      UnifiedDiffChange change = myChanges.get(myIndex);
      myIndex++;

      LineFragment lineFragment = change.getLineFragment();
      Document document = getContent2().getDocument();

      for (int lineNumber = lineFragment.getStartLine2(); lineNumber < lineFragment.getEndLine2(); lineNumber++) {
        int offset1 = document.getLineStartOffset(lineNumber);
        int offset2 = document.getLineEndOffset(lineNumber);
        CharSequence line = document.getImmutableCharSequence().subSequence(offset1, offset2);
        addLine(lineNumber, line);
      }
    }
  }

  //
  // Helpers
  //

  @ApiStatus.Internal
  @Override
  public @Nullable PrevNextDifferenceIterable getDifferenceIterable() {
    return myPrevNextDifferenceIterable;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    UnifiedDiffChange change = getCurrentChange();
    sink.set(DiffDataKeys.CURRENT_EDITOR, myEditor);

    // the editor to navigate in, as TextEditorHolder sets it for a side-by-side editor:
    // a target in the file of the caret side lands in the unified editor through the projection
    UnifiedSideEditor sideEditor = getSideEditor(myEditor.getCaretModel().getOffset());
    if (sideEditor != null) sink.set(OpenFileDescriptor.NAVIGATE_IN_EDITOR, sideEditor);

    if (change != null) {
      sink.set(DiffDataKeys.CURRENT_CHANGE_RANGE, new LineRange(change.getLine1(), change.getLine2()));
    }
    sink.set(DiffDataKeys.EDITOR_CHANGED_RANGE_PROVIDER, new MyChangedRangeProvider());

    sink.set(PlatformCoreDataKeys.FILE_EDITOR,
             TextEditorProvider.getInstance().getTextEditor(myEditor));

    sink.set(DiffDataKeys.DIFF_VIEWER, this);
  }

  @Override
  public @NotNull List<? extends Editor> getHighlightEditors() {
    if (myProject == null) return Collections.emptyList();

    List<Editor> result = new ArrayList<>();
    result.add(myEditor);
    if (mySideEditors != null) result.addAll(mySideEditors);
    return result;
  }

  private @Nullable List<UnifiedSideEditor> createSideEditors() {
    if (myProject == null) return null;
    if (!Registry.is("diff.unified.enable.imaginary.editor")) return null;
    return List.of(new UnifiedSideEditor(this, myProject, Side.LEFT), new UnifiedSideEditor(this, myProject, Side.RIGHT));
  }

  /**
   * The editor of the side that shows the line at the offset of the unified document.
   * A line of both sides belongs to the master side.
   * Returns null while the unified document is out of date, and for a line that is on no side.
   */
  private @Nullable UnifiedSideEditor getSideEditor(int onesideOffset) {
    if (mySideEditors == null) return null;
    if (myStateIsOutOfDate || !myModel.isValid()) return null;

    Pair<int[], Side> pair = transferLineFromOnesideStrict(LineCol.fromOffset(myDocument, onesideOffset).line);
    if (pair == null) return null;
    return pair.second.select(mySideEditors);
  }

  private class MyStatusPanel extends StatusPanel {
    @Override
    protected @Nullable String getMessage() {
      return getStatusTextMessage();
    }
  }

  protected @Nullable @Nls String getStatusTextMessage() {
    ChangedBlockData blockData = myModel.getData();
    if (blockData == null) return null;

    List<UnifiedDiffChange> allChanges = blockData.getDiffChanges();
    return DiffUtil.getStatusText(allChanges.size(),
                                  ContainerUtil.count(allChanges, it -> it.isExcluded()),
                                  myModel.isContentsEqual());
  }

  private class MyInitialScrollHelper extends InitialScrollPositionSupport.TwosideInitialScrollHelper {
    @Override
    protected @NotNull List<? extends Editor> getEditors() {
      return UnifiedDiffViewer.this.getEditors();
    }

    @Override
    protected void disableSyncScroll(boolean value) {
    }

    @Override
    public void onSlowRediff() {
      // Will not happen for initial rediff
    }

    @Override
    protected LogicalPosition @Nullable [] getCaretPositions() {
      LogicalPosition position = myEditor.getCaretModel().getLogicalPosition();
      Pair<int[], Side> pair = transferLineFromOneside(position.line);
      LogicalPosition[] carets = new LogicalPosition[2];
      carets[0] = getPosition(pair.first[0], position.column);
      carets[1] = getPosition(pair.first[1], position.column);
      return carets;
    }

    @Override
    protected boolean doScrollToPosition() {
      if (myCaretPosition == null) return false;

      LogicalPosition twosidePosition = myMasterSide.selectNotNull(myCaretPosition);
      int onesideLine = transferLineToOneside(myMasterSide, twosidePosition.line);
      LogicalPosition position = new LogicalPosition(onesideLine, twosidePosition.column);

      myEditor.getCaretModel().moveToLogicalPosition(position);

      if (myEditorsPosition != null && myEditorsPosition.isSame(position)) {
        DiffUtil.scrollToPoint(myEditor, myEditorsPosition.myPoints[0], false);
      }
      else {
        DiffUtil.scrollToCaret(myEditor, false);
      }
      return true;
    }

    private static @NotNull LogicalPosition getPosition(int line, int column) {
      if (line == -1) return new LogicalPosition(0, 0);
      return new LogicalPosition(line, column);
    }

    @Override
    protected boolean doScrollToLine(boolean onSlowRediff) {
      if (myScrollToLine == null) return false;
      scrollToLine(myScrollToLine.first, myScrollToLine.second);
      return true;
    }

    @Override
    protected boolean doScrollToChange() {
      if (myScrollToChange == null) return false;
      return scrollToChange(myScrollToChange);
    }

    @Override
    protected boolean doScrollToFirstChange() {
      return scrollToChange(ScrollToPolicy.FIRST_CHANGE);
    }

    @Override
    protected boolean doScrollToContext() {
      if (myNavigationContext == null) return false;

      List<UnifiedDiffChange> changes = myModel.getDiffChanges();
      if (changes == null) return false;

      ChangedLinesIterator changedLinesIterator = new ChangedLinesIterator(changes);
      int line = myNavigationContext.contextMatchCheck(changedLinesIterator);
      if (line == -1) {
        // this will work for the case, when spaces changes are ignored, and corresponding fragments are not reported as changed
        // just try to find target line  -> +-
        AllLinesIterator allLinesIterator = new AllLinesIterator(getContent2().getDocument());
        line = myNavigationContext.contextMatchCheck(allLinesIterator);
      }
      if (line == -1) return false;

      scrollToLine(Side.RIGHT, line);
      return true;
    }
  }

  private static class MyReadonlyFragmentModificationHandler implements ReadonlyFragmentModificationHandler {
    @Override
    public void handle(ReadOnlyFragmentModificationException e) {
      // do nothing
    }
  }

  private final class MarkupUpdater implements Disposable {
    private final @NotNull MergingUpdateQueue myUpdateQueue =
      new MergingUpdateQueue("UnifiedDiffViewer.MarkupUpdater", 300, true, myPanel, this);

    private volatile @NotNull ProgressIndicator myUpdateIndicator = new EmptyProgressIndicator();
    private volatile boolean mySuspended;

    private MarkupUpdater(@NotNull List<? extends DocumentContent> contents) {
      Disposer.register(UnifiedDiffViewer.this, this);

      ApplicationManager.getApplication().getMessageBus().connect(this)
        .subscribe(EditorColorsManager.TOPIC, scheme -> resetMarkup());

      MyMarkupModelListener markupListener = new MyMarkupModelListener();
      for (DocumentContent content : contents) {
        Document document = content.getDocument();
        MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myProject, true);
        model.addMarkupModelListener(this, markupListener);
      }
    }

    @Override
    public void dispose() {
      myUpdateIndicator.cancel();
    }

    @RequiresEdt
    public void suspendUpdate() {
      myUpdateIndicator.cancel();
      myUpdateQueue.cancelAllUpdates();
      mySuspended = true;
    }

    @RequiresEdt
    public void resumeUpdate() {
      mySuspended = false;
      scheduleUpdate();
    }

    public void resetMarkup() {
      // erase old highlighting to make sure text is readable if markup update is slow
      myEditor.setHighlighter(DiffUtil.createEmptyEditorHighlighter());
      UnifiedEditorRangeHighlighter.erase(myProject, myEditor.getDocument());

      scheduleUpdate();

      // NB: flush request might be overwritten by another 'scheduleUpdate()'
      // Ex: if XLineBreakpointImpl is updating its markers after us, triggering our MarkupModelListener
      // This will delay re-highlighting by 300ms causing blinking
      myUpdateQueue.sendFlush();
    }

    void scheduleUpdate() {
      if (myProject == null) return;
      if (mySuspended) return;
      myUpdateIndicator.cancel();
      myUpdateQueue.queue(new Update("update") {
        @Override
        public void run() {
          if (!UIUtil.isShowing(getComponent())) return;
          if (myStateIsOutOfDate || !myModel.isValid()) return;

          myUpdateIndicator.cancel();
          myUpdateIndicator = new EmptyProgressIndicator();

          ChangedBlockData blockData = Objects.requireNonNull(myModel.getData());

          ReadAction
            .nonBlocking(() -> updateHighlighters(blockData))
            .finishOnUiThread(ModalityState.stateForComponent(myPanel), result -> {
              if (myStateIsOutOfDate || blockData != myModel.getData()) return;

              UnifiedDiffHighlightersData.apply(myProject, myEditor, result);
            })
            .withDocumentsCommitted(myProject)
            .wrapProgress(myUpdateIndicator)
            .submit(NonUrgentExecutor.getInstance());
        }
      });
    }

    private @NotNull UnifiedDiffHighlightersData updateHighlighters(@NotNull ChangedBlockData blockData) {
      List<HighlightRange> ranges = blockData.getRanges();
      Document document1 = getContent1().getDocument();
      Document document2 = getContent2().getDocument();

      ProgressManager.checkCanceled();
      EditorHighlighter highlighter = UnifiedEditorHighlighter.buildHighlighter(myProject, myDocument, getContent1(), getContent2(),
                                                       document1.getCharsSequence(), document2.getCharsSequence(), ranges,
                                                       myDocument.getTextLength());

      ProgressManager.checkCanceled();
      UnifiedEditorRangeHighlighter rangeHighlighter = new UnifiedEditorRangeHighlighter(myProject, document1, document2, ranges);

      return new UnifiedDiffHighlightersData(highlighter, rangeHighlighter);
    }

    private class MyMarkupModelListener implements MarkupModelListener {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        scheduleUpdate();
      }

      @Override
      public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
        scheduleUpdate();
      }

      @Override
      public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {
        scheduleUpdate();
      }
    }
  }

  private final class UnifiedBreadcrumbsPanel extends DiffBreadcrumbsPanel {
    private final @Nullable VirtualFile myFile1;
    private final @Nullable VirtualFile myFile2;

    // effectively nullable: The 'com.intellij.xml.breadcrumbs.BreadcrumbsPanel.<init>' may synchronously execute update in tests
    private final @Nullable DiffBreadcrumbCollectorHolder myCollectorHolder1;
    private final @Nullable DiffBreadcrumbCollectorHolder myCollectorHolder2;

    private UnifiedBreadcrumbsPanel() {
      super(getEditor(), UnifiedDiffViewer.this);

      myFile1 = FileDocumentManager.getInstance().getFile(getDocument(Side.LEFT));
      myFile2 = FileDocumentManager.getInstance().getFile(getDocument(Side.RIGHT));

      myCollectorHolder1 = new DiffBreadcrumbCollectorHolder();
      myCollectorHolder2 = new DiffBreadcrumbCollectorHolder();
    }

    @Override
    protected boolean updateCollectors(boolean enabled) {
      // null check: The 'com.intellij.xml.breadcrumbs.BreadcrumbsPanel.<init>' may synchronously execute update in tests
      boolean update1 = myCollectorHolder1 != null && myCollectorHolder1.update(myFile1, enabled);
      boolean update2 = myCollectorHolder2 != null && myCollectorHolder2.update(myFile2, enabled);
      return update1 || update2;
    }

    @Override
    protected @Nullable Iterable<? extends Crumb> computeCrumbs(int offset) {
      Pair<Integer, Side> pair = transferOffsetFromOneside(offset);
      if (pair == null) return null;

      Side side = pair.second;
      int twosideOffset = pair.first;

      VirtualFile file = side.select(myFile1, myFile2);
      DiffBreadcrumbCollectorHolder holder = side.select(myCollectorHolder1, myCollectorHolder2);
      FileBreadcrumbsCollector collector = holder != null ? holder.getBreadcrumbsCollector() : null;
      if (file == null || collector == null) return null;

      Iterable<? extends Crumb> crumbs = collector.computeCrumbs(file, getDocument(side), twosideOffset, null);
      return ContainerUtil.map(crumbs, it -> it instanceof NavigatableCrumb ? new UnifiedNavigatableCrumb((NavigatableCrumb)it, side) : it);
    }

    @Override
    protected void navigateToCrumb(Crumb crumb, boolean withSelection) {
      if (crumb instanceof UnifiedNavigatableCrumb) {
        super.navigateToCrumb(crumb, withSelection);
      }
    }


    private final class UnifiedNavigatableCrumb implements NavigatableCrumb {
      private final @NotNull NavigatableCrumb myDelegate;
      private final @NotNull Side mySide;

      private UnifiedNavigatableCrumb(@NotNull NavigatableCrumb delegate, @NotNull Side side) {
        myDelegate = delegate;
        mySide = side;
      }

      @Override
      public int getAnchorOffset() {
        int offset = myDelegate.getAnchorOffset();
        return offset != -1 ? transferOffsetToOneside(mySide, offset) : -1;
      }

      @Override
      public @Nullable TextRange getHighlightRange() {
        TextRange range = myDelegate.getHighlightRange();
        if (range == null) return null;
        int start = transferOffsetToOneside(mySide, range.getStartOffset());
        int end = transferOffsetToOneside(mySide, range.getEndOffset());
        if (start == -1 || end == -1) return null;
        return new TextRange(start, end);
      }

      @Override
      public void navigate(@NotNull Editor editor, boolean withSelection) {
        int offset = getAnchorOffset();
        if (offset != -1) {
          editor.getCaretModel().moveToOffset(offset);
          editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        }

        if (withSelection) {
          final TextRange range = getHighlightRange();
          if (range != null) {
            editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
          }
        }
      }

      @Override
      public Icon getIcon() {
        return myDelegate.getIcon();
      }

      @Override
      public String getText() {
        return myDelegate.getText();
      }

      @Override
      public @Nullable String getTooltip() {
        return myDelegate.getTooltip();
      }

      @Override
      public @NotNull List<? extends Action> getContextActions() {
        return myDelegate.getContextActions();
      }
    }
  }

  private class MyChangedRangeProvider implements DiffChangedRangeProvider {
    @Override
    public @Nullable List<TextRange> getChangedRanges(@NotNull Editor editor) {
      if (editor != myEditor) return null;

      return ContainerUtil.map(getNonSkippedDiffChanges(), change -> {
        return DiffUtil.getLinesRange(editor.getDocument(), change.getLine1(), change.getLine2());
      });
    }
  }
}
