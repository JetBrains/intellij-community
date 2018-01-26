/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.DiffContext;
import com.intellij.diff.actions.AllLinesIterator;
import com.intellij.diff.actions.BufferedLineIterator;
import com.intellij.diff.actions.impl.OpenInEditorWithMouseAction;
import com.intellij.diff.actions.impl.SetEditorSettingsAction;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.*;
import com.intellij.diff.tools.util.base.InitialScrollPositionSupport;
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.tools.util.text.TwosideTextDiffProvider;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;

import static com.intellij.diff.util.DiffUtil.getLinesContent;

public class UnifiedDiffViewer extends ListenerDiffViewerBase {
  @NotNull protected final EditorEx myEditor;
  @NotNull protected final Document myDocument;
  @NotNull private final UnifiedDiffPanel myPanel;

  @NotNull private final SetEditorSettingsAction myEditorSettingsAction;
  @NotNull private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final MyStatusPanel myStatusPanel;

  @NotNull private final MyInitialScrollHelper myInitialScrollHelper = new MyInitialScrollHelper();
  @NotNull private final MyFoldingModel myFoldingModel;

  @NotNull private final TwosideTextDiffProvider.NoIgnore myTextDiffProvider;

  @NotNull protected Side myMasterSide = Side.RIGHT;

  @Nullable private ChangedBlockData myChangedBlockData;

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

    List<JComponent> titles = DiffUtil.createTextTitles(myRequest, ContainerUtil.list(myEditor, myEditor));
    UnifiedContentPanel contentPanel = new UnifiedContentPanel(titles, myEditor);

    myPanel = new UnifiedDiffPanel(myProject, contentPanel, this, myContext);

