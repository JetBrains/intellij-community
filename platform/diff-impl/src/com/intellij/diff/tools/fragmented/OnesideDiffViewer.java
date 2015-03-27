/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.diff.actions.BufferedLineIterator;
import com.intellij.diff.actions.NavigationContextChecker;
import com.intellij.diff.actions.impl.OpenInEditorWithMouseAction;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.comparison.iterables.DiffIterableUtil.IntPair;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.diff.tools.util.StatusPanel;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffViewerBase;
import com.intellij.diff.tools.util.twoside.TwosideTextDiffViewer;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.DiffUtil.DocumentData;
import com.intellij.diff.util.DiffUtil.EditorsVisiblePositions;
import com.intellij.diff.util.Side;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.MergingCharSequence;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public class OnesideDiffViewer extends TextDiffViewerBase {
  public static final Logger LOG = Logger.getInstance(OnesideDiffViewer.class);

  @NotNull protected final EditorEx myEditor;
  @NotNull protected final Document myDocument;
  @NotNull private final OnesideDiffPanel myPanel;

  @Nullable private final DocumentContent myActualContent1;
  @Nullable private final DocumentContent myActualContent2;

  @NotNull private final MySetEditorSettingsAction myEditorSettingsAction;
  @NotNull private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final MyStatusPanel myStatusPanel;

  @NotNull private final MyScrollToLineHelper myScrollToLineHelper = new MyScrollToLineHelper();
  @NotNull private final MyFoldingModel myFoldingModel;

  @NotNull protected Side myMasterSide = Side.RIGHT;

  @Nullable private ChangedBlockData myChangedBlockData;

  public OnesideDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    myStatusPanel = new MyStatusPanel();


    List<DiffContent> contents = myRequest.getContents();
    myActualContent1 = contents.get(0) instanceof DocumentContent ? ((DocumentContent)contents.get(0)) : null;
    myActualContent2 = contents.get(1) instanceof DocumentContent ? ((DocumentContent)contents.get(1)) : null;
    assert myActualContent1 != null || myActualContent2 != null;


    myDocument = EditorFactory.getInstance().createDocument("");
    myEditor = DiffUtil.createEditor(myDocument, myProject, true, true);
    List<JComponent> titles = DiffUtil.createTextTitles(myRequest, ContainerUtil.list(myEditor, myEditor));


    OnesideContentPanel contentPanel = new OnesideContentPanel(titles, myEditor);

    myPanel = new OnesideDiffPanel(myProject, contentPanel, myEditor, this, myContext);

    myFoldingModel = new MyFoldingModel(myEditor, this);

    myEditorSettingsAction = new MySetEditorSettingsAction();
    myEditorSettingsAction.applyDefaults();

    new MyOpenInEditorWithMouseAction().register(getEditors());
  }

  @Override
  protected void onInit() {
    super.onInit();
    processContextHints();
  }

  @Override
  protected void onDispose() {
    updateContextHints();
    EditorFactory.getInstance().releaseEditor(myEditor);
    super.onDispose();
  }

  protected void processContextHints() {
    Side side = DiffUtil.getUserData(myRequest, myContext, DiffUserDataKeys.MASTER_SIDE);
    if (side != null) myMasterSide = side;

    myScrollToLineHelper.processContext();
  }

  protected void updateContextHints() {
    myScrollToLineHelper.updateContext();
    myFoldingModel.updateContext(myRequest, getTextSettings().isExpandByDefault());
  }

  @NotNull
  public List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(new MyIgnorePolicySettingAction());
    group.add(new MyHighlightPolicySettingAction());
    group.add(new MyToggleExpandByDefaultAction());
    group.add(new ReadOnlyLockAction());
    group.add(myEditorSettingsAction);

    return group;
  }

  @NotNull
  public List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(Separator.getInstance());
    group.add(new MyIgnorePolicySettingAction().getPopupGroup());
    group.add(Separator.getInstance());
    group.add(new MyHighlightPolicySettingAction().getPopupGroup());
    group.add(Separator.getInstance());
    group.add(new MyToggleExpandByDefaultAction());

    return group;
  }

  //
  // Diff
  //

  @Override
  protected void onSlowRediff() {
    super.onSlowRediff();
    myPanel.setLoadingContent();
  }

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    try {
      indicator.checkCanceled();
      assert myActualContent1 != null || myActualContent2 != null;

      if (myActualContent1 == null) {
        final DocumentContent content = myActualContent2;
        final Document document = content.getDocument();

        OnesideDocumentData data = ApplicationManager.getApplication().runReadAction(new Computable<OnesideDocumentData>() {
          @Override
          public OnesideDocumentData compute() {
            EditorHighlighter highlighter = DiffUtil.createEditorHighlighter(myProject, content);
            OnesideEditorRangeHighlighter rangeHighlighter = new OnesideEditorRangeHighlighter(myProject, content.getDocument());
            return new OnesideDocumentData(document.getImmutableCharSequence(), getLineCount(document), highlighter, rangeHighlighter);
          }
        });

        List<ChangedBlock> blocks = new ArrayList<ChangedBlock>();
        blocks.add(ChangedBlock.createInserted(data.getText().length() + 1, data.getLines()));

        indicator.checkCanceled();
        LineNumberConvertor convertor = LineNumberConvertor.Builder.createLeft(data.getLines());

        CombinedEditorData editorData = new CombinedEditorData(new MergingCharSequence(data.getText(), "\n"), data.getHighlighter(),
                                                               data.getRangeHighlighter(), content.getContentType(),
                                                               convertor.createConvertor1(), null);

        return apply(editorData, blocks, convertor, Collections.singletonList(new IntPair(0, data.getLines())), false);
      }

      if (myActualContent2 == null) {
        final DocumentContent content = myActualContent1;
        final Document document = content.getDocument();

        OnesideDocumentData data = ApplicationManager.getApplication().runReadAction(new Computable<OnesideDocumentData>() {
          @Override
          public OnesideDocumentData compute() {
            EditorHighlighter highlighter = DiffUtil.createEditorHighlighter(myProject, content);
            OnesideEditorRangeHighlighter rangeHighlighter = new OnesideEditorRangeHighlighter(myProject, content.getDocument());
            return new OnesideDocumentData(document.getImmutableCharSequence(), getLineCount(document), highlighter, rangeHighlighter);
          }
        });

        List<ChangedBlock> blocks = new ArrayList<ChangedBlock>();
        blocks.add(ChangedBlock.createDeleted(data.getText().length() + 1, data.getLines()));

        indicator.checkCanceled();
        LineNumberConvertor convertor = LineNumberConvertor.Builder.createRight(data.getLines());

        CombinedEditorData editorData = new CombinedEditorData(new MergingCharSequence(data.getText(), "\n"), data.getHighlighter(),
                                                               data.getRangeHighlighter(), content.getContentType(),
                                                               convertor.createConvertor2(), null);

        return apply(editorData, blocks, convertor, Collections.singletonList(new IntPair(0, data.getLines())), false);
      }

      final DocumentContent content1 = myActualContent1;
      final DocumentContent content2 = myActualContent2;
      final Document document1 = content1.getDocument();
      final Document document2 = content2.getDocument();

      final DocumentData documentData = ApplicationManager.getApplication().runReadAction(new Computable<DocumentData>() {
        @Override
        public DocumentData compute() {
          return new DocumentData(document1.getImmutableCharSequence(), document2.getImmutableCharSequence(),
                                  document1.getModificationStamp(), document2.getModificationStamp());
        }
      });

      final List<LineFragment> fragments = DiffUtil.compareWithCache(myRequest, documentData, getDiffConfig(), indicator);

      indicator.checkCanceled();
      TwosideDocumentData data = ApplicationManager.getApplication().runReadAction(new Computable<TwosideDocumentData>() {
        @Override
        public TwosideDocumentData compute() {
          indicator.checkCanceled();
          OnesideFragmentBuilder builder = new OnesideFragmentBuilder(fragments, document1, document2,
                                                                      getHighlightPolicy().isFineFragments(),
                                                                      myMasterSide);
          builder.exec();

          indicator.checkCanceled();

          EditorHighlighter highlighter = buildHighlighter(myProject, content1, content2,
                                                           documentData.getText1(), documentData.getText2(), builder.getRanges(),
                                                           builder.getText().length());

          OnesideEditorRangeHighlighter rangeHighlighter = new OnesideEditorRangeHighlighter(myProject, document1, document2,
                                                                                             builder.getRanges());

          return new TwosideDocumentData(builder, highlighter, rangeHighlighter);
        }
      });
      OnesideFragmentBuilder builder = data.getBuilder();

      FileType fileType = content2.getContentType() == null ? content1.getContentType() : content2.getContentType();

      LineNumberConvertor convertor = builder.getConvertor();
      List<IntPair> changedLines = builder.getChangedLines();
      boolean isEqual = builder.isEqual();

      CombinedEditorData editorData = new CombinedEditorData(builder.getText(), data.getHighlighter(), data.getRangeHighlighter(), fileType,
                                                             convertor.createConvertor1(), convertor.createConvertor2());

      return apply(editorData, builder.getBlocks(), convertor, changedLines, isEqual);
    }
    catch (DiffTooBigException ignore) {
      return new Runnable() {
        @Override
        public void run() {
          clearDiffPresentation();
          myPanel.setTooBigContent();
        }
      };
    }
    catch (ProcessCanceledException ignore) {
      return new Runnable() {
        @Override
        public void run() {
          clearDiffPresentation();
          myPanel.setOperationCanceledContent();
        }
      };
    }
    catch (Throwable e) {
      LOG.error(e);
      return new Runnable() {
        @Override
        public void run() {
          clearDiffPresentation();
          myPanel.setErrorContent();
        }
      };
    }
  }

  private void clearDiffPresentation() {
    myPanel.resetNotifications();
    destroyChangedBlockData();
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
    if (highlighter1 == null) highlighter1 = DiffUtil.initEmptyEditorHighlighter(project, text1);
    if (highlighter2 == null) highlighter2 = DiffUtil.initEmptyEditorHighlighter(project, text2);

    return new OnesideEditorHighlighter(myDocument, highlighter1, highlighter2, ranges, textLength);
  }

  @NotNull
  private Runnable apply(@NotNull final CombinedEditorData data,
                         @NotNull final List<ChangedBlock> blocks,
                         @NotNull final LineNumberConvertor convertor,
                         @NotNull final List<IntPair> changedLines,
                         final boolean isEqual) {
    return new Runnable() {
      @Override
      public void run() {
        myFoldingModel.updateContext(myRequest, getTextSettings().isExpandByDefault());

        clearDiffPresentation();
        if (isEqual) myPanel.addContentsEqualNotification();

        TIntFunction separatorLines = myFoldingModel.getLineNumberConvertor();
        myEditor.getGutterComponentEx().setLineNumberConvertor(mergeConverters(data.getLineConvertor1(), separatorLines),
                                                               mergeConverters(data.getLineConvertor2(), separatorLines));

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            myDocument.setText(data.getText());
          }
        });

        if (data.getHighlighter() != null) myEditor.setHighlighter(data.getHighlighter());
        DiffUtil.setEditorCodeStyle(myProject, myEditor, data.getFileType());

        if (data.getRangeHighlighter() != null) data.getRangeHighlighter().apply(myProject, myDocument);


        ArrayList<OnesideDiffChange> diffChanges = new ArrayList<OnesideDiffChange>(blocks.size());
        for (ChangedBlock block : blocks) {
          diffChanges.add(new OnesideDiffChange(myEditor, block));
        }

        myChangedBlockData = new ChangedBlockData(diffChanges, convertor);

        myFoldingModel.install(changedLines, myRequest, getTextSettings().isExpandByDefault(), getTextSettings().getContextRange());

        myScrollToLineHelper.onRediff();

        myStatusPanel.update();
        myPanel.setGoodContent();
      }
    };
  }

  @Contract("!null, _ -> !null")
  private static TIntFunction mergeConverters(@Nullable final TIntFunction convertor, @NotNull final TIntFunction separatorLines) {
    if (convertor == null) return null;
    return new TIntFunction() {
      @Override
      public int execute(int value) {
        return convertor.execute(separatorLines.execute(value));
      }
    };
  }

  /*
   * This convertor returns -1 if exact matching is impossible
   */
  @CalledInAwt
  protected int transferLineToOnesideStrict(@NotNull Side side, int line) {
    if (myChangedBlockData == null) return -1;

    LineNumberConvertor lineConvertor = myChangedBlockData.getLineNumberConvertor();
    return side.isLeft() ? lineConvertor.convertInv1(line) : lineConvertor.convertInv2(line);
  }

  /*
   * This convertor returns -1 if exact matching is impossible
   */
  @CalledInAwt
  protected int transferLineFromOnesideStrict(@NotNull Side side, int line) {
    if (myChangedBlockData == null) return -1;

    LineNumberConvertor lineConvertor = myChangedBlockData.getLineNumberConvertor();
    return side.isLeft() ? lineConvertor.convert1(line) : lineConvertor.convert2(line);
  }

  /*
   * This convertor returns 'good enough' position, even if exact matching is impossible
   */
  @CalledInAwt
  protected int transferLineToOneside(@NotNull Side side, int line) {
    if (myChangedBlockData == null) return line;

    LineNumberConvertor lineConvertor = myChangedBlockData.getLineNumberConvertor();
    return side.isLeft() ? lineConvertor.convertApproximateInv1(line) : lineConvertor.convertApproximateInv2(line);
  }

  /*
   * This convertor returns 'good enough' position, even if exact matching is impossible
   */
  @CalledInAwt
  protected Pair<int[], Side> transferLineFromOneside(int line) {
    int[] lines = new int[2];

    if (myChangedBlockData == null) {
      lines[0] = myActualContent1 != null ? line : 0;
      lines[1] = myActualContent2 != null ? line : 0;
      return Pair.create(lines, myMasterSide);
    }

    LineNumberConvertor lineConvertor = myChangedBlockData.getLineNumberConvertor();

    Side side = myMasterSide;
    lines[0] = lineConvertor.convert1(line);
    lines[1] = lineConvertor.convert2(line);

    if (lines[0] == -1 && lines[1] == -1) {
      lines[0] = lineConvertor.convertApproximate1(line);
      lines[1] = lineConvertor.convertApproximate2(line);
    }
    else if (lines[0] == -1) {
      lines[0] = lineConvertor.convertApproximate1(line);
      side = Side.RIGHT;
    }
    else if (lines[1] == -1) {
      lines[1] = lineConvertor.convertApproximate2(line);
      side = Side.LEFT;
    }

    return Pair.create(lines, side);
  }

  @CalledInAwt
  private void destroyChangedBlockData() {
    if (myChangedBlockData == null) return;

    for (OnesideDiffChange change : myChangedBlockData.getDiffChanges()) {
      change.destroyHighlighter();
    }
    myChangedBlockData = null;

    myFoldingModel.destroy();

    myStatusPanel.update();
  }

  //
  // Impl
  //

  @NotNull
  private DiffUtil.DiffConfig getDiffConfig() {
    return new DiffUtil.DiffConfig(getIgnorePolicy(), getHighlightPolicy());
  }

  @NotNull
  private HighlightPolicy getHighlightPolicy() {
    HighlightPolicy policy = getTextSettings().getHighlightPolicy();
    if (policy == HighlightPolicy.DO_NOT_HIGHLIGHT) return HighlightPolicy.BY_LINE;
    return policy;
  }

  @NotNull
  private IgnorePolicy getIgnorePolicy() {
    IgnorePolicy policy = getTextSettings().getIgnorePolicy();
    if (policy == IgnorePolicy.IGNORE_WHITESPACES_CHUNKS) return IgnorePolicy.IGNORE_WHITESPACES;
    return policy;
  }

  @Override
  protected void onDocumentChange(@NotNull DocumentEvent e) {
    super.onDocumentChange(e);
    myFoldingModel.onDocumentChanged(e);
  }

  //
  // Getters
  //

  @NotNull
  @Override
  protected List<? extends EditorEx> getEditors() {
    return Collections.singletonList(myEditor);
  }

  @CalledInAwt
  @Nullable
  protected List<OnesideDiffChange> getDiffChanges() {
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
    return myPanel.getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  protected JComponent getStatusPanel() {
    return myStatusPanel;
  }

  //
  // Misc
  //

  @Override
  protected boolean tryRediffSynchronously() {
    return myPanel.isWindowFocused();
  }

  @Nullable
  @Override
  protected OpenFileDescriptor getOpenFileDescriptor() {
    return getOpenFileDescriptor(myEditor.getCaretModel().getOffset());
  }

  @Nullable
  protected OpenFileDescriptor getOpenFileDescriptor(int offset) {
    assert myActualContent1 != null || myActualContent2 != null;
    if (myActualContent2 == null) {
      return myActualContent1.getOpenFileDescriptor(offset);
    }
    if (myActualContent1 == null) {
      return myActualContent2.getOpenFileDescriptor(offset);
    }

    LogicalPosition position = myEditor.offsetToLogicalPosition(offset);
    Pair<int[], Side> pair = transferLineFromOneside(position.line);
    int offset1 = DiffUtil.getOffset(myActualContent1.getDocument(), pair.first[0], position.column);
    int offset2 = DiffUtil.getOffset(myActualContent2.getDocument(), pair.first[1], position.column);

    // TODO: issue: non-optimal GoToSource position with caret on deleted block for "Compare with local"
    //       we should transfer using calculated diff, not jump to "somehow related" position from old content's descriptor

    OpenFileDescriptor descriptor1 = myActualContent1.getOpenFileDescriptor(offset1);
    OpenFileDescriptor descriptor2 = myActualContent2.getOpenFileDescriptor(offset2);
    if (descriptor1 == null) return descriptor2;
    if (descriptor2 == null) return descriptor1;
    return pair.second.select(descriptor1, descriptor2);
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return TwosideTextDiffViewer.canShowRequest(context, request);
  }

  //
  // Actions
  //

  private class MyPrevNextDifferenceIterable implements PrevNextDifferenceIterable {
    @Override
    public boolean canGoNext() {
      List<OnesideDiffChange> diffChanges = getDiffChanges();
      if (diffChanges == null || diffChanges.isEmpty()) return false;

      int line = myEditor.getCaretModel().getLogicalPosition().line;
      OnesideDiffChange lastChange = diffChanges.get(diffChanges.size() - 1);
      if (lastChange.getLine1() <= line) return false;

      return true;
    }

    @Override
    public void goNext() {
      List<OnesideDiffChange> diffChanges = getDiffChanges();
      assert diffChanges != null;
      int line = myEditor.getCaretModel().getLogicalPosition().line;

      OnesideDiffChange next = null;
      for (int i = 0; i < diffChanges.size(); i++) {
        OnesideDiffChange change = diffChanges.get(i);
        if (change.getLine1() <= line) continue;

        next = change;
        break;
      }

      assert next != null;

      DiffUtil.scrollToLineAnimated(myEditor, next.getLine1());
    }

    @Override
    public boolean canGoPrev() {
      List<OnesideDiffChange> diffChanges = getDiffChanges();
      if (diffChanges == null || diffChanges.isEmpty()) return false;

      int line = myEditor.getCaretModel().getLogicalPosition().line;
      OnesideDiffChange firstChange = diffChanges.get(0);
      if (firstChange.getLine2() > line) return false;

      return true;
    }

    @Override
    public void goPrev() {
      List<OnesideDiffChange> diffChanges = getDiffChanges();
      assert diffChanges != null;
      int line = myEditor.getCaretModel().getLogicalPosition().line;

      OnesideDiffChange prev = null;
      for (int i = 0; i < diffChanges.size(); i++) {
        OnesideDiffChange change = diffChanges.get(i);
        if (change.getLine2() <= line) continue;

        prev = diffChanges.get(i - 1);
        break;
      }

      if (prev == null) prev = diffChanges.get(diffChanges.size() - 1);

      DiffUtil.scrollToLineAnimated(myEditor, prev.getLine1());
    }
  }

  private class MyOpenInEditorWithMouseAction extends OpenInEditorWithMouseAction {
    @Override
    protected OpenFileDescriptor getDescriptor(@NotNull Editor editor, int line) {
      if (editor != myEditor) return null;

      return getOpenFileDescriptor(myEditor.logicalPositionToOffset(new LogicalPosition(line, 0)));
    }
  }

  private class MyToggleExpandByDefaultAction extends ToggleExpandByDefaultAction {
    @Override
    protected void expandAll(boolean expand) {
      myFoldingModel.expandAll(expand);
    }
  }

  private class MyHighlightPolicySettingAction extends HighlightPolicySettingAction {
    @NotNull
    @Override
    protected HighlightPolicy getCurrentSetting() {
      return getHighlightPolicy();
    }

    @NotNull
    @Override
    protected List<HighlightPolicy> getAvailableSettings() {
      ArrayList<HighlightPolicy> settings = ContainerUtil.newArrayList(HighlightPolicy.values());
      settings.remove(HighlightPolicy.DO_NOT_HIGHLIGHT);
      return settings;
    }
  }

  private class MyIgnorePolicySettingAction extends IgnorePolicySettingAction {
    @NotNull
    @Override
    protected IgnorePolicy getCurrentSetting() {
      return getIgnorePolicy();
    }

    @NotNull
    @Override
    protected List<IgnorePolicy> getAvailableSettings() {
      ArrayList<IgnorePolicy> settings = ContainerUtil.newArrayList(IgnorePolicy.values());
      settings.remove(IgnorePolicy.IGNORE_WHITESPACES_CHUNKS);
      return settings;
    }
  }

  //
  // Scroll from annotate
  //

  private class AllLinesIterator implements Iterator<Pair<Integer, CharSequence>> {
    @NotNull private final Side mySide;
    @NotNull private final Document myDocument;
    private int myLine = 0;

    private AllLinesIterator(@NotNull Side side) {
      mySide = side;

      DocumentContent content = mySide.select(myActualContent1, myActualContent2);
      assert content != null;
      myDocument = content.getDocument();
    }

    @Override
    public boolean hasNext() {
      return myLine < getLineCount(myDocument);
    }

    @Override
    public Pair<Integer, CharSequence> next() {
      int offset1 = myDocument.getLineStartOffset(myLine);
      int offset2 = myDocument.getLineEndOffset(myLine);

      CharSequence text = myDocument.getImmutableCharSequence().subSequence(offset1, offset2);

      Pair<Integer, CharSequence> pair = new Pair<Integer, CharSequence>(myLine, text);
      myLine++;

      return pair;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private class ChangedLinesIterator extends BufferedLineIterator {
    @NotNull private final Side mySide;
    @NotNull private final List<OnesideDiffChange> myChanges;

    private int myIndex = 0;

    private ChangedLinesIterator(@NotNull Side side, @NotNull List<OnesideDiffChange> changes) {
      mySide = side;
      myChanges = changes;
      init();
    }

    @Override
    public boolean hasNextBlock() {
      return myIndex < myChanges.size();
    }

    @Override
    public void loadNextBlock() {
      OnesideDiffChange change = myChanges.get(myIndex);
      myIndex++;

      int line1 = change.getLine1();
      int line2 = change.getLine2();

      Document document = myEditor.getDocument();

      for (int i = line1; i < line2; i++) {
        int offset1 = document.getLineStartOffset(i);
        int offset2 = document.getLineEndOffset(i);

        if (offset2 < change.getStartOffset2()) continue; // because we want only insertions

        CharSequence text = document.getImmutableCharSequence().subSequence(offset1, offset2);
        addLine(i, text);
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
    else {
      return super.getData(dataId);
    }
  }

  private class MyStatusPanel extends StatusPanel {
    @Override
    protected int getChangesCount() {
      return myChangedBlockData == null ? 0 : myChangedBlockData.getDiffChanges().size();
    }
  }

  private static class OnesideDocumentData {
    @NotNull private final CharSequence myText;
    private final int myLines;

    @Nullable private final EditorHighlighter myHighlighter;
    @Nullable private final OnesideEditorRangeHighlighter myRangeHighlighter;

    public OnesideDocumentData(@NotNull CharSequence text,
                               int lines,
                               @Nullable EditorHighlighter highlighter,
                               @Nullable OnesideEditorRangeHighlighter rangeHighlighter) {
      myText = text;
      myLines = lines;
      myHighlighter = highlighter;
      myRangeHighlighter = rangeHighlighter;
    }

    @NotNull
    public CharSequence getText() {
      return myText;
    }

    public int getLines() {
      return myLines;
    }

    @Nullable
    public EditorHighlighter getHighlighter() {
      return myHighlighter;
    }

    @Nullable
    public OnesideEditorRangeHighlighter getRangeHighlighter() {
      return myRangeHighlighter;
    }
  }

  private static class TwosideDocumentData {
    @NotNull private final OnesideFragmentBuilder myBuilder;
    @Nullable private final EditorHighlighter myHighlighter;
    @Nullable private final OnesideEditorRangeHighlighter myRangeHighlighter;

    public TwosideDocumentData(@NotNull OnesideFragmentBuilder builder,
                               @Nullable EditorHighlighter highlighter,
                               @Nullable OnesideEditorRangeHighlighter rangeHighlighter) {
      myBuilder = builder;
      myHighlighter = highlighter;
      myRangeHighlighter = rangeHighlighter;
    }

    @NotNull
    public OnesideFragmentBuilder getBuilder() {
      return myBuilder;
    }

    @Nullable
    public EditorHighlighter getHighlighter() {
      return myHighlighter;
    }

    @Nullable
    public OnesideEditorRangeHighlighter getRangeHighlighter() {
      return myRangeHighlighter;
    }
  }

  private static class ChangedBlockData {
    @NotNull private final List<OnesideDiffChange> myDiffChanges;
    @NotNull private final LineNumberConvertor myLineNumberConvertor;

    public ChangedBlockData(@NotNull List<OnesideDiffChange> diffChanges,
                            @NotNull LineNumberConvertor lineNumberConvertor) {
      myDiffChanges = diffChanges;
      myLineNumberConvertor = lineNumberConvertor;
    }

    @NotNull
    public List<OnesideDiffChange> getDiffChanges() {
      return myDiffChanges;
    }

    @NotNull
    public LineNumberConvertor getLineNumberConvertor() {
      return myLineNumberConvertor;
    }
  }

  private static class CombinedEditorData {
    @NotNull private final CharSequence myText;
    @Nullable private final EditorHighlighter myHighlighter;
    @Nullable private final OnesideEditorRangeHighlighter myRangeHighlighter;
    @Nullable private final FileType myFileType;
    @NotNull private final TIntFunction myLineConvertor1;
    @Nullable private final TIntFunction myLineConvertor2;

    public CombinedEditorData(@NotNull CharSequence text,
                              @Nullable EditorHighlighter highlighter,
                              @Nullable OnesideEditorRangeHighlighter rangeHighlighter,
                              @Nullable FileType fileType,
                              @NotNull TIntFunction convertor1,
                              @Nullable TIntFunction convertor2) {
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
    public OnesideEditorRangeHighlighter getRangeHighlighter() {
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

    @Nullable
    public TIntFunction getLineConvertor2() {
      return myLineConvertor2;
    }
  }

  private class MyScrollToLineHelper {
    protected boolean myShouldScroll = true;

    @Nullable private ScrollToPolicy myScrollToChange;
    @Nullable private EditorsVisiblePositions myEditorPosition;
    @Nullable private LogicalPosition[] myCaretPosition;
    @Nullable private DiffNavigationContext myNavigationContext;

    public void processContext() {
      myScrollToChange = myRequest.getUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE);
      myEditorPosition = myRequest.getUserData(EditorsVisiblePositions.KEY);
      myCaretPosition = myRequest.getUserData(DiffUserDataKeysEx.EDITORS_CARET_POSITION);
      myNavigationContext = myRequest.getUserData(DiffUserDataKeysEx.NAVIGATION_CONTEXT);
    }

    public void updateContext() {
      LogicalPosition position = myEditor.getCaretModel().getLogicalPosition();
      Pair<int[], Side> pair = transferLineFromOneside(position.line);
      LogicalPosition[] carets = new LogicalPosition[2];
      carets[0] = getPosition(pair.first[0], position.column);
      carets[1] = getPosition(pair.first[1], position.column);

      EditorsVisiblePositions editorsPosition = new EditorsVisiblePositions(position, DiffUtil.getScrollingPosition(myEditor));

      myRequest.putUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE, null);
      myRequest.putUserData(EditorsVisiblePositions.KEY, editorsPosition);
      myRequest.putUserData(DiffUserDataKeysEx.EDITORS_CARET_POSITION, carets);
      myRequest.putUserData(DiffUserDataKeysEx.NAVIGATION_CONTEXT, null);
    }

    public void onRediff() {
      if (myShouldScroll && myScrollToChange != null) {
        myShouldScroll = !doScrollToChange(myScrollToChange);
      }
      if (myShouldScroll && myNavigationContext != null) {
        myShouldScroll = !doScrollToContext(myNavigationContext);
      }
      if (myShouldScroll && myCaretPosition != null && myCaretPosition.length == 2) {
        LogicalPosition twosidePosition = myMasterSide.selectNotNull(myCaretPosition);
        int onesideLine = transferLineToOneside(myMasterSide, twosidePosition.line);
        LogicalPosition position = new LogicalPosition(onesideLine, twosidePosition.column);

        myEditor.getCaretModel().moveToLogicalPosition(position);

        if (myEditorPosition != null && myEditorPosition.isSame(position)) {
          DiffUtil.scrollToPoint(myEditor, myEditorPosition.myPoints[0]);
        }
        else {
          DiffUtil.scrollToCaret(myEditor);
        }
        myShouldScroll = false;
      }
      if (myShouldScroll) {
        doScrollToChange(ScrollToPolicy.FIRST_CHANGE);
      }
      myShouldScroll = false;
    }

    @NotNull
    private LogicalPosition getPosition(int line, int column) {
      if (line == -1) return new LogicalPosition(0, 0);
      return new LogicalPosition(line, column);
    }

    private boolean doScrollToLine(@NotNull Side side, @NotNull LogicalPosition position) {
      int onesideLine = transferLineToOneside(side, position.line);
      DiffUtil.scrollEditor(myEditor, onesideLine, position.column);
      return true;
    }

    private boolean doScrollToChange(@NotNull ScrollToPolicy scrollToChangePolicy) {
      if (myChangedBlockData == null) return false;
      List<OnesideDiffChange> changes = myChangedBlockData.getDiffChanges();
      if (changes.isEmpty()) return false;

      OnesideDiffChange targetChange;
      switch (scrollToChangePolicy) {
        case FIRST_CHANGE:
          targetChange = changes.get(0);
          break;
        case LAST_CHANGE:
          targetChange = changes.get(changes.size() - 1);
          break;
        default:
          throw new IllegalArgumentException(scrollToChangePolicy.name());
      }

      DiffUtil.scrollEditor(myEditor, targetChange.getLine1());
      return true;
    }

    private boolean doScrollToContext(@NotNull DiffNavigationContext context) {
      if (myChangedBlockData == null) return false;
      if (myActualContent2 == null) return false;

      ChangedLinesIterator changedLinesIterator = new ChangedLinesIterator(Side.RIGHT, myChangedBlockData.getDiffChanges());
      NavigationContextChecker checker = new NavigationContextChecker(changedLinesIterator, context);
      int line = checker.contextMatchCheck();
      if (line == -1) {
        // this will work for the case, when spaces changes are ignored, and corresponding fragments are not reported as changed
        // just try to find target line  -> +-
        AllLinesIterator allLinesIterator = new AllLinesIterator(Side.RIGHT);
        NavigationContextChecker checker2 = new NavigationContextChecker(allLinesIterator, context);
        line = checker2.contextMatchCheck();
      }
      if (line == -1) return false;

      return doScrollToLine(Side.RIGHT, new LogicalPosition(line, 0));
    }
  }

  private static class MyFoldingModel extends FoldingModelSupport {
    public MyFoldingModel(@NotNull EditorEx editor, @NotNull Disposable disposable) {
      super(new EditorEx[]{editor}, disposable);
    }

    public void install(@Nullable List<IntPair> changedLines, @NotNull UserDataHolder context, boolean defaultExpanded, int range) {
      Iterator<int[]> it = map(changedLines, new Function<IntPair, int[]>() {
        @Override
        public int[] fun(IntPair line) {
          return new int[]{
            line.val1,
            line.val2};
        }
      });
      install(it, context, defaultExpanded, range);
    }

    @NotNull
    public TIntFunction getLineNumberConvertor() {
      return getLineConvertor(0);
    }
  }
}
