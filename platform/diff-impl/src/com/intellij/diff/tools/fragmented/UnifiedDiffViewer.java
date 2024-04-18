// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.diff.DiffContext;
import com.intellij.diff.EditorDiffViewer;
import com.intellij.diff.actions.AllLinesIterator;
import com.intellij.diff.actions.BufferedLineIterator;
import com.intellij.diff.actions.impl.OpenInEditorWithMouseAction;
import com.intellij.diff.actions.impl.SetEditorSettingsAction;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.fragmented.UnifiedDiffModel.ChangedBlockData;
import com.intellij.diff.tools.util.*;
import com.intellij.diff.tools.util.base.InitialScrollPositionSupport;
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.breadcrumbs.DiffBreadcrumbsPanel;
import com.intellij.diff.tools.util.side.OnesideContentPanel;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.tools.util.text.TwosideTextDiffProvider;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.*;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.textarea.EmptyInlayModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
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
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xml.breadcrumbs.NavigatableCrumb;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

public class UnifiedDiffViewer extends ListenerDiffViewerBase implements EditorDiffViewer {
  @NotNull protected final EditorEx myEditor;
  @NotNull protected final Document myDocument;
  @NotNull protected final UnifiedDiffPanel myPanel;
  @NotNull private final OnesideContentPanel myContentPanel;

  @NotNull private final SetEditorSettingsAction myEditorSettingsAction;
  @NotNull private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final MyStatusPanel myStatusPanel;

  @NotNull private final MyInitialScrollHelper myInitialScrollHelper = new MyInitialScrollHelper();
  @NotNull private final MyFoldingModel myFoldingModel;
  @NotNull private final MarkupUpdater myMarkupUpdater;

  @NotNull protected final TwosideTextDiffProvider.NoIgnore myTextDiffProvider;

  @NotNull protected Side myMasterSide = Side.RIGHT;

  @NotNull protected final UnifiedDiffModel myModel = new UnifiedDiffModel(this);

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

