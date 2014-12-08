package com.intellij.openapi.util.diff.tools.oneside;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.diff.actions.BufferedLineIterator;
import com.intellij.openapi.util.diff.actions.NavigationContextChecker;
import com.intellij.openapi.util.diff.actions.impl.OpenInEditorWithMouseAction;
import com.intellij.openapi.util.diff.actions.impl.SetEditorSettingsAction;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.comparison.DiffTooBigException;
import com.intellij.openapi.util.diff.contents.DiffContent;
import com.intellij.openapi.util.diff.contents.DocumentContent;
import com.intellij.openapi.util.diff.contents.EmptyContent;
import com.intellij.openapi.util.diff.fragments.LineFragments;
import com.intellij.openapi.util.diff.requests.ContentDiffRequest;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.tools.oneside.OnesideDiffSettingsHolder.OnesideDiffSettings;
import com.intellij.openapi.util.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.util.diff.tools.util.DiffUserDataKeys;
import com.intellij.openapi.util.diff.tools.util.DiffUserDataKeys.ScrollToPolicy;
import com.intellij.openapi.util.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.openapi.util.diff.tools.util.ScrollToLineHelper;
import com.intellij.openapi.util.diff.tools.util.base.TextDiffViewerBase;
import com.intellij.openapi.util.diff.util.CalledInAwt;
import com.intellij.openapi.util.diff.util.DiffUtil;
import com.intellij.openapi.util.diff.util.DiffUtil.DocumentData;
import com.intellij.openapi.util.diff.util.Side;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.text.MergingCharSequence;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

class OnesideDiffViewer extends TextDiffViewerBase {
  public static final Logger LOG = Logger.getInstance(OnesideDiffViewer.class);

  @NotNull private final EditorEx myEditor;
  @NotNull private final Document myDocument;
  @NotNull private final OnesideDiffPanel myPanel;

  @Nullable private final DocumentContent myActualContent1;
  @Nullable private final DocumentContent myActualContent2;

  @NotNull private final OnesideDiffSettings mySettings;

  @NotNull private final MySetEditorSettingsAction myEditorSettingsAction;
  @NotNull private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
  @NotNull private final MyStatusPanel myStatusPanel;

  @NotNull private final MyScrollToLineHelper myScrollToLineHelper = new MyScrollToLineHelper();

  @NotNull private Side myMasterSide = Side.LEFT;

  @Nullable private ChangedBlockData myChangedBlockData;

  public OnesideDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    myStatusPanel = new MyStatusPanel();


    mySettings = initSettings(context);

    DiffContent[] contents = myRequest.getContents();
    myActualContent1 = contents[0] instanceof DocumentContent ? ((DocumentContent)contents[0]) : null;
    myActualContent2 = contents[1] instanceof DocumentContent ? ((DocumentContent)contents[1]) : null;
    assert myActualContent1 != null || myActualContent2 != null;


    myDocument = EditorFactory.getInstance().createDocument("");
    myEditor = DiffUtil.createEditor(myDocument, myProject, true);
    List<JComponent> titles = DiffUtil.createTextTitles(myRequest);


    OnesideContentPanel contentPanel = new OnesideContentPanel(titles, myEditor);

    myPanel = new OnesideDiffPanel(myProject, contentPanel, myEditor, this, myContext);


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
  public void onDispose() {
    super.onDispose();
    updateContextHints();
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  private void processContextHints() {
    Side side = myContext.getUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE);
    if (side != null) myMasterSide = side;

    myScrollToLineHelper.processContext(myContext, myRequest);
  }