    myFoldingModel = new MyFoldingModel(myEditor, this);

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), getEditors());
    myEditorSettingsAction.applyDefaults();

    myTextDiffProvider = DiffUtil.createNoIgnoreTextDiffProvider(getProject(), getRequest(), getTextSettings(), this::rediff, this);

    new MyOpenInEditorWithMouseAction().install(getEditors());

    TextDiffViewerUtil.checkDifferentDocuments(myRequest);

    DiffUtil.registerAction(new ReplaceSelectedChangesAction(Side.LEFT, true), myPanel);
    DiffUtil.registerAction(new AppendSelectedChangesAction(Side.LEFT, true), myPanel);
    DiffUtil.registerAction(new ReplaceSelectedChangesAction(Side.RIGHT, true), myPanel);
    DiffUtil.registerAction(new AppendSelectedChangesAction(Side.RIGHT, true), myPanel);
  }

  @Override
  @CalledInAwt
  protected void onInit() {
    super.onInit();
    installEditorListeners();
    installTypingSupport();
    myPanel.setLoadingContent(); // We need loading panel only for initial rediff()
    myPanel.setPersistentNotifications(DiffUtil.getCustomNotifications(myContext, myRequest));
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    super.onDispose();
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  @Override
  @CalledInAwt
  protected void processContextHints() {
    super.processContextHints();
    Side side = DiffUtil.getUserData(myRequest, myContext, DiffUserDataKeys.MASTER_SIDE);
    if (side != null) myMasterSide = side;

    myInitialScrollHelper.processContext(myRequest);
  }

  @Override
  @CalledInAwt
  protected void updateContextHints() {
    super.updateContextHints();
    myInitialScrollHelper.updateContext(myRequest);
    myFoldingModel.updateContext(myRequest, getFoldingModelSettings());
  }

  @CalledInAwt
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
  @CalledInAwt
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
  @CalledInAwt
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

    if (isEditable(Side.RIGHT, false)) {
      group.add(new ReplaceSelectedChangesAction(Side.LEFT, false));
      group.add(new ReplaceSelectedChangesAction(Side.RIGHT, false));
    }

    group.add(Separator.getInstance());
    group.addAll(TextDiffViewerUtil.createEditorPopupActions());

    return group;
  }

  @CalledInAwt
  protected void installEditorListeners() {
    new TextDiffViewerUtil.EditorActionsPopup(createEditorPopupActions()).install(getEditors());
  }

  //
  // Diff
  //

  @Override
  @CalledInAwt
  protected void onSlowRediff() {
    super.onSlowRediff();
    myStatusPanel.setBusy(true);
  }

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    try {
      indicator.checkCanceled();

      final Document document1 = getContent1().getDocument();
      final Document document2 = getContent2().getDocument();

      final CharSequence[] texts = ReadAction.compute(() -> {
        return new CharSequence[]{document1.getImmutableCharSequence(), document2.getImmutableCharSequence()};
      });

      final List<LineFragment> fragments = myTextDiffProvider.compare(texts[0], texts[1], indicator);

      final DocumentContent content1 = getContent1();
      final DocumentContent content2 = getContent2();

      indicator.checkCanceled();
      TwosideDocumentData data = ReadAction.compute(() -> {
        indicator.checkCanceled();
        UnifiedFragmentBuilder builder = new UnifiedFragmentBuilder(fragments, document1, document2, myMasterSide);
        builder.exec();

        indicator.checkCanceled();

        EditorHighlighter highlighter = buildHighlighter(myProject, content1, content2,
                                                         texts[0], texts[1], builder.getRanges(),
                                                         builder.getText().length());

        UnifiedEditorRangeHighlighter rangeHighlighter = new UnifiedEditorRangeHighlighter(myProject, document1, document2,
                                                                                           builder.getRanges());

        return new TwosideDocumentData(builder, highlighter, rangeHighlighter);
      });
      UnifiedFragmentBuilder builder = data.getBuilder();

      FileType fileType = content2.getContentType() == null ? content1.getContentType() : content2.getContentType();

      LineNumberConvertor convertor1 = builder.getConvertor1();
      LineNumberConvertor convertor2 = builder.getConvertor2();
      List<LineRange> changedLines = builder.getChangedLines();
      boolean isContentsEqual = builder.isEqual();

      CombinedEditorData editorData = new CombinedEditorData(builder.getText(), data.getHighlighter(), data.getRangeHighlighter(), fileType,
                                                             convertor1.createConvertor(), convertor2.createConvertor());

      return apply(editorData, builder.getBlocks(), convertor1, convertor2, changedLines, isContentsEqual);
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
      return () -> {
        clearDiffPresentation();
        myPanel.setErrorContent();
      };
    }
  }

  private void clearDiffPresentation() {
    myPanel.resetNotifications();
    myStatusPanel.setBusy(false);
    destroyChangedBlockData();

    myStateIsOutOfDate = false;
    mySuppressEditorTyping = false;
    updateEditorCanBeTyped();
  }

  @CalledInAwt
  protected void markSuppressEditorTyping() {
    mySuppressEditorTyping = true;
    updateEditorCanBeTyped();
  }

  @CalledInAwt
  protected void markStateIsOutOfDate() {
    myStateIsOutOfDate = true;
    if (myChangedBlockData != null) {
      for (UnifiedDiffChange diffChange : myChangedBlockData.getDiffChanges()) {
        diffChange.updateGutterActions();
      }
    }
  }

  @Nullable
  private EditorHighlighter buildHighlighter(@Nullable Project project,
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

    return new UnifiedEditorHighlighter(myDocument, highlighter1, highlighter2, ranges, textLength);
  }

  @NotNull
  private Runnable apply(@NotNull final CombinedEditorData data,
                         @NotNull final List<ChangedBlock> blocks,
                         @NotNull final LineNumberConvertor convertor1,
                         @NotNull final LineNumberConvertor convertor2,
                         @NotNull final List<LineRange> changedLines,
                         final boolean isContentsEqual) {
    return () -> {
      myFoldingModel.updateContext(myRequest, getFoldingModelSettings());

      LineCol oldCaretPosition = LineCol.fromOffset(myDocument, myEditor.getCaretModel().getPrimaryCaret().getOffset());
      Pair<int[], Side> oldCaretLineTwoside = transferLineFromOneside(oldCaretPosition.line);


      clearDiffPresentation();


      if (isContentsEqual) {
        boolean equalCharsets = TextDiffViewerUtil.areEqualCharsets(getContents());
        boolean equalSeparators = TextDiffViewerUtil.areEqualLineSeparators(getContents());
        myPanel.addNotification(DiffNotifications.createEqualContents(equalCharsets, equalSeparators));
      }

      TIntFunction foldingLineConvertor = myFoldingModel.getLineNumberConvertor();
      TIntFunction contentConvertor1 = DiffUtil.getContentLineConvertor(getContent1());
      TIntFunction contentConvertor2 = DiffUtil.getContentLineConvertor(getContent2());
      myEditor.getGutterComponentEx().setLineNumberConvertor(
        mergeLineConverters(contentConvertor1, data.getLineConvertor1(), foldingLineConvertor),
        mergeLineConverters(contentConvertor2, data.getLineConvertor2(), foldingLineConvertor));

      ApplicationManager.getApplication().runWriteAction(() -> {
        myDuringOnesideDocumentModification = true;
        try {
          myDocument.setText(data.getText());
        }
        finally {
          myDuringOnesideDocumentModification = false;
        }
      });

      if (data.getHighlighter() != null) myEditor.setHighlighter(data.getHighlighter());
      DiffUtil.setEditorCodeStyle(myProject, myEditor, data.getFileType());

      if (data.getRangeHighlighter() != null) data.getRangeHighlighter().apply(myProject, myDocument);


      ArrayList<UnifiedDiffChange> diffChanges = new ArrayList<>(blocks.size());
      for (ChangedBlock block : blocks) {
        diffChanges.add(new UnifiedDiffChange(this, block));
      }

      List<RangeMarker> guarderRangeBlocks = new ArrayList<>();
      if (!myEditor.isViewer()) {
        for (ChangedBlock block : blocks) {
          LineRange range = myMasterSide.select(block.getRange2(), block.getRange1());
          if (range.isEmpty()) continue;
          TextRange textRange = DiffUtil.getLinesRange(myDocument, range.start, range.end);
          guarderRangeBlocks.add(createGuardedBlock(textRange.getStartOffset(), textRange.getEndOffset()));
        }
        int textLength = myDocument.getTextLength(); // there are 'fake' newline at the very end
        guarderRangeBlocks.add(createGuardedBlock(textLength, textLength));
      }


      myChangedBlockData = new ChangedBlockData(diffChanges, guarderRangeBlocks, convertor1, convertor2, isContentsEqual);


      int newCaretLine = transferLineToOneside(oldCaretLineTwoside.second,
                                               oldCaretLineTwoside.second.select(oldCaretLineTwoside.first));
      myEditor.getCaretModel().moveToOffset(LineCol.toOffset(myDocument, newCaretLine, oldCaretPosition.column));

      myFoldingModel.install(changedLines, myRequest, getFoldingModelSettings());

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

  private static TIntFunction mergeLineConverters(@Nullable TIntFunction contentConvertor,
                                                  @NotNull TIntFunction unifiedConvertor,
                                                  @NotNull TIntFunction foldingConvertor) {
    return DiffUtil.mergeLineConverters(DiffUtil.mergeLineConverters(contentConvertor, unifiedConvertor), foldingConvertor);
  }

  /*
   * This convertor returns -1 if exact matching is impossible
   */
  @CalledInAwt
  public int transferLineToOnesideStrict(@NotNull Side side, int line) {
    if (myChangedBlockData == null) return -1;
    return myChangedBlockData.getLineNumberConvertor(side).convertInv(line);
  }

  /*
   * This convertor returns -1 if exact matching is impossible
   */
  @CalledInAwt
  public int transferLineFromOnesideStrict(@NotNull Side side, int line) {
    if (myChangedBlockData == null) return -1;
    return myChangedBlockData.getLineNumberConvertor(side).convert(line);
  }

  /*
   * This convertor returns 'good enough' position, even if exact matching is impossible
   */
  @CalledInAwt
  public int transferLineToOneside(@NotNull Side side, int line) {
    if (myChangedBlockData == null) return line;
    return myChangedBlockData.getLineNumberConvertor(side).convertApproximateInv(line);
  }

  /*
   * This convertor returns 'good enough' position, even if exact matching is impossible
   */
  @CalledInAwt
  @NotNull
  public Pair<int[], Side> transferLineFromOneside(int line) {
    int[] lines = new int[2];

    if (myChangedBlockData == null) {
      lines[0] = line;
      lines[1] = line;
      return Pair.create(lines, myMasterSide);
    }

    LineNumberConvertor lineConvertor1 = myChangedBlockData.getLineNumberConvertor(Side.LEFT);
    LineNumberConvertor lineConvertor2 = myChangedBlockData.getLineNumberConvertor(Side.RIGHT);

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

  @CalledInAwt
  private void destroyChangedBlockData() {
    if (myChangedBlockData == null) return;

    for (UnifiedDiffChange change : myChangedBlockData.getDiffChanges()) {
      change.destroyHighlighter();
    }
    for (RangeMarker block : myChangedBlockData.getGuardedRangeBlocks()) {
      myDocument.removeGuardedBlock(block);
    }
    myChangedBlockData = null;

    UnifiedEditorRangeHighlighter.erase(myProject, myDocument);

    myFoldingModel.destroy();

    myStatusPanel.update();
  }

  //
  // Typing
  //

  private class MyOnesideDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      if (myDuringOnesideDocumentModification) return;
      if (myChangedBlockData == null) {
        LOG.warn("oneside beforeDocumentChange - myChangedBlockData == null");
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

        for (UnifiedDiffChange change : myChangedBlockData.getDiffChanges()) {
          change.processChange(line1, line2, shift);
        }

        LineNumberConvertor masterConvertor = myChangedBlockData.getLineNumberConvertor(myMasterSide);
        LineNumberConvertor slaveConvertor = myChangedBlockData.getLineNumberConvertor(myMasterSide.other());
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
      StringBuilder info = new StringBuilder();
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
    protected final boolean myShortcut;

    public ApplySelectedChangesActionBase(@NotNull Side modifiedSide, boolean shortcut) {
      myModifiedSide = modifiedSide;
      myShortcut = shortcut;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myShortcut) {
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

      String title = e.getPresentation().getText() + " selected changes";
      DiffUtil.executeWriteCommand(getDocument(myModifiedSide), e.getProject(), title, () -> {
        // state is invalidated during apply(), but changes are in reverse order, so they should not conflict with each other
        apply(selectedChanges);
        scheduleRediff();
      });
    }

    protected boolean isSomeChangeSelected() {
      if (myChangedBlockData == null) return false;
      List<UnifiedDiffChange> changes = myChangedBlockData.getDiffChanges();
      if (changes.isEmpty()) return false;

      List<Caret> carets = getEditor().getCaretModel().getAllCarets();
      if (carets.size() != 1) return true;
      Caret caret = carets.get(0);
      if (caret.hasSelection()) return true;
      int line = getEditor().getDocument().getLineNumber(getEditor().getExpectedCaretOffset());

      for (UnifiedDiffChange change : changes) {
        if (DiffUtil.isSelectedByLine(line, change.getLine1(), change.getLine2())) return true;
      }
      return false;
    }

    @CalledWithWriteLock
    protected abstract void apply(@NotNull List<UnifiedDiffChange> changes);
  }

  private class ReplaceSelectedChangesAction extends ApplySelectedChangesActionBase {
    public ReplaceSelectedChangesAction(@NotNull Side focusedSide, boolean shortcut) {
      super(focusedSide.other(), shortcut);

      setShortcutSet(ActionManager.getInstance().getAction(focusedSide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide")).getShortcutSet());
      getTemplatePresentation().setText(focusedSide.select("Revert", "Accept"));
      getTemplatePresentation().setIcon(focusedSide.select(AllIcons.Diff.Remove, AllIcons.Actions.Checked));
    }

    @Override
    protected void apply(@NotNull List<UnifiedDiffChange> changes) {
      for (UnifiedDiffChange change : changes) {
        replaceChange(change, myModifiedSide.other());
      }
    }
  }

  private class AppendSelectedChangesAction extends ApplySelectedChangesActionBase {
    public AppendSelectedChangesAction(@NotNull Side focusedSide, boolean shortcut) {
      super(focusedSide.other(), shortcut);

      setShortcutSet(ActionManager.getInstance().getAction(focusedSide.select("Diff.AppendLeftSide", "Diff.AppendRightSide")).getShortcutSet());
      getTemplatePresentation().setText("Append");
      getTemplatePresentation().setIcon(DiffUtil.getArrowDownIcon(focusedSide));
    }

    @Override
    protected void apply(@NotNull List<UnifiedDiffChange> changes) {
      for (UnifiedDiffChange change : changes) {
        appendChange(change, myModifiedSide.other());
      }
    }
  }

  @CalledWithWriteLock
  public void replaceChange(@NotNull UnifiedDiffChange change, @NotNull Side sourceSide) {
    Side outputSide = sourceSide.other();

    Document document1 = getDocument(Side.LEFT);
    Document document2 = getDocument(Side.RIGHT);

    LineFragment lineFragment = change.getLineFragment();

    DiffUtil.applyModification(outputSide.select(document1, document2),
                               outputSide.getStartLine(lineFragment), outputSide.getEndLine(lineFragment),
                               sourceSide.select(document1, document2),
                               sourceSide.getStartLine(lineFragment), sourceSide.getEndLine(lineFragment));

    // no need to mark myStateIsOutOfDate - it will be made by DocumentListener
    // TODO: we can apply change manually, without marking state out-of-date. But we'll have to schedule rediff anyway.
  }

  @CalledWithWriteLock
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
  protected List<? extends EditorEx> getEditors() {
    return Collections.singletonList(myEditor);
  }

  @NotNull
  protected List<? extends DocumentContent> getContents() {
    //noinspection unchecked
    return (List<? extends DocumentContent>)(List)myRequest.getContents();
  }

  @NotNull
  protected DocumentContent getContent(@NotNull Side side) {
    return side.select(getContents());
  }

  @NotNull
  protected DocumentContent getContent1() {
    return getContent(Side.LEFT);
  }

  @NotNull
  protected DocumentContent getContent2() {
    return getContent(Side.RIGHT);
  }

  @CalledInAwt
  @Nullable
  protected List<UnifiedDiffChange> getDiffChanges() {
    return myChangedBlockData == null ? null : myChangedBlockData.getDiffChanges();
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
  protected JComponent getStatusPanel() {
    return myStatusPanel;
  }

  @CalledInAwt
  public boolean isEditable(@NotNull Side side, boolean respectReadOnlyLock) {
    if (myReadOnlyLockSet && respectReadOnlyLock) return false;
    if (side.select(myForceReadOnlyFlags)) return false;
    return DiffUtil.canMakeWritable(getDocument(side));
  }

  @NotNull
  public Document getDocument(@NotNull Side side) {
    return getContent(side).getDocument();
  }

  protected boolean isStateIsOutOfDate() {
    return myStateIsOutOfDate;
  }

  //
  // Misc
  //

  @Nullable
  @Override
  protected Navigatable getNavigatable() {
    return getNavigatable(LineCol.fromCaret(myEditor));
  }

  @CalledInAwt
  @Nullable
  protected UnifiedDiffChange getCurrentChange() {
    if (myChangedBlockData == null) return null;
    int caretLine = myEditor.getCaretModel().getLogicalPosition().line;

    for (UnifiedDiffChange change : myChangedBlockData.getDiffChanges()) {
      if (DiffUtil.isSelectedByLine(caretLine, change.getLine1(), change.getLine2())) return change;
    }
    return null;
  }

  @NotNull
  @CalledInAwt
  private List<UnifiedDiffChange> getSelectedChanges() {
    if (myChangedBlockData == null) return Collections.emptyList();
    final BitSet lines = DiffUtil.getSelectedLines(myEditor);
    List<UnifiedDiffChange> changes = myChangedBlockData.getDiffChanges();

    List<UnifiedDiffChange> affectedChanges = new ArrayList<>();
    for (int i = changes.size() - 1; i >= 0; i--) {
      UnifiedDiffChange change = changes.get(i);
      int line1 = change.getLine1();
      int line2 = change.getLine2();

      if (DiffUtil.isSelectedByLine(lines, line1, line2)) {
        affectedChanges.add(change);
      }
    }
    return affectedChanges;
  }

  @CalledInAwt
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
      return ContainerUtil.notNullize(getDiffChanges());
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
    public MyToggleExpandByDefaultAction() {
      super(getTextSettings());
    }

    @Override
    protected void expandAll(boolean expand) {
      myFoldingModel.expandAll(expand);
    }
  }

  private class MyReadOnlyLockAction extends TextDiffViewerUtil.ReadOnlyLockAction {
    public MyReadOnlyLockAction() {
      super(getContext());
      applyDefaults();
    }

    @Override
    protected void doApply(boolean readOnly) {
      myReadOnlyLockSet = readOnly;
      if (myChangedBlockData != null) {
        for (UnifiedDiffChange unifiedDiffChange : myChangedBlockData.getDiffChanges()) {
          unifiedDiffChange.updateGutterActions();
        }
      }
      updateEditorCanBeTyped();
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

  private class ChangedLinesIterator extends BufferedLineIterator {
    @NotNull private final List<UnifiedDiffChange> myChanges;

    private int myIndex = 0;

    private ChangedLinesIterator(@NotNull List<UnifiedDiffChange> changes) {
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
      CharSequence insertedText = getLinesContent(document, lineFragment.getStartLine2(), lineFragment.getEndLine2());

      int lineNumber = lineFragment.getStartLine2();

      LineTokenizer tokenizer = new LineTokenizer(insertedText.toString());
      for (String line : tokenizer.execute()) {
        addLine(lineNumber, line);
        lineNumber++;
      }
    }
  }

  //
  // Helpers
  //

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.is(dataId)) {
      return myPrevNextDifferenceIterable;
    }
    else if (DiffDataKeys.CURRENT_EDITOR.is(dataId)) {
      return myEditor;
    }
    else if (DiffDataKeys.CURRENT_CHANGE_RANGE.is(dataId)) {
      UnifiedDiffChange change = getCurrentChange();
      if (change != null) {
        return new LineRange(change.getLine1(), change.getLine2());
      }
    }
    return super.getData(dataId);
  }

  private class MyStatusPanel extends StatusPanel {
    @Nullable
    @Override
    protected String getMessage() {
      if (myChangedBlockData == null) return null;
      int changesCount = myChangedBlockData.getDiffChanges().size();
      if (changesCount == 0 && !myChangedBlockData.isContentsEqual()) {
        return DiffBundle.message("diff.all.differences.ignored.text");
      }
      return DiffBundle.message("diff.count.differences.status.text", changesCount);
    }
  }

  private static class TwosideDocumentData {
    @NotNull private final UnifiedFragmentBuilder myBuilder;
    @Nullable private final EditorHighlighter myHighlighter;
    @Nullable private final UnifiedEditorRangeHighlighter myRangeHighlighter;

    public TwosideDocumentData(@NotNull UnifiedFragmentBuilder builder,
                               @Nullable EditorHighlighter highlighter,
                               @Nullable UnifiedEditorRangeHighlighter rangeHighlighter) {
      myBuilder = builder;
      myHighlighter = highlighter;
      myRangeHighlighter = rangeHighlighter;
    }

    @NotNull
    public UnifiedFragmentBuilder getBuilder() {
      return myBuilder;
    }

    @Nullable
    public EditorHighlighter getHighlighter() {
      return myHighlighter;
    }

    @Nullable
    public UnifiedEditorRangeHighlighter getRangeHighlighter() {
      return myRangeHighlighter;
    }
  }

  private static class ChangedBlockData {
    @NotNull private final List<UnifiedDiffChange> myDiffChanges;
    @NotNull private final List<RangeMarker> myGuardedRangeBlocks;
    @NotNull private final LineNumberConvertor myLineNumberConvertor1;
    @NotNull private final LineNumberConvertor myLineNumberConvertor2;
    private final boolean myIsContentsEqual;

    public ChangedBlockData(@NotNull List<UnifiedDiffChange> diffChanges,
                            @NotNull List<RangeMarker> guarderRangeBlocks,
                            @NotNull LineNumberConvertor lineNumberConvertor1,
                            @NotNull LineNumberConvertor lineNumberConvertor2,
                            boolean isContentsEqual) {
      myDiffChanges = diffChanges;
      myGuardedRangeBlocks = guarderRangeBlocks;
      myLineNumberConvertor1 = lineNumberConvertor1;
      myLineNumberConvertor2 = lineNumberConvertor2;
      myIsContentsEqual = isContentsEqual;
    }

    @NotNull
    public List<UnifiedDiffChange> getDiffChanges() {
      return myDiffChanges;
    }

    @NotNull
    public List<RangeMarker> getGuardedRangeBlocks() {
      return myGuardedRangeBlocks;
    }

    @NotNull
    public LineNumberConvertor getLineNumberConvertor(@NotNull Side side) {
      return side.select(myLineNumberConvertor1, myLineNumberConvertor2);
    }

    public boolean isContentsEqual() {
      return myIsContentsEqual;
    }
  }

  private static class CombinedEditorData {
    @NotNull private final CharSequence myText;
    @Nullable private final EditorHighlighter myHighlighter;
    @Nullable private final UnifiedEditorRangeHighlighter myRangeHighlighter;
    @Nullable private final FileType myFileType;
    @NotNull private final TIntFunction myLineConvertor1;
    @NotNull private final TIntFunction myLineConvertor2;

    public CombinedEditorData(@NotNull CharSequence text,
                              @Nullable EditorHighlighter highlighter,
                              @Nullable UnifiedEditorRangeHighlighter rangeHighlighter,
                              @Nullable FileType fileType,
                              @NotNull TIntFunction convertor1,
                              @NotNull TIntFunction convertor2) {
      myText = text;
      myHighlighter = highlighter;
      myRangeHighlighter = rangeHighlighter;
      myFileType = fileType;
      myLineConvertor1 = convertor1;
      myLineConvertor2 = convertor2;
    }

    @NotNull
    public CharSequence getText() {
      return myText;
    }

    @Nullable
    public EditorHighlighter getHighlighter() {
      return myHighlighter;
    }

    @Nullable
    public UnifiedEditorRangeHighlighter getRangeHighlighter() {
      return myRangeHighlighter;
    }

    @Nullable
    public FileType getFileType() {
      return myFileType;
    }

    @NotNull
    public TIntFunction getLineConvertor1() {
      return myLineConvertor1;
    }

    @NotNull
    public TIntFunction getLineConvertor2() {
      return myLineConvertor2;
    }
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

    @Nullable
    @Override
    protected LogicalPosition[] getCaretPositions() {
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
    private LogicalPosition getPosition(int line, int column) {
      if (line == -1) return new LogicalPosition(0, 0);
      return new LogicalPosition(line, column);
    }

    private void doScrollToLine(@NotNull Side side, @NotNull LogicalPosition position) {
      int onesideLine = transferLineToOneside(side, position.line);
      DiffUtil.scrollEditor(myEditor, onesideLine, position.column, false);
    }

    @Override
    protected boolean doScrollToLine() {
      if (myScrollToLine == null) return false;
      doScrollToLine(myScrollToLine.first, new LogicalPosition(myScrollToLine.second, 0));
      return true;
    }

    private boolean doScrollToChange(@NotNull ScrollToPolicy scrollToChangePolicy) {
      if (myChangedBlockData == null) return false;
      List<UnifiedDiffChange> changes = myChangedBlockData.getDiffChanges();

      UnifiedDiffChange targetChange = scrollToChangePolicy.select(changes);
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
      if (myChangedBlockData == null) return false;

      ChangedLinesIterator changedLinesIterator = new ChangedLinesIterator(myChangedBlockData.getDiffChanges());
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
    public MyFoldingModel(@NotNull EditorEx editor, @NotNull Disposable disposable) {
      super(new EditorEx[]{editor}, disposable);
    }

    public void install(@Nullable List<LineRange> changedLines,
                        @NotNull UserDataHolder context,
                        @NotNull FoldingModelSupport.Settings settings) {
      Iterator<int[]> it = map(changedLines, line -> new int[]{
        line.start,
        line.end
      });
      install(it, context, settings);
    }

    @NotNull
    public TIntFunction getLineNumberConvertor() {
      return getLineConvertor(0);
    }
  }

  private static class MyReadonlyFragmentModificationHandler implements ReadonlyFragmentModificationHandler {
    @Override
    public void handle(ReadOnlyFragmentModificationException e) {
      // do nothing
    }
  }
}