    myFoldingModel = new MyFoldingModel(getProject(), myEditor, this);
    myMarkupUpdater = new MarkupUpdater(getContents());

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), getEditors());
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

    DiffUtil.installShowNotifyListener(getComponent(), () -> myMarkupUpdater.scheduleUpdate());
  }

  @Override
  @RequiresEdt
  protected void onDispose() {
    super.onDispose();
    EditorFactory.getInstance().releaseEditor(myEditor);
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

  @Nullable
  protected JComponent createTitles() {
    List<JComponent> titles = DiffUtil.createTextTitles(this, myRequest, Arrays.asList(myEditor, myEditor));
    assert titles.size() == 2;

    titles = ContainerUtil.skipNulls(titles);
    if (titles.isEmpty()) return null;

    return DiffUtil.createStackedComponents(titles, DiffUtil.TITLE_GAP);
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

    myDocument.addDocumentListener(new MyOnesideDocumentListener());
  }

  @NotNull
  @Override
  @RequiresEdt
  public List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<>(myTextDiffProvider.getToolbarActions());
    group.add(new MyToggleExpandByDefaultAction());
    group.add(new MyReadOnlyLockAction());
    group.add(myEditorSettingsAction);

    group.add(Separator.getInstance());
    group.addAll(super.createToolbarActions());

    return group;
  }

  @NotNull
  @Override
  @RequiresEdt
  public List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<>(myTextDiffProvider.getPopupActions());
    group.add(new MyToggleExpandByDefaultAction());

    group.add(Separator.getInstance());
    group.addAll(super.createPopupActions());

    return group;
  }

  @NotNull
  protected List<AnAction> createEditorPopupActions() {
    List<AnAction> group = new ArrayList<>();

    group.add(new ReplaceSelectedChangesAction(Side.LEFT));
    group.add(new ReplaceSelectedChangesAction(Side.RIGHT));
    group.add(Separator.getInstance());
    group.addAll(TextDiffViewerUtil.createEditorPopupActions());

    return group;
  }

  @RequiresEdt
  protected void installEditorListeners() {
    new TextDiffViewerUtil.EditorActionsPopup(createEditorPopupActions()).install(getEditors(), myPanel);
  }

  @NotNull
  protected UnifiedDiffChangeUi createUi(@NotNull UnifiedDiffChange change) {
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
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
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

  @NotNull
  protected Runnable applyErrorNotification() {
    return () -> {
      clearDiffPresentation();
      myPanel.setErrorContent();
    };
  }

  @NotNull
  protected Runnable computeDifferences(@NotNull ProgressIndicator indicator) {
    final Document document1 = getContent1().getDocument();
    final Document document2 = getContent2().getDocument();

    final CharSequence[] texts = ReadAction.compute(
      () -> new CharSequence[]{document1.getImmutableCharSequence(), document2.getImmutableCharSequence()});

    final List<LineFragment> fragments = myTextDiffProvider.compare(texts[0], texts[1], indicator);

    UnifiedDiffState builder = ReadAction.compute(() -> {
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

  @Nullable
  private static EditorHighlighter buildHighlighter(@Nullable Project project,
                                                    @NotNull Document document,
                                                    @NotNull DocumentContent content1,
                                                    @NotNull DocumentContent content2,
                                                    @NotNull CharSequence text1,
                                                    @NotNull CharSequence text2,
                                                    @NotNull List<HighlightRange> ranges,
                                                    int textLength) {
    EditorHighlighter highlighter1 = DiffUtil.initEditorHighlighter(project, content1, text1);
    EditorHighlighter highlighter2 = DiffUtil.initEditorHighlighter(project, content2, text2);

    if (highlighter1 == null && highlighter2 == null) return null;
    if (highlighter1 == null) highlighter1 = DiffUtil.initEmptyEditorHighlighter(text1);
    if (highlighter2 == null) highlighter2 = DiffUtil.initEmptyEditorHighlighter(text2);

    return new UnifiedEditorHighlighter(document, highlighter1, highlighter2, ranges, textLength);
  }

  @NotNull
  protected Runnable apply(@NotNull UnifiedDiffState builder,
                           CharSequence @NotNull [] texts,
                           @NotNull ProgressIndicator indicator) {
    final DocumentContent content1 = getContent1();
    final DocumentContent content2 = getContent2();

    HighlightersData highlightersData = BackgroundTaskUtil.tryComputeFast(___ -> {
      return ReadAction.compute(() -> {
        EditorHighlighter highlighter =
          buildHighlighter(myProject, myDocument, content1, content2,
                           texts[0], texts[1], builder.getRanges(),
                           builder.getText().length());
        UnifiedEditorRangeHighlighter rangeHighlighter =
          new UnifiedEditorRangeHighlighter(myProject, content1.getDocument(),
                                            content2.getDocument(), builder.getRanges());
        return new HighlightersData(highlighter, rangeHighlighter);
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

      HighlightersData.apply(myProject, myEditor, highlightersData);
      myMarkupUpdater.resumeUpdate();

      myInitialScrollHelper.onRediff();

      myStatusPanel.update();
      myPanel.setGoodContent();

      myEditor.getGutterComponentEx().revalidateMarkup();
    };
  }

  @NotNull
  private RangeMarker createGuardedBlock(int start, int end) {
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
  @NotNull
  public Pair<int[], @NotNull Side> transferLineFromOneside(int line) {
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
    @NotNull protected final Side myModifiedSide;

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
    public void actionPerformed(@NotNull final AnActionEvent e) {
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
  public void appendChange(@NotNull UnifiedDiffChange change, @NotNull final Side sourceSide) {
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

  @NotNull
  @RequiresEdt
  protected List<UnifiedDiffChange> getSelectedChanges() {
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


  @NotNull
  public TextDiffSettings getTextSettings() {
    return TextDiffViewerUtil.getTextSettings(myContext);
  }

  @NotNull
  public FoldingModelSupport.Settings getFoldingModelSettings() {
    return TextDiffViewerUtil.getFoldingModelSettings(myContext);
  }

  @NotNull
  public FoldingModelSupport getFoldingModel() {
    return myFoldingModel;
  }

  //
  // Getters
  //


  @NotNull
  public Side getMasterSide() {
    return myMasterSide;
  }

  @NotNull
  public EditorEx getEditor() {
    return myEditor;
  }

  @NotNull
  @Override
  public List<? extends EditorEx> getEditors() {
    return Collections.singletonList(myEditor);
  }

  @NotNull
  public List<? extends DocumentContent> getContents() {
    //noinspection unchecked,rawtypes
    return (List)myRequest.getContents();
  }

  @NotNull
  public DocumentContent getContent(@NotNull Side side) {
    return side.select(getContents());
  }

  @NotNull
  public DocumentContent getContent1() {
    return getContent(Side.LEFT);
  }

  @NotNull
  public DocumentContent getContent2() {
    return getContent(Side.RIGHT);
  }

  @Nullable
  public List<UnifiedDiffChange> getDiffChanges() {
    return myModel.getDiffChanges();
  }

  @NotNull
  private List<UnifiedDiffChange> getNonSkippedDiffChanges() {
    return ContainerUtil.filter(ContainerUtil.notNullize(getDiffChanges()), it -> !it.isSkipped());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    if (!myPanel.isGoodContent()) return null;
    return myEditor.getContentComponent();
  }

  @NotNull
  @Override
  protected StatusPanel getStatusPanel() {
    return myStatusPanel;
  }

  @RequiresEdt
  public boolean isEditable(@NotNull Side side, boolean respectReadOnlyLock) {
    if (myReadOnlyLockSet && respectReadOnlyLock) return false;
    if (side.select(myForceReadOnlyFlags)) return false;
    return DiffUtil.canMakeWritable(getDocument(side));
  }

  @NotNull
  public Document getDocument(@NotNull Side side) {
    return getContent(side).getDocument();
  }

  public boolean isStateIsOutOfDate() {
    return myStateIsOutOfDate;
  }

  //
  // Misc
  //

  @Nullable
  @Override
  public Navigatable getNavigatable() {
    return getNavigatable(LineCol.fromCaret(myEditor));
  }

  @RequiresEdt
  @Nullable
  protected UnifiedDiffChange getCurrentChange() {
    List<UnifiedDiffChange> changes = myModel.getDiffChanges();
    if (changes == null) return null;

    int caretLine = myEditor.getCaretModel().getLogicalPosition().line;

    for (UnifiedDiffChange change : changes) {
      if (DiffUtil.isSelectedByLine(caretLine, change.getLine1(), change.getLine2())) return change;
    }
    return null;
  }

  @RequiresEdt
  @Nullable
  protected Navigatable getNavigatable(@NotNull LineCol position) {
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
    @NotNull
    @Override
    protected List<UnifiedDiffChange> getChanges() {
      return getNonSkippedDiffChanges();
    }

    @NotNull
    @Override
    protected EditorEx getEditor() {
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
    @NotNull private final List<? extends UnifiedDiffChange> myChanges;

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

  @Override
  public @Nullable PrevNextDifferenceIterable getDifferenceIterable() {
    return myPrevNextDifferenceIterable;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    UnifiedDiffChange change = getCurrentChange();
    sink.set(DiffDataKeys.CURRENT_EDITOR, myEditor);
    if (change != null) {
      sink.set(DiffDataKeys.CURRENT_CHANGE_RANGE, new LineRange(change.getLine1(), change.getLine2()));
    }
    sink.set(DiffDataKeys.EDITOR_CHANGED_RANGE_PROVIDER, new MyChangedRangeProvider());
  }

  @Override
  public @NotNull List<? extends Editor> getHighlightEditors() {
    if (myProject == null) return Collections.emptyList();

    return ReadAction.compute(() -> {
      List<Editor> result = new ArrayList<>();
      result.add(myEditor);
      ContainerUtil.addIfNotNull(result, createImaginaryEditor(Side.LEFT));
      ContainerUtil.addIfNotNull(result, createImaginaryEditor(Side.RIGHT));
      return result;
    });
  }

  private @Nullable Editor createImaginaryEditor(@NotNull Side side) {
    if (myProject == null) return null;
    if (UnifiedImaginaryEditor.ourDisableImaginaryEditor) return null;
    if (!Registry.is("diff.unified.enable.imaginary.editor")) return null;

    int caretOffset = myEditor.getCaretModel().getOffset();
    LineCol caretPosition = LineCol.fromOffset(myDocument, caretOffset);

    Document sideDocument = getDocument(side);
    ImaginaryEditor editor = new UnifiedImaginaryEditor(myProject, sideDocument, side);

    int sideLine = transferLineFromOnesideStrict(side, caretPosition.line);
    if (sideLine != -1) {
      editor.getCaretModel().moveToOffset(LineCol.toOffset(sideDocument, sideLine, caretPosition.column));
    }
    return editor;
  }

  private class MyStatusPanel extends StatusPanel {
    @Nullable
    @Override
    protected String getMessage() {
      return getStatusTextMessage();
    }
  }

  @Nullable
  protected @Nls String getStatusTextMessage() {
    ChangedBlockData blockData = myModel.getData();
    if (blockData == null) return null;

    List<UnifiedDiffChange> allChanges = blockData.getDiffChanges();
    return DiffUtil.getStatusText(allChanges.size(),
                                  ContainerUtil.count(allChanges, it -> it.isExcluded()),
                                  myModel.isContentsEqual());
  }

  private class MyInitialScrollHelper extends InitialScrollPositionSupport.TwosideInitialScrollHelper {
    @NotNull
    @Override
    protected List<? extends Editor> getEditors() {
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

    @NotNull
    private static LogicalPosition getPosition(int line, int column) {
      if (line == -1) return new LogicalPosition(0, 0);
      return new LogicalPosition(line, column);
    }

    private void doScrollToLine(@NotNull Side side, @NotNull LogicalPosition position) {
      int onesideLine = transferLineToOneside(side, position.line);
      DiffUtil.scrollEditor(myEditor, onesideLine, position.column, false);
    }

    @Override
    protected boolean doScrollToLine(boolean onSlowRediff) {
      if (myScrollToLine == null) return false;
      doScrollToLine(myScrollToLine.first, new LogicalPosition(myScrollToLine.second, 0));
      return true;
    }

    private boolean doScrollToChange(@NotNull ScrollToPolicy scrollToChangePolicy) {
      List<UnifiedDiffChange> changes = myModel.getDiffChanges();
      if (changes == null) return false;

      UnifiedDiffChange targetChange = scrollToChangePolicy.select(ContainerUtil.filter(changes, it -> !it.isSkipped()));
      if (targetChange == null) targetChange = scrollToChangePolicy.select(changes);
      if (targetChange == null) return false;

      DiffUtil.scrollEditor(myEditor, targetChange.getLine1(), false);
      return true;
    }

    @Override
    protected boolean doScrollToChange() {
      if (myScrollToChange == null) return false;
      return doScrollToChange(myScrollToChange);
    }

    @Override
    protected boolean doScrollToFirstChange() {
      return doScrollToChange(ScrollToPolicy.FIRST_CHANGE);
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

      doScrollToLine(Side.RIGHT, new LogicalPosition(line, 0));
      return true;
    }
  }

  private static class MyFoldingModel extends FoldingModelSupport {
    @NotNull private DisposableLineNumberConvertor myLineNumberConvertor = new DisposableLineNumberConvertor(null);

    MyFoldingModel(@Nullable Project project, @NotNull EditorEx editor, @NotNull Disposable disposable) {
      super(project, new EditorEx[]{editor}, disposable);
    }

    @Nullable
    public Data createState(@Nullable List<? extends LineRange> changedLines,
                            @NotNull Settings settings,
                            @NotNull Document document,
                            @NotNull LineNumberConvertor lineConvertor,
                            int lineCount) {
      Iterator<int[]> it = map(changedLines, line -> new int[]{
        line.start,
        line.end
      });

      if (it == null || settings.range == -1) return null;

      myLineNumberConvertor = new DisposableLineNumberConvertor(lineConvertor);
      MyFoldingBuilder builder = new MyFoldingBuilder(document, myLineNumberConvertor, lineCount, settings);
      return builder.build(it);
    }

    public void disposeLineConvertor() {
      myLineNumberConvertor.dispose();
    }

    private static final class MyFoldingBuilder extends FoldingBuilderBase {
      @NotNull private final Document myDocument;
      @NotNull private final DisposableLineNumberConvertor myLineConvertor;

      private MyFoldingBuilder(@NotNull Document document,
                               @NotNull DisposableLineNumberConvertor lineConvertor,
                               int lineCount,
                               @NotNull Settings settings) {
        super(new int[]{lineCount}, settings);
        myDocument = document;
        myLineConvertor = lineConvertor;
      }

      @Nullable
      @Override
      protected FoldedRangeDescription getDescription(@NotNull Project project, int lineNumber, int index) {
        int masterLine = myLineConvertor.convert(lineNumber);
        if (masterLine == -1) return null;
        return getLineSeparatorDescription(project, myDocument, masterLine);
      }
    }

    private static final class DisposableLineNumberConvertor {
      @Nullable private volatile LineNumberConvertor myConvertor;

      private DisposableLineNumberConvertor(@Nullable LineNumberConvertor convertor) {
        myConvertor = convertor;
      }

      public int convert(int lineNumber) {
        LineNumberConvertor convertor = myConvertor;
        return convertor != null ? convertor.convert(lineNumber) : -1;
      }

      public void dispose() {
        myConvertor = null;
      }
    }
  }

  private static class MyReadonlyFragmentModificationHandler implements ReadonlyFragmentModificationHandler {
    @Override
    public void handle(ReadOnlyFragmentModificationException e) {
      // do nothing
    }
  }

  private final class MarkupUpdater implements Disposable {
    @NotNull private final MergingUpdateQueue myUpdateQueue =
      new MergingUpdateQueue("UnifiedDiffViewer.MarkupUpdater", 300, true, myPanel, this);

    @NotNull private volatile ProgressIndicator myUpdateIndicator = new EmptyProgressIndicator();
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

              HighlightersData.apply(myProject, myEditor, result);
            })
            .withDocumentsCommitted(myProject)
            .wrapProgress(myUpdateIndicator)
            .submit(NonUrgentExecutor.getInstance());
        }
      });
    }

    @NotNull
    private HighlightersData updateHighlighters(@NotNull ChangedBlockData blockData) {
      List<HighlightRange> ranges = blockData.getRanges();
      Document document1 = getContent1().getDocument();
      Document document2 = getContent2().getDocument();

      ProgressManager.checkCanceled();
      EditorHighlighter highlighter = buildHighlighter(myProject, myDocument, getContent1(), getContent2(),
                                                       document1.getCharsSequence(), document2.getCharsSequence(), ranges,
                                                       myDocument.getTextLength());

      ProgressManager.checkCanceled();
      UnifiedEditorRangeHighlighter rangeHighlighter = new UnifiedEditorRangeHighlighter(myProject, document1, document2, ranges);

      return new HighlightersData(highlighter, rangeHighlighter);
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

  private static class HighlightersData {
    @Nullable private final EditorHighlighter myHighlighter;
    @Nullable private final UnifiedEditorRangeHighlighter myRangeHighlighter;

    private HighlightersData(@Nullable EditorHighlighter highlighter,
                             @Nullable UnifiedEditorRangeHighlighter rangeHighlighter) {
      myHighlighter = highlighter;
      myRangeHighlighter = rangeHighlighter;
    }

    public static void apply(@Nullable Project project, @NotNull EditorEx editor, @Nullable HighlightersData highlightersData) {
      EditorHighlighter highlighter = highlightersData != null ? highlightersData.myHighlighter : null;
      UnifiedEditorRangeHighlighter rangeHighlighter = highlightersData != null ? highlightersData.myRangeHighlighter : null;

      if (highlighter != null) {
        editor.setHighlighter(highlighter);
      }
      else {
        editor.setHighlighter(DiffUtil.createEmptyEditorHighlighter());
      }

      UnifiedEditorRangeHighlighter.erase(project, editor.getDocument());
      if (rangeHighlighter != null) {
        rangeHighlighter.apply(project, editor.getDocument());
      }
    }
  }

  private final class UnifiedBreadcrumbsPanel extends DiffBreadcrumbsPanel {
    private final VirtualFile myFile1;
    private final VirtualFile myFile2;

    private UnifiedBreadcrumbsPanel() {
      super(getEditor(), UnifiedDiffViewer.this);

      myFile1 = FileDocumentManager.getInstance().getFile(getDocument(Side.LEFT));
      myFile2 = FileDocumentManager.getInstance().getFile(getDocument(Side.RIGHT));
    }

    @Override
    protected boolean updateCollectors(boolean enabled) {
      return enabled && (findCollector(myFile1) != null || findCollector(myFile2) != null);
    }

    @Nullable
    @Override
    protected Iterable<? extends Crumb> computeCrumbs(int offset) {
      Pair<Integer, Side> pair = transferOffsetToTwoside(offset);
      if (pair == null) return null;

      Side side = pair.second;
      int twosideOffset = pair.first;

      VirtualFile file = side.select(myFile1, myFile2);
      FileBreadcrumbsCollector collector = side.select(findCollector(myFile1), findCollector(myFile2));
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

    @Nullable
    private Pair<Integer, Side> transferOffsetToTwoside(int offset) {
      LineCol onesidePosition = LineCol.fromOffset(myDocument, offset);

      Pair<int[], Side> pair = transferLineFromOneside(onesidePosition.line);
      Side side = pair.second;
      int twosideLine = side.select(pair.first);
      if (twosideLine == -1) return null;

      Document twosideDocument = getDocument(side);
      LineCol twosidePosition = new LineCol(twosideLine, onesidePosition.column);
      return Pair.create(twosidePosition.toOffset(twosideDocument), side);
    }

    private int transferOffsetFromTwoside(@NotNull Side side, int offset) {
      LineCol twosidePosition = LineCol.fromOffset(getDocument(side), offset);

      int onesideLine = transferLineToOneside(side, twosidePosition.line);
      if (onesideLine == -1) return -1;

      LineCol onesidePosition = new LineCol(onesideLine, twosidePosition.column);
      return onesidePosition.toOffset(myDocument);
    }


    private final class UnifiedNavigatableCrumb implements NavigatableCrumb {
      @NotNull private final NavigatableCrumb myDelegate;
      @NotNull private final Side mySide;

      private UnifiedNavigatableCrumb(@NotNull NavigatableCrumb delegate, @NotNull Side side) {
        myDelegate = delegate;
        mySide = side;
      }

      @Override
      public int getAnchorOffset() {
        int offset = myDelegate.getAnchorOffset();
        return offset != -1 ? transferOffsetFromTwoside(mySide, offset) : -1;
      }

      @Override
      @Nullable
      public TextRange getHighlightRange() {
        TextRange range = myDelegate.getHighlightRange();
        if (range == null) return null;
        int start = transferOffsetFromTwoside(mySide, range.getStartOffset());
        int end = transferOffsetFromTwoside(mySide, range.getEndOffset());
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
      @Nullable
      public String getTooltip() {
        return myDelegate.getTooltip();
      }

      @Override
      @NotNull
      public List<? extends Action> getContextActions() {
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

  private class UnifiedImaginaryEditor extends ImaginaryEditor {
    private static boolean ourDisableImaginaryEditor = false;

    private final Side mySide;

    private UnifiedImaginaryEditor(@NotNull Project project, @NotNull Document document, @NotNull Side side) {
      super(project, document);
      mySide = side;
    }

    @Override
    protected RuntimeException notImplemented() {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourDisableImaginaryEditor = true;
      return new UnsupportedOperationException("Not implemented. UnifiedDiffViewer.UnifiedImaginaryEditor will be disabled.");
    }

    @Override
    public VirtualFile getVirtualFile() {
      return FileDocumentManager.getInstance().getFile(getDocument());
    }

    @Override
    public @NotNull EditorColorsScheme getColorsScheme() {
      return myEditor.getColorsScheme();
    }

    @Override
    public @NotNull ProperTextRange calculateVisibleRange() {
      ChangedBlockData blockData = myModel.getData();
      if (blockData == null) return new ProperTextRange(0, 0);

      ProperTextRange range = myEditor.calculateVisibleRange();
      Document oneSideDocument = UnifiedDiffViewer.this.myDocument;
      int line1 = oneSideDocument.getLineNumber(range.getStartOffset());
      int line2 = oneSideDocument.getLineNumber(range.getEndOffset());

      Document sideDocument = UnifiedDiffViewer.this.getDocument(mySide);
      LineNumberConvertor lineConvertor = blockData.getLineNumberConvertor(mySide);
      int sideLine1 = lineConvertor.convertApproximate(Math.max(0, line1 - 1));
      int sideLine2 = lineConvertor.convertApproximate(Math.min(DiffUtil.getLineCount(sideDocument), line2 + 1));

      TextRange sideRange = DiffUtil.getLinesRange(sideDocument, sideLine1, sideLine2, false);
      return new ProperTextRange(sideRange.getStartOffset(), sideRange.getEndOffset());
    }

    @Override
    public boolean isOneLineMode() {
      return false;
    }

    @Override
    public boolean isViewer() {
      return true;
    }

    @Override
    public @NotNull EditorSettings getSettings() {
      return myEditor.getSettings();
    }

    @Override
    public @NotNull JComponent getComponent() {
      return myEditor.getComponent(); // isShowing() checks
    }

    @Override
    public @NotNull JComponent getContentComponent() {
      return myEditor.getContentComponent(); // isShowing() checks
    }

    @Override
    public @NotNull MarkupModel getMarkupModel() {
      return new EmptyMarkupModel(getDocument());
    }

    @Override
    public @NotNull IndentsModel getIndentsModel() {
      return new EmptyIndentsModel();
    }

    @Override
    public @NotNull InlayModel getInlayModel() {
      return new EmptyInlayModel();
    }

    @Override
    public @NotNull FoldingModel getFoldingModel() {
      return new EmptyFoldingModel();
    }
  }
}