  private void updateContextHints() {
    int onesideLine = DiffUtil.getLineToScroll(myEditor);
    Pair<Side, Integer> pair = transferLineFromOneside(onesideLine);
    myRequest.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, pair);
  }

  @NotNull
  public List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(new MyComparisonPolicySettingAction());
    group.add(new MyInlineHighlightSettingAction());
    group.add(new MyContextRangeSettingAction());
    group.add(myEditorSettingsAction);

    return group;
  }

  //
  // Diff
  //

  @Override
  protected void onSlowRediff() {
    myPanel.setLoadingContent();
  }

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    try {
      indicator.checkCanceled();
      assert myActualContent1 != null || myActualContent2 != null;

      if (myActualContent1 == null) {
        DocumentContent content = myActualContent2;
        final Document document = content.getDocument();

        OnesideDocumentData data = ApplicationManager.getApplication().runReadAction(new Computable<OnesideDocumentData>() {
          @Override
          public OnesideDocumentData compute() {
            return new OnesideDocumentData(document.getImmutableCharSequence(), getLineCount(document));
          }
        });

        List<ChangedBlock> blocks = new ArrayList<ChangedBlock>();
        blocks.add(ChangedBlock.createInserted(data.getText().length() + 1, data.getLines()));

        indicator.checkCanceled();
        EditorHighlighter highlighter = DiffUtil.createEditorHighlighter(myProject, content);
        LineNumberConvertor convertor = LineNumberConvertor.Builder.createLeft(data.getLines());
        LineNumberConvertor invertedConvertor = LineNumberConvertor.Builder.createInvertedLeft(data.getLines());

        CombinedEditorData editorData = new CombinedEditorData(new MergingCharSequence(data.getText(), "\n"), highlighter,
                                                               content.getContentType(), convertor.getConvertor1(), null);

        return apply(editorData, blocks, convertor, invertedConvertor, Collections.<Integer>emptyList(), false);
      }

      if (myActualContent2 == null) {
        DocumentContent content = myActualContent1;
        final Document document = content.getDocument();

        OnesideDocumentData data = ApplicationManager.getApplication().runReadAction(new Computable<OnesideDocumentData>() {
          @Override
          public OnesideDocumentData compute() {
            return new OnesideDocumentData(document.getImmutableCharSequence(), getLineCount(document));
          }
        });

        List<ChangedBlock> blocks = new ArrayList<ChangedBlock>();
        blocks.add(ChangedBlock.createDeleted(data.getText().length() + 1, data.getLines()));

        indicator.checkCanceled();
        EditorHighlighter highlighter = DiffUtil.createEditorHighlighter(myProject, content);
        LineNumberConvertor convertor = LineNumberConvertor.Builder.createRight(data.getLines());
        LineNumberConvertor invertedConvertor = LineNumberConvertor.Builder.createInvertedRight(data.getLines());

        CombinedEditorData editorData = new CombinedEditorData(new MergingCharSequence(data.getText(), "\n"), highlighter,
                                                               content.getContentType(), convertor.getConvertor2(), null);

        return apply(editorData, blocks, convertor, invertedConvertor, Collections.<Integer>emptyList(), false);
      }

      DocumentContent content1 = myActualContent1;
      DocumentContent content2 = myActualContent2;
      final Document document1 = content1.getDocument();
      final Document document2 = content2.getDocument();

      DocumentData data = ApplicationManager.getApplication().runReadAction(new Computable<DocumentData>() {
        @Override
        public DocumentData compute() {
          return new DocumentData(document1.getImmutableCharSequence(), document2.getImmutableCharSequence(),
                                  document1.getModificationStamp(), document2.getModificationStamp());
        }
      });

      LineFragments fragments = DiffUtil.compareWithCache(myRequest, data, getDiffConfig(), indicator);

      indicator.checkCanceled();
      OnesideFragmentBuilder builder = new OnesideFragmentBuilder(fragments, document1, document2,
                                                                  mySettings.getContextRange(),
                                                                  getTextSettings().isInlineHighlight(),
                                                                  myMasterSide);
      builder.exec();

      indicator.checkCanceled();

      EditorHighlighter highlighter = buildHighlighter(myProject, content1, content2,
                                                       data.getText1(), data.getText2(), builder.getRanges(), builder.getText().length());
      FileType fileType = content2.getContentType() == null ? content1.getContentType() : content2.getContentType();

      LineNumberConvertor convertor = builder.getConvertor();
      LineNumberConvertor invertedConvertor = builder.getInvertedConvertor();
      List<Integer> separatorLines = builder.getSeparatorLines();
      boolean isEqual = builder.isEqual();

      CombinedEditorData editorData = new CombinedEditorData(builder.getText(), highlighter, fileType,
                                                             convertor.getConvertor1(), convertor.getConvertor2());

      return apply(editorData, builder.getBlocks(), convertor, invertedConvertor, separatorLines, isEqual);
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
    catch (Exception e) {
      LOG.error(e);
      return new Runnable() {
        @Override
        public void run() {
          clearDiffPresentation();
          myPanel.setErrorContent();
        }
      };
    }
    catch (final Error e) {
      return new Runnable() {
        @Override
        public void run() {
          clearDiffPresentation();
          myPanel.setErrorContent();
          throw e;
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
                         @NotNull final LineNumberConvertor invertedConvertor,
                         @NotNull final List<Integer> separatorLines,
                         final boolean isEqual) {
    return new Runnable() {
      @Override
      public void run() {
        clearDiffPresentation();
        if (isEqual) myPanel.addContentsEqualNotification();


        if (data.getLineConvertor2() == null) {
          myEditor.getGutterComponentEx().setLineNumberConvertor(data.getLineConvertor1());
        }
        else {
          myEditor.getGutterComponentEx().setLineNumberConvertor(data.getLineConvertor1(), data.getLineConvertor2());
        }

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            myDocument.setText(data.getText());
          }
        });

        if (data.getHighlighter() != null) myEditor.setHighlighter(data.getHighlighter());
        DiffUtil.setEditorCodeStyle(myProject, myEditor, data.getFileType());


        ArrayList<OnesideDiffChange> diffChanges = new ArrayList<OnesideDiffChange>(blocks.size());
        for (ChangedBlock block : blocks) {
          diffChanges.add(new OnesideDiffChange(myEditor, block));
        }

        ArrayList<OnesideDiffSeparator> diffSeparators = new ArrayList<OnesideDiffSeparator>(separatorLines.size());
        for (Integer line : separatorLines) {
          diffSeparators.add(new OnesideDiffSeparator(myEditor, line));
        }

        myChangedBlockData = new ChangedBlockData(diffChanges, convertor, invertedConvertor, diffSeparators);

        // TODO: remember current in current caret position position on rediff rather than positon in combined file
        myScrollToLineHelper.scrollOnRediff();

        myStatusPanel.update();
        myPanel.setGoodContent();
      }
    };
  }

  @CalledInAwt
  private int transferLineToOneside(@NotNull Side side, int line) {
    if (myChangedBlockData == null) return line;

    LineNumberConvertor invertedLineConvertor = myChangedBlockData.getLineNumberInvertedConvertor();
    TIntFunction convertor = side.isLeft() ? invertedLineConvertor.getConvertor1() : invertedLineConvertor.getConvertor2();

    return convertor.execute(line);
  }

  @CalledInAwt
  private Pair<Side, Integer> transferLineFromOneside(int line) {
    if (myChangedBlockData == null) return Pair.create(myMasterSide, line);

    LineNumberConvertor lineConvertor = myChangedBlockData.getLineNumberConvertor();
    TIntFunction convertor1 = lineConvertor.getConvertor1();
    TIntFunction convertor2 = lineConvertor.getConvertor2();

    int line1 = convertor1.execute(line);
    int line2 = convertor2.execute(line);

    if (line1 == -1 && line2 == -1) {
      line1 = convertor1.execute(line - 1);
      line2 = convertor2.execute(line - 1);
      if (line1 != -1) line1++;
      if (line2 != -1) line2++;
    }
    if (line1 == -1 && line2 == -1) {
      line1 = convertor1.execute(line + 1);
      line2 = convertor2.execute(line + 1);
      if (line1 > 0) line1--;
      if (line2 > 0) line2--;
    }

    if (line1 == -1 && line2 == -1) {
      return Pair.create(myMasterSide, line);
    }
    if (line1 == -1) {
      return Pair.create(Side.RIGHT, line2);
    }
    if (line2 == -1) {
      return Pair.create(Side.LEFT, line1);
    }

    return myMasterSide.isLeft() ? Pair.create(Side.LEFT, line1) : Pair.create(Side.RIGHT, line2);
  }

  @CalledInAwt
  private void destroyChangedBlockData() {
    if (myChangedBlockData == null) return;

    for (OnesideDiffChange change : myChangedBlockData.getDiffChanges()) {
      change.destroyHighlighter();
    }
    for (OnesideDiffSeparator separator : myChangedBlockData.getDiffSeparators()) {
      separator.destroyHighlighter();
    }
    myChangedBlockData = null;
    myStatusPanel.update();
  }

  //
  // Impl
  //

  @NotNull
  private static OnesideDiffSettings initSettings(@NotNull DiffContext context) {
    OnesideDiffSettings settings = context.getUserData(OnesideDiffSettings.KEY);
    if (settings == null) {
      settings = OnesideDiffSettings.getSettings();
      context.putUserData(OnesideDiffSettings.KEY, settings);
    }
    return settings;
  }

  @NotNull
  private DiffUtil.DiffConfig getDiffConfig() {
    return new DiffUtil.DiffConfig(getTextSettings().getComparisonPolicy(), getTextSettings().isInlineHighlight(), true);
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
  List<OnesideDiffChange> getDiffChanges() {
    return myChangedBlockData == null ? null : myChangedBlockData.getDiffChanges();
  }

  @NotNull
  OnesideDiffSettings getSettings() {
    return mySettings;
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
    return myStatusPanel.getComponent();
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
      OpenFileDescriptor descriptor = myActualContent1.getOpenFileDescriptor(offset);
      if (descriptor != null) return descriptor;
    }
    else if (myActualContent1 == null) {
      OpenFileDescriptor descriptor = myActualContent2.getOpenFileDescriptor(offset);
      if (descriptor != null) return descriptor;
    }
    else {
      Pair<Side, Integer> pair = transferLineFromOneside(myEditor.offsetToLogicalPosition(offset).line);
      OpenFileDescriptor descriptor1 = myActualContent1.getOpenFileDescriptor(offset);
      OpenFileDescriptor descriptor2 = myActualContent2.getOpenFileDescriptor(offset);
      if (descriptor1 == null) return descriptor2;
      if (descriptor2 == null) return descriptor1;
      return pair.first.select(descriptor1, descriptor2);
    }

    return null;
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (!(request instanceof ContentDiffRequest)) return false;

    DiffContent[] contents = ((ContentDiffRequest)request).getContents();
    if (contents.length != 2) return false;

    if (!canShowContent(contents[0])) return false;
    if (!canShowContent(contents[1])) return false;

    if (contents[0] instanceof EmptyContent && contents[1] instanceof EmptyContent) return false;

    return true;
  }

  public static boolean canShowContent(@NotNull DiffContent content) {
    if (content instanceof EmptyContent) return true;
    if (content instanceof DocumentContent) return true;
    return false;
  }

  //
  // Actions
  //

  private class MyPrevNextDifferenceIterable implements PrevNextDifferenceIterable {
    @Override
    public void notify(@NotNull String message) {
      final LightweightHint hint = new LightweightHint(HintUtil.createInformationLabel(message));
      HintManagerImpl.getInstanceImpl().showEditorHint(hint, myEditor, HintManager.UNDER, HintManager.HIDE_BY_ANY_KEY |
                                                                                          HintManager.HIDE_BY_TEXT_CHANGE |
                                                                                          HintManager.HIDE_BY_SCROLLING, 0, false);
    }

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

  private class MyContextRangeSettingAction extends DumbAwareAction {
    private MyContextRangeSettingAction() {
      super("Context Lines...", "More/Less Lines...", AllIcons.Actions.Expandall);
      setEnabledInModalContext(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final int[] modes = OnesideDiffSettings.CONTEXT_RANGE_MODES;
      String[] modeLabels = OnesideDiffSettings.CONTEXT_RANGE_MODE_LABELS;

      //noinspection UseOfObsoleteCollectionType
      Dictionary<Integer, JLabel> sliderLabels = new Hashtable<Integer, JLabel>();
      for (int i = 0; i < modes.length; i++) {
        sliderLabels.put(i, new JLabel(modeLabels[i]));
      }

      JPanel result = new JPanel(new BorderLayout());
      JLabel label = new JLabel("Context Lines:");
      label.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 0));
      JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(label, BorderLayout.NORTH);
      result.add(wrapper, BorderLayout.WEST);
      final JSlider slider = new JSlider(SwingConstants.HORIZONTAL, 0, modes.length - 1, 0);
      slider.setMinorTickSpacing(1);
      slider.setPaintTicks(true);
      slider.setPaintTrack(true);
      slider.setSnapToTicks(true);
      UIUtil.setSliderIsFilled(slider, true);
      slider.setPaintLabels(true);
      slider.setLabelTable(sliderLabels);
      result.add(slider, BorderLayout.CENTER);

      for (int i = 0; i < modes.length; i++) {
        int mark = modes[i];
        if (mark == mySettings.getContextRange()) {
          slider.setValue(i);
        }
      }

      JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(result, slider).createPopup();
      popup.setFinalRunnable(new Runnable() {
        @Override
        public void run() {
          int value = slider.getModel().getValue();
          if (mySettings.getContextRange() != modes[value]) {
            mySettings.setContextRange(modes[value]);
            rediff();
          }
        }
      });
      if (e.getInputEvent() instanceof MouseEvent) {
        MouseEvent inputEvent = ((MouseEvent)e.getInputEvent());
        int width = result.getPreferredSize().width;
        Point point = new Point(inputEvent.getX() - width / 2, inputEvent.getY());
        RelativePoint absPoint = new RelativePoint(inputEvent.getComponent(), point); // TODO: WTF, wrong component - fix positioning
        popup.show(absPoint);
      }
      else {
        popup.showInBestPositionFor(e.getDataContext());
      }
    }
  }

  private class MySetEditorSettingsAction extends SetEditorSettingsAction {
    public MySetEditorSettingsAction() {
      super(getTextSettings());
    }

    @NotNull
    @Override
    public List<? extends Editor> getEditors() {
      return OnesideDiffViewer.this.getEditors();
    }
  }

  private class MyOpenInEditorWithMouseAction extends OpenInEditorWithMouseAction {
    @Override
    protected OpenFileDescriptor getDescriptor(@NotNull Editor editor, int line) {
      if (editor != myEditor) return null;

      return getOpenFileDescriptor(myEditor.logicalPositionToOffset(new LogicalPosition(line, 0)));
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
    else {
      return super.getData(dataId);
    }
  }

  private class MyStatusPanel {
    private final JLabel myTextLabel = new JLabel("");

    @NotNull
    public JComponent getComponent() {
      return myTextLabel;
    }

    public void update() {
      int changes = 0;
      changes += myChangedBlockData == null ? 0 : myChangedBlockData.getDiffChanges().size();
      myTextLabel.setText(DiffBundle.message("diff.count.differences.status.text", changes));
    }
  }

  private static class OnesideDocumentData {
    @NotNull private final CharSequence myText;
    private final int myLines;

    public OnesideDocumentData(@NotNull CharSequence text, int lines) {
      myText = text;
      myLines = lines;
    }

    @NotNull
    public CharSequence getText() {
      return myText;
    }

    public int getLines() {
      return myLines;
    }
  }

  private static class ChangedBlockData {
    @NotNull private final List<OnesideDiffChange> myDiffChanges;
    @NotNull private final LineNumberConvertor myLineNumberConvertor;
    @NotNull private final LineNumberConvertor myLineNumberInvertedConvertor;
    @NotNull private final List<OnesideDiffSeparator> myDiffSeparators;

    public ChangedBlockData(@NotNull List<OnesideDiffChange> diffChanges,
                            @NotNull LineNumberConvertor lineNumberConvertor,
                            @NotNull LineNumberConvertor lineNumberInvertedConvertor,
                            @NotNull List<OnesideDiffSeparator> diffSeparators) {
      myDiffChanges = diffChanges;
      myLineNumberConvertor = lineNumberConvertor;
      myLineNumberInvertedConvertor = lineNumberInvertedConvertor;
      myDiffSeparators = diffSeparators;
    }

    @NotNull
    public List<OnesideDiffChange> getDiffChanges() {
      return myDiffChanges;
    }

    @NotNull
    public LineNumberConvertor getLineNumberConvertor() {
      return myLineNumberConvertor;
    }

    @NotNull
    public LineNumberConvertor getLineNumberInvertedConvertor() {
      return myLineNumberInvertedConvertor;
    }

    @NotNull
    public List<OnesideDiffSeparator> getDiffSeparators() {
      return myDiffSeparators;
    }
  }

  private static class CombinedEditorData {
    @NotNull private final CharSequence myText;
    @Nullable private final EditorHighlighter myHighlighter;
    @Nullable private final FileType myFileType;
    @NotNull private final TIntFunction myLineConvertor1;
    @Nullable private final TIntFunction myLineConvertor2;

    public CombinedEditorData(@NotNull CharSequence text,
                              @Nullable EditorHighlighter highlighter,
                              @Nullable FileType fileType,
                              @NotNull TIntFunction convertor1,
                              @Nullable TIntFunction convertor2) {
      myText = text;
      myHighlighter = highlighter;
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

  private class MyScrollToLineHelper extends ScrollToLineHelper {
    public void scrollOnRediff() {
      if (myShouldScroll && myScrollToChange != null) if (doScrollToChange(myScrollToChange)) onSuccessfulScroll();
      if (myShouldScroll && myScrollToLine != null) if (doScrollToLine(myScrollToLine)) onSuccessfulScroll();
      if (myShouldScroll && myNavigationContext != null) if (doScrollToContext(myNavigationContext)) onSuccessfulScroll();
      if (myShouldScroll) doScrollToChange(ScrollToPolicy.FIRST_CHANGE);
      onSuccessfulScroll();
    }

    private boolean doScrollToLine(@NotNull Pair<Side, Integer> scrollToLine) {
      int onesideLine = transferLineToOneside(scrollToLine.first, scrollToLine.second);
      DiffUtil.scrollToLine(myEditor, onesideLine);
      return true;
    }

    private boolean doScrollToChange(@NotNull ScrollToPolicy scrollToPolicy) {
      if (myChangedBlockData == null) return false;
      List<OnesideDiffChange> changes = myChangedBlockData.getDiffChanges();
      if (changes.isEmpty()) return false;

      OnesideDiffChange targetChange;
      switch (scrollToPolicy) {
        case FIRST_CHANGE:
          targetChange = changes.get(0);
          break;
        case LAST_CHANGE:
          targetChange = changes.get(changes.size() - 1);
          break;
        default:
          throw new IllegalArgumentException(scrollToPolicy.name());
      }

      DiffUtil.scrollToLine(myEditor, targetChange.getLine1());
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

      return doScrollToLine(Pair.create(Side.RIGHT, line));
    }
  }
}
