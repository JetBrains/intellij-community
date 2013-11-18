/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CompletionLookupArranger;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.ShowHideIntentionIconLookupAction;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.plaf.beg.BegPopupMenuBorder;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.Alarm;
import com.intellij.util.CollectConsumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LookupImpl extends LightweightHint implements LookupEx, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.lookup.impl.LookupImpl");

  private final LookupOffsets myOffsets;
  private final Project myProject;
  private final Editor myEditor;
  private final JBList myList = new JBList(new CollectionListModel<LookupElement>()) {
    @Override
    protected void processKeyEvent(final KeyEvent e) {
      final char keyChar = e.getKeyChar();
      if (keyChar == KeyEvent.VK_ENTER || keyChar == KeyEvent.VK_TAB) {
        IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), true).doWhenDone(new Runnable() {
          @Override
          public void run() {
            IdeEventQueue.getInstance().getKeyEventDispatcher().dispatchKeyEvent(e);
          }
        });
        return;
      }

      super.processKeyEvent(e);
    }

    ExpandableItemsHandler<Integer> myExtender = new CompletionExtender(this);
    @NotNull
    @Override
    public ExpandableItemsHandler<Integer> getExpandableItemsHandler() {
      return myExtender;
    }
  };
  private final LookupCellRenderer myCellRenderer;
  private Boolean myPositionedAbove = null;

  private final List<LookupListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private long myStampShown = 0;
  private boolean myShown = false;
  private boolean myDisposed = false;
  private boolean myHidden = false;
  private boolean mySelectionTouched;
  private FocusDegree myFocusDegree = FocusDegree.FOCUSED;
  private final AsyncProcessIcon myProcessIcon = new AsyncProcessIcon("Completion progress");
  private final JPanel myIconPanel = new JPanel(new BorderLayout());
  private volatile boolean myCalculating;
  private final Advertiser myAdComponent;
  private volatile String myAdText;
  private volatile int myLookupTextWidth = 50;
  private boolean myChangeGuard;
  private volatile LookupArranger myArranger;
  private LookupArranger myPresentableArranger;
  private final Map<LookupElement, PrefixMatcher> myMatchers = new ConcurrentHashMap<LookupElement, PrefixMatcher>(
    ContainerUtil.<LookupElement>identityStrategy());
  private final Map<LookupElement, Font> myCustomFonts = new ConcurrentWeakHashMap<LookupElement, Font>(
    ContainerUtil.<LookupElement>identityStrategy());
  private LookupHint myElementHint = null;
  private final Alarm myHintAlarm = new Alarm();
  private final JLabel mySortingLabel = new JLabel();
  private final JScrollPane myScrollPane;
  final LookupLayeredPane myLayeredPane = new LookupLayeredPane();
  private final JButton myScrollBarIncreaseButton;
  private boolean myStartCompletionWhenNothingMatches;
  private boolean myResizePending;
  private int myMaximumHeight = Integer.MAX_VALUE;
  private boolean myFinishing;
  private boolean myUpdating;
  private final ModalityState myModalityState;

  public LookupImpl(Project project, Editor editor, @NotNull LookupArranger arranger) {
    super(new JPanel(new BorderLayout()));
    setForceShowAsPopup(true);
    setCancelOnClickOutside(false);
    setResizable(true);
    AbstractPopup.suppressMacCornerFor(getComponent());

    myProject = project;
    myEditor = editor;
    myArranger = arranger;
    myPresentableArranger = arranger;

    myIconPanel.setVisible(false);
    myCellRenderer = new LookupCellRenderer(this);
    myList.setCellRenderer(myCellRenderer);

    myList.setFocusable(false);
    myList.setFixedCellWidth(50);

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setBackground(LookupCellRenderer.BACKGROUND_COLOR);

    myList.getExpandableItemsHandler();

    myScrollBarIncreaseButton = new JButton();
    myScrollBarIncreaseButton.setFocusable(false);
    myScrollBarIncreaseButton.setRequestFocusEnabled(false);

    myScrollPane = new JBScrollPane(myList);
    myScrollPane.setViewportBorder(new EmptyBorder(0, 0, 0, 0));
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(13, -1));
    myScrollPane.getVerticalScrollBar().setUI(new ButtonlessScrollBarUI() {
      @Override
      protected JButton createIncreaseButton(int orientation) {
        return myScrollBarIncreaseButton;
      }
    });
    getComponent().add(myLayeredPane, BorderLayout.CENTER);

    //IDEA-82111
    fixMouseCheaters();

    myLayeredPane.mainPanel.add(myScrollPane, BorderLayout.CENTER);
    myScrollPane.setBorder(null);

    myAdComponent = new Advertiser();
    JComponent adComponent = myAdComponent.getAdComponent();
    adComponent.setBorder(new EmptyBorder(0, 1, 1, 2 + AllIcons.Ide.LookupRelevance.getIconWidth()));
    myLayeredPane.mainPanel.add(adComponent, BorderLayout.SOUTH);
    getComponent().setBorder(new BegPopupMenuBorder());

    myIconPanel.setBackground(Color.LIGHT_GRAY);
    myIconPanel.add(myProcessIcon);

    myOffsets = new LookupOffsets(editor);

    final CollectionListModel<LookupElement> model = getListModel();
    addEmptyItem(model);
    updateListHeight(model);


    addListeners();

    mySortingLabel.setBorder(new LineBorder(new JBColor(Color.LIGHT_GRAY, JBColor.background())));
    mySortingLabel.setOpaque(true);
    new ChangeLookupSorting().installOn(mySortingLabel);
    updateSorting();
    myModalityState = ModalityState.stateForComponent(getComponent());
  }

  private CollectionListModel<LookupElement> getListModel() {
    //noinspection unchecked
    return (CollectionListModel<LookupElement>)myList.getModel();
  }

  //Yes, it's possible to move focus to the hint. It's inconvenient, it doesn't make sense, but it's possible.
  // This fix is for those jerks
  private void fixMouseCheaters() {
    getComponent().addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        final ActionCallback done = IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), true);
        IdeFocusManager.getInstance(myProject).typeAheadUntil(done);
        new Alarm(LookupImpl.this).addRequest(new Runnable() {
          @Override
          public void run() {
            if (!done.isDone()) {
              done.setDone();
            }
          }
        }, 300, myModalityState);
      }
    });
  }

  void updateSorting() {
    final boolean lexi = UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY;
    mySortingLabel.setIcon(lexi ? AllIcons.Ide.LookupAlphanumeric : AllIcons.Ide.LookupRelevance);
    mySortingLabel.setToolTipText(lexi ? "Click to sort variants by relevance" : "Click to sort variants alphabetically");

    resort(false);
  }

  public void setArranger(LookupArranger arranger) {
    myArranger = arranger;
  }

  public FocusDegree getFocusDegree() {
    return myFocusDegree;
  }

  @Override
  public boolean isFocused() {
    return getFocusDegree() == FocusDegree.FOCUSED;
  }

  public void setFocusDegree(FocusDegree focusDegree) {
    myFocusDegree = focusDegree;
  }

  public boolean isCalculating() {
    return myCalculating;
  }

  public void setCalculating(final boolean calculating) {
    myCalculating = calculating;
    Runnable setVisible = new Runnable() {
      @Override
      public void run() {
        myIconPanel.setVisible(myCalculating);
      }
    };
    if (myCalculating) {
      new Alarm(this).addRequest(setVisible, 100, myModalityState);
    } else {
      setVisible.run();
    }

    if (calculating) {
      myProcessIcon.resume();
    } else {
      myProcessIcon.suspend();
    }
  }

  public void markSelectionTouched() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    mySelectionTouched = true;
    myList.repaint();
  }

  @TestOnly
  public void setSelectionTouched(boolean selectionTouched) {
    mySelectionTouched = selectionTouched;
  }

  public void resort(boolean addAgain) {
    final List<LookupElement> items = getItems();

    synchronized (myList) {
      myPresentableArranger.prefixChanged(this);
      getListModel().removeAll();
    }

    if (addAgain) {
      for (final LookupElement item : items) {
        addItem(item, itemMatcher(item));
      }
    }
    refreshUi(true, true);
  }

  public void addItem(LookupElement item, PrefixMatcher matcher) {
    myMatchers.put(item, matcher);
    LookupElementPresentation presentation = updateLookupWidth(item);
    synchronized (myList) {
      myArranger.addElement(this, item, presentation);
    }
  }

  public LookupElementPresentation updateLookupWidth(LookupElement item) {
    final LookupElementPresentation presentation = renderItemApproximately(item);
    final Font customFont = myCellRenderer.getFontAbleToDisplay(presentation);
    if (customFont != null) {
      myCustomFonts.put(item, customFont);
    }
    int maxWidth = myCellRenderer.updateMaximumWidth(presentation, item);
    myLookupTextWidth = Math.max(maxWidth, myLookupTextWidth);
    return presentation;
  }

  @Nullable
  public Font getCustomFont(LookupElement item, boolean bold) {
    Font font = myCustomFonts.get(item);
    return font == null ? null : bold ? font.deriveFont(Font.BOLD) : font;
  }

  public void requestResize() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myResizePending = true;
  }

  public Collection<LookupElementAction> getActionsFor(LookupElement element) {
    final CollectConsumer<LookupElementAction> consumer = new CollectConsumer<LookupElementAction>();
    for (LookupActionProvider provider : LookupActionProvider.EP_NAME.getExtensions()) {
      provider.fillActions(element, this, consumer);
    }
    if (!consumer.getResult().isEmpty()) {
      consumer.consume(new ShowHideIntentionIconLookupAction());
    }
    return consumer.getResult();
  }

  public JList getList() {
    return myList;
  }

  @Override
  public List<LookupElement> getItems() {
    synchronized (myList) {
      return ContainerUtil.findAll(getListModel().toList(), new Condition<LookupElement>() {
        @Override
        public boolean value(LookupElement element) {
          return !(element instanceof EmptyLookupItem);
        }
      });
    }
  }

  public void setAdvertisementText(@Nullable String text) {
    myAdText = text;
    if (StringUtil.isNotEmpty(text)) {
      addAdvertisement(text, null);
    }
  }

  public String getAdvertisementText() {
    return myAdText;
  }


  public String getAdditionalPrefix() {
    return myOffsets.getAdditionalPrefix();
  }

  void appendPrefix(char c) {
    checkValid();
    myOffsets.appendPrefix(c);
    synchronized (myList) {
      myPresentableArranger.prefixChanged(this);
    }
    requestResize();
    refreshUi(false, true);
    ensureSelectionVisible(true);
  }

  public void setStartCompletionWhenNothingMatches(boolean startCompletionWhenNothingMatches) {
    myStartCompletionWhenNothingMatches = startCompletionWhenNothingMatches;
  }

  public boolean isStartCompletionWhenNothingMatches() {
    return myStartCompletionWhenNothingMatches;
  }

  public void ensureSelectionVisible(boolean forceTopSelection) {
    if (isSelectionVisible() && !forceTopSelection) {
      return;
    }

    if (!forceTopSelection) {
      ListScrollingUtil.ensureIndexIsVisible(myList, myList.getSelectedIndex(), 1);
      return;
    }

    // selected item should be at the top of the visible list 
    int top = myList.getSelectedIndex();
    if (top > 0) {
      top--; // show one element above the selected one to give the hint that there are more available via scrolling
    }
    
    int firstVisibleIndex = myList.getFirstVisibleIndex();
    if (firstVisibleIndex == top) {
      return;
    }
    
    ListScrollingUtil.ensureRangeIsVisible(myList, top, top + myList.getLastVisibleIndex() - firstVisibleIndex);
  }

  boolean truncatePrefix(boolean preserveSelection) {
    if (!myOffsets.truncatePrefix()) {
      return false;
    }

    if (preserveSelection) {
      markSelectionTouched();
    }

    boolean shouldUpdate;
    synchronized (myList) {
      shouldUpdate = myPresentableArranger == myArranger;
      myPresentableArranger.prefixChanged(this);
    }
    requestResize();
    if (shouldUpdate) {
      refreshUi(false, true);
      ensureSelectionVisible(true);
    }

    return true;
  }

  private boolean updateList(boolean onExplicitAction, boolean reused) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    checkValid();

    CollectionListModel<LookupElement> listModel = getListModel();
    synchronized (myList) {
      Pair<List<LookupElement>, Integer> pair = myPresentableArranger.arrangeItems(this, onExplicitAction || reused);
      List<LookupElement> items = pair.first;
      Integer toSelect = pair.second;
      if (toSelect == null || toSelect < 0 || items.size() > 0 && toSelect >= items.size()) {
        LOG.error("Arranger " + myPresentableArranger + " returned invalid selection index=" + toSelect + "; items=" + items);
      }

      myOffsets.checkMinPrefixLengthChanges(items, this);
      List<LookupElement> oldModel = listModel.toList();

      listModel.removeAll();
      if (!items.isEmpty()) {
        listModel.add(items);
      }
      else {
        addEmptyItem(listModel);
      }

      updateListHeight(listModel);

      myList.setSelectedIndex(toSelect);
      return !ContainerUtil.equalsIdentity(oldModel, items);
    }

  }

  private boolean isSelectionVisible() {
    return ListScrollingUtil.isIndexFullyVisible(myList, myList.getSelectedIndex());
  }

  private boolean checkReused() {
    synchronized (myList) {
      if (myPresentableArranger != myArranger) {
        myPresentableArranger = myArranger;
        myOffsets.clearAdditionalPrefix();
        myPresentableArranger.prefixChanged(this);
        return true;
      }
      return false;
    }
  }

  private void updateListHeight(ListModel model) {
    myList.setFixedCellHeight(myCellRenderer.getListCellRendererComponent(myList, model.getElementAt(0), 0, false, false).getPreferredSize().height);

    myList.setVisibleRowCount(Math.min(model.getSize(), UISettings.getInstance().MAX_LOOKUP_LIST_HEIGHT));
  }

  private void addEmptyItem(CollectionListModel<LookupElement> model) {
    LookupItem<String> item = new EmptyLookupItem(myCalculating ? " " : LangBundle.message("completion.no.suggestions"), false);
    myMatchers.put(item, new CamelHumpMatcher(""));
    model.add(item);

    updateLookupWidth(item);
    requestResize();
  }

  private static LookupElementPresentation renderItemApproximately(LookupElement item) {
    final LookupElementPresentation p = new LookupElementPresentation();
    item.renderElement(p);
    return p;
  }

  @NotNull
  @Override
  public String itemPattern(@NotNull LookupElement element) {
    String prefix = itemMatcher(element).getPrefix();
    String additionalPrefix = getAdditionalPrefix();
    return additionalPrefix.isEmpty() ? prefix : prefix + additionalPrefix;
  }

  @Override
  @NotNull
  public PrefixMatcher itemMatcher(@NotNull LookupElement item) {
    PrefixMatcher matcher = itemMatcherNullable(item);
    if (matcher == null) {
      throw new AssertionError("Item not in lookup: item=" + item + "; lookup items=" + getItems());
    }
    return matcher;
  }

  public PrefixMatcher itemMatcherNullable(LookupElement item) {
    return myMatchers.get(item);
  }

  // in layered pane coordinate system.
  private Rectangle calculatePosition() {
    Dimension dim = getComponent().getPreferredSize();
    int lookupStart = getLookupStart();
    if (lookupStart < 0 || lookupStart > myEditor.getDocument().getTextLength()) {
      LOG.error(lookupStart + "; offset=" + myEditor.getCaretModel().getOffset() + "; element=" +
                getPsiElement());
    }

    LogicalPosition pos = myEditor.offsetToLogicalPosition(lookupStart);
    Point location = myEditor.logicalPositionToXY(pos);
    location.y += myEditor.getLineHeight();
    location.x -= myCellRenderer.getIconIndent() + getComponent().getInsets().left;

    SwingUtilities.convertPointToScreen(location, myEditor.getContentComponent());
    final Rectangle screenRectangle = ScreenUtil.getScreenRectangle(location);

    if (!isPositionedAboveCaret()) {
      int shiftLow = screenRectangle.height - (location.y + dim.height);
      myPositionedAbove = shiftLow < 0 && shiftLow < location.y - dim.height && location.y >= dim.height;
    }
    if (isPositionedAboveCaret()) {
      location.y -= dim.height + myEditor.getLineHeight();
      if (pos.line == 0) {
        location.y += 1;
        //otherwise the lookup won't intersect with the editor and every editor's resize (e.g. after typing in console) will close the lookup
      }
    }

    if (!screenRectangle.contains(location)) {
      location = ScreenUtil.findNearestPointOnBorder(screenRectangle, location);
    }

    final JRootPane rootPane = myEditor.getComponent().getRootPane();
    if (rootPane == null) {
      LOG.error(myEditor.isDisposed() + "; shown=" + myShown + "; disposed=" + myDisposed + "; editorShowing=" + myEditor.getContentComponent().isShowing());
    }
    Rectangle candidate = new Rectangle(location, dim);
    ScreenUtil.cropRectangleToFitTheScreen(candidate);

    SwingUtilities.convertPointFromScreen(location, rootPane.getLayeredPane());
    return new Rectangle(location.x, location.y, dim.width, candidate.height);
  }

  public void finishLookup(final char completionChar) {
    finishLookup(completionChar, (LookupElement)myList.getSelectedValue());
  }

  public void finishLookup(char completionChar, @Nullable final LookupElement item) {
    //noinspection deprecation,unchecked
    if (item == null ||
        item instanceof EmptyLookupItem ||
        item.getObject() instanceof DeferredUserLookupValue &&
        item.as(LookupItem.CLASS_CONDITION_KEY) != null &&
        !((DeferredUserLookupValue)item.getObject()).handleUserSelection(item.as(LookupItem.CLASS_CONDITION_KEY), myProject)) {
      doHide(false, true);
      fireItemSelected(null, completionChar);
      return;
    }

    if (myDisposed) { // DeferredUserLookupValue could close us in any way
      return;
    }

    final PsiFile file = getPsiFile();
    boolean writableOk = file == null || FileModificationService.getInstance().prepareFileForWrite(file);
    if (myDisposed) { // ensureFilesWritable could close us by showing a dialog
      return;
    }

    if (!writableOk) {
      doHide(false, true);
      fireItemSelected(null, completionChar);
      return;
    }

    final String prefix = itemPattern(item);
    boolean plainMatch = ContainerUtil.or(item.getAllLookupStrings(), new Condition<String>() {
      @Override
      public boolean value(String s) {
        return StringUtil.containsIgnoreCase(s, prefix);
      }
    });
    if (!plainMatch) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CAMEL_HUMPS);
    }

    myFinishing = true;
    AccessToken token = WriteAction.start();
    try {
      insertLookupString(item, getPrefixLength(item));
    }
    finally {
      token.finish();
    }

    if (myDisposed) { // any document listeners could close us
      return;
    }

    doHide(false, true);

    fireItemSelected(item, completionChar);
  }

  public int getPrefixLength(LookupElement item) {
    return myOffsets.getPrefixLength(item, this);
  }

  private void insertLookupString(LookupElement item, final int prefix) {
    Document document = myEditor.getDocument();

    String lookupString = getCaseCorrectedLookupString(item);

    if (myEditor.getSelectionModel().hasBlockSelection()) {
      LogicalPosition blockStart = myEditor.getSelectionModel().getBlockStart();
      LogicalPosition blockEnd = myEditor.getSelectionModel().getBlockEnd();
      assert blockStart != null && blockEnd != null;

      int minLine = Math.min(blockStart.line, blockEnd.line);
      int maxLine = Math.max(blockStart.line, blockEnd.line);
      int minColumn = Math.min(blockStart.column, blockEnd.column);
      int maxColumn = Math.max(blockStart.column, blockEnd.column);

      int caretLine = document.getLineNumber(myEditor.getCaretModel().getOffset());

      for (int line = minLine; line <= maxLine; line++) {
        int bs = myEditor.logicalPositionToOffset(new LogicalPosition(line, minColumn));
        int start = bs - prefix;
        int end = myEditor.logicalPositionToOffset(new LogicalPosition(line, maxColumn));
        if (start > end) {
          LOG.error("bs=" + bs + "; start=" + start + "; end=" + end +
                    "; blockStart=" + blockStart + "; blockEnd=" + blockEnd + "; line=" + line + "; len=" +
                    (document.getLineEndOffset(line) - document.getLineStartOffset(line)));
        }
        document.replaceString(start, end, lookupString);
      }
      LogicalPosition start = new LogicalPosition(minLine, minColumn - prefix);
      LogicalPosition end = new LogicalPosition(maxLine, start.column + lookupString.length());
      myEditor.getSelectionModel().setBlockSelection(start, end);
      myEditor.getCaretModel().moveToLogicalPosition(new LogicalPosition(caretLine, end.column));
    } else {
      EditorModificationUtil.deleteSelectedText(myEditor);
      final int caretOffset = myEditor.getCaretModel().getOffset();
      int lookupStart = caretOffset - prefix;

      int len = document.getTextLength();
      LOG.assertTrue(lookupStart >= 0 && lookupStart <= len,
                     "ls: " + lookupStart + " caret: " + caretOffset + " prefix:" + prefix + " doc: " + len);
      LOG.assertTrue(caretOffset >= 0 && caretOffset <= len, "co: " + caretOffset + " doc: " + len);

      document.replaceString(lookupStart, caretOffset, lookupString);

      int offset = lookupStart + lookupString.length();
      myEditor.getCaretModel().moveToOffset(offset);
      myEditor.getSelectionModel().removeSelection();
    }

    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private String getCaseCorrectedLookupString(LookupElement item) {
    String lookupString = item.getLookupString();
    if (item.isCaseSensitive()) {
      return lookupString;
    }

    final String prefix = itemPattern(item);
    final int length = prefix.length();
    if (length == 0 || !StringUtil.startsWithIgnoreCase(lookupString, prefix)) return lookupString;
    boolean isAllLower = true;
    boolean isAllUpper = true;
    boolean sameCase = true;
    for (int i = 0; i < length && (isAllLower || isAllUpper || sameCase); i++) {
      final char c = prefix.charAt(i);
      boolean isLower = Character.isLowerCase(c);
      boolean isUpper = Character.isUpperCase(c);
      // do not take this kind of symbols into account ('_', '@', etc.)
      if (!isLower && !isUpper) continue;
      isAllLower = isAllLower && isLower;
      isAllUpper = isAllUpper && isUpper;
      sameCase = sameCase && isLower == Character.isLowerCase(lookupString.charAt(i));
    }
    if (sameCase) return lookupString;
    if (isAllLower) return lookupString.toLowerCase();
    if (isAllUpper) return StringUtil.toUpperCase(lookupString);
    return lookupString;
  }

  @Override
  public int getLookupStart() {
    return myOffsets.getLookupStart(disposeTrace);
  }

  public int getLookupOriginalStart() {
    return myOffsets.getLookupOriginalStart();
  }

  public boolean performGuardedChange(Runnable change) {
    return performGuardedChange(change, null);
  }

  public boolean performGuardedChange(Runnable change, @Nullable final String debug) {
    checkValid();
    assert !myChangeGuard : "already in change";

    myChangeGuard = true;
    boolean result;
    try {
      result = myOffsets.performGuardedChange(change, debug);
    }
    finally {
      myChangeGuard = false;
    }
    if (!result || myDisposed) {
      hide();
      return false;
    }
    if (isVisible()) {
      updateLookupLocation();
    }
    checkValid();
    return true;
  }

  @Override
  public boolean vetoesHiding() {
    return myChangeGuard;
  }

  public boolean isAvailableToUser() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myShown;
    }
    return isVisible();
  }

  public boolean isShown() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    return myShown;
  }

  public boolean showLookup() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkValid();
    LOG.assertTrue(!myShown);
    myShown = true;
    myStampShown = System.currentTimeMillis();

    if (ApplicationManager.getApplication().isUnitTestMode()) return true;

    if (!myEditor.getContentComponent().isShowing()) {
      hide();
      return false;
    }

    myAdComponent.showRandomText();

    getComponent().setBorder(null);
    updateScrollbarVisibility();

    Rectangle bounds = calculatePosition();
    myMaximumHeight = bounds.height;
    Point p = bounds.getLocation();
    HintManagerImpl.getInstanceImpl().showEditorHint(this, myEditor, p, HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING, 0, false,
                                                     HintManagerImpl.createHintHint(myEditor, p, this, HintManager.UNDER).setAwtTooltip(false));

    if (!isVisible()) {
      hide();
      return false;
    }

    LOG.assertTrue(myList.isShowing(), "!showing, disposed=" + myDisposed);

    return true;
  }

  public boolean mayBeNoticed() {
    return myStampShown > 0 && System.currentTimeMillis() - myStampShown > 300;
  }

  private void addListeners() {
    myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (!myChangeGuard && !myFinishing) {
          hide();
        }
      }
    }, this);

    final CaretListener caretListener = new CaretListener() {
      @Override
      public void caretPositionChanged(CaretEvent e) {
        if (!myChangeGuard && !myFinishing) {
          hide();
        }
      }
    };
    final SelectionListener selectionListener = new SelectionListener() {
      @Override
      public void selectionChanged(final SelectionEvent e) {
        if (!myChangeGuard && !myFinishing) {
          hide();
        }
      }
    };
    final EditorMouseListener mouseListener = new EditorMouseAdapter() {
      @Override
      public void mouseClicked(EditorMouseEvent e){
        e.consume();
        hide();
      }
    };

    myEditor.getCaretModel().addCaretListener(caretListener);
    myEditor.getSelectionModel().addSelectionListener(selectionListener);
    myEditor.addEditorMouseListener(mouseListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        myEditor.getCaretModel().removeCaretListener(caretListener);
        myEditor.getSelectionModel().removeSelectionListener(selectionListener);
        myEditor.removeEditorMouseListener(mouseListener);
      }
    });

    JComponent editorComponent = myEditor.getContentComponent();
    if (editorComponent.isShowing()) {
      Disposer.register(this, new UiNotifyConnector(editorComponent, new Activatable() {
        @Override
        public void showNotify() {
        }
  
        @Override
        public void hideNotify() {
          hideLookup(false);
        }
      }));
    }

    myList.addListSelectionListener(new ListSelectionListener() {
      private LookupElement oldItem = null;

      @Override
      public void valueChanged(ListSelectionEvent e){
        myHintAlarm.cancelAllRequests();

        final LookupElement item = getCurrentItem();
        if (oldItem != item && !myList.isEmpty()) { // do not update on temporary model wipe
          fireCurrentItemChanged(item);
          if (myDisposed) { //a listener may have decided to close us, what can we do?
            return;
          }
          oldItem = item;
        }
        if (item != null) {
          updateHint(item);
        }
      }
    });

    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        setFocusDegree(FocusDegree.FOCUSED);
        markSelectionTouched();

        if (clickCount == 2){
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            @Override
            public void run() {
              finishLookup(NORMAL_SELECT_CHAR);
            }
          }, "", null);
        }
        return true;
      }
    }.installOn(myList);

    final Alarm alarm = new Alarm(this);
    myScrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
      @Override
      public void adjustmentValueChanged(AdjustmentEvent e) {
        if (!myShown || myUpdating || myDisposed) return;
        alarm.addRequest(new Runnable() {
          @Override
          public void run() {
            refreshUi(false, false);
          }
        }, 300, myModalityState);
      }
    });
  }

  private void updateHint(@NotNull final LookupElement item) {
    checkValid();
    if (myElementHint != null) {
      myLayeredPane.remove(myElementHint);
      myElementHint = null;
      final JRootPane rootPane = getComponent().getRootPane();
      if (rootPane != null) {
        rootPane.revalidate();
        rootPane.repaint();
      }
    }
    if (!isFocused()) {
      return;
    }

    final Collection<LookupElementAction> actions = getActionsFor(item);
    if (!actions.isEmpty()) {
      myHintAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          assert !myDisposed;
          if (!ShowHideIntentionIconLookupAction.shouldShowLookupHint() ||
              ((CompletionExtender)myList.getExpandableItemsHandler()).isShowing()) {
            return;
          }
          myElementHint = new LookupHint();
          myLayeredPane.add(myElementHint, 20, 0);
          myLayeredPane.layoutHint();
        }
      }, 500, myModalityState);
    }
  }

  @Override
  @Nullable
  public LookupElement getCurrentItem(){
    LookupElement item = (LookupElement)myList.getSelectedValue();
    return item instanceof EmptyLookupItem ? null : item;
  }

  @Override
  public void setCurrentItem(LookupElement item){
    markSelectionTouched();
    myList.setSelectedValue(item, false);
  }

  @Override
  public void addLookupListener(LookupListener listener){
    myListeners.add(listener);
  }

  @Override
  public void removeLookupListener(LookupListener listener){
    myListeners.remove(listener);
  }

  @Override
  public Rectangle getCurrentItemBounds(){
    int index = myList.getSelectedIndex();
    if (index < 0) {
      LOG.error("No selected element, size=" + getListModel().getSize() + "; items" + getItems());
    }
    Rectangle itmBounds = myList.getCellBounds(index, index);
    if (itmBounds == null){
      LOG.error("No bounds for " + index + "; size=" + getListModel().getSize());
      return null;
    }
    Point layeredPanePoint=SwingUtilities.convertPoint(myList,itmBounds.x,itmBounds.y,getComponent());
    itmBounds.x = layeredPanePoint.x;
    itmBounds.y = layeredPanePoint.y;
    return itmBounds;
  }

  public void fireItemSelected(@Nullable final LookupElement item, char completionChar){
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (!myListeners.isEmpty()){
      LookupEvent event = new LookupEvent(this, item, completionChar);
      for (LookupListener listener : myListeners) {
        try {
          listener.itemSelected(event);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private void fireLookupCanceled(final boolean explicitly) {
    if (!myListeners.isEmpty()){
      LookupEvent event = new LookupEvent(this, explicitly);
      for (LookupListener listener : myListeners) {
        try {
          listener.lookupCanceled(event);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private void fireCurrentItemChanged(LookupElement item){
    if (!myListeners.isEmpty()){
      LookupEvent event = new LookupEvent(this, item, (char)0);
      for (LookupListener listener : myListeners) {
        listener.currentItemChanged(event);
      }
    }
  }

  public boolean fillInCommonPrefix(boolean explicitlyInvoked) {
    if (explicitlyInvoked) {
      setFocusDegree(FocusDegree.FOCUSED);
    }

    if (explicitlyInvoked && myCalculating) return false;
    if (!explicitlyInvoked && mySelectionTouched) return false;

    ListModel listModel = getListModel();
    if (listModel.getSize() <= 1) return false;

    if (listModel.getSize() == 0) return false;

    final LookupElement firstItem = (LookupElement)listModel.getElementAt(0);
    if (listModel.getSize() == 1 && firstItem instanceof EmptyLookupItem) return false;

    final PrefixMatcher firstItemMatcher = itemMatcher(firstItem);
    final String oldPrefix = firstItemMatcher.getPrefix();
    final String presentPrefix = oldPrefix + getAdditionalPrefix();
    String commonPrefix = getCaseCorrectedLookupString(firstItem);

    for (int i = 1; i < listModel.getSize(); i++) {
      LookupElement item = (LookupElement)listModel.getElementAt(i);
      if (item instanceof EmptyLookupItem) return false;
      if (!oldPrefix.equals(itemMatcher(item).getPrefix())) return false;

      final String lookupString = getCaseCorrectedLookupString(item);
      final int length = Math.min(commonPrefix.length(), lookupString.length());
      if (length < commonPrefix.length()) {
        commonPrefix = commonPrefix.substring(0, length);
      }

      for (int j = 0; j < length; j++) {
        if (commonPrefix.charAt(j) != lookupString.charAt(j)) {
          commonPrefix = lookupString.substring(0, j);
          break;
        }
      }

      if (commonPrefix.length() == 0 || commonPrefix.length() < presentPrefix.length()) {
        return false;
      }
    }

    if (commonPrefix.equals(presentPrefix)) {
      return false;
    }

    for (int i = 0; i < listModel.getSize(); i++) {
      LookupElement item = (LookupElement)listModel.getElementAt(i);
      if (!itemMatcher(item).cloneWithPrefix(commonPrefix).prefixMatches(item)) {
        return false;
      }
    }

    myOffsets.setInitialPrefix(presentPrefix, explicitlyInvoked);

    replacePrefix(presentPrefix, commonPrefix);
    return true;
  }

  public void replacePrefix(final String presentPrefix, final String newPrefix) {
    if (!performGuardedChange(new Runnable() {
      @Override
      public void run() {
        EditorModificationUtil.deleteSelectedText(myEditor);
        int offset = myEditor.getCaretModel().getOffset();
        final int start = offset - presentPrefix.length();
        myEditor.getDocument().replaceString(start, offset, newPrefix);

        Map<LookupElement, PrefixMatcher> newMatchers = new HashMap<LookupElement, PrefixMatcher>();
        for (LookupElement item : getItems()) {
          if (item.isValid()) {
            PrefixMatcher matcher = itemMatcher(item).cloneWithPrefix(newPrefix);
            if (matcher.prefixMatches(item)) {
              newMatchers.put(item, matcher);
            }
          }
        }
        myMatchers.clear();
        myMatchers.putAll(newMatchers);

        myOffsets.clearAdditionalPrefix();

        myEditor.getCaretModel().moveToOffset(start + newPrefix.length());
      }
    })) {
      return;
    }
    synchronized (myList) {
      myPresentableArranger.prefixChanged(this);
    }
    refreshUi(true, true);
  }

  @Override
  @Nullable
  public PsiFile getPsiFile() {
    return PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
  }

  @Override
  public boolean isCompletion() {
    return myArranger instanceof CompletionLookupArranger;
  }

  @Override
  public PsiElement getPsiElement() {
    PsiFile file = getPsiFile();
    if (file == null) return null;

    int offset = getLookupStart();
    if (offset > 0) return file.findElementAt(offset - 1);

    return file.findElementAt(0);
  }

  @Override
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  public boolean isPositionedAboveCaret(){
    return myPositionedAbove != null && myPositionedAbove.booleanValue();
  }

  @Override
  public boolean isSelectionTouched() {
    return mySelectionTouched;
  }

  @Override
  public List<String> getAdvertisements() {
    return myAdComponent.getAdvertisements();
  }

  @Override
  public void hide(){
    hideLookup(true);
  }

  public void hideLookup(boolean explicitly) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myHidden) return;

    doHide(true, explicitly);
  }

  private void doHide(final boolean fireCanceled, final boolean explicitly) {
    if (myDisposed) {
      LOG.error(disposeTrace);
    }
    else {
      myHidden = true;

      try {
        super.hide();

        Disposer.dispose(this);

        assert myDisposed;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    if (fireCanceled) {
      fireLookupCanceled(explicitly);
    }
  }

  public void restorePrefix() {
    myOffsets.restorePrefix(getLookupStart());
  }

  private static String staticDisposeTrace = null;
  private String disposeTrace = null;

  public static String getLastLookupDisposeTrace() {
    return staticDisposeTrace;
  }

  @Override
  public void dispose() {
    assert ApplicationManager.getApplication().isDispatchThread();
    assert myHidden;
    if (myDisposed) {
      LOG.error(disposeTrace);
      return;
    }

    myOffsets.disposeMarkers();
    Disposer.dispose(myProcessIcon);
    Disposer.dispose(myHintAlarm);
    myDisposed = true;
    disposeTrace = DebugUtil.currentStackTrace() + "\n============";
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    staticDisposeTrace = disposeTrace;
  }

  public void refreshUi(boolean mayCheckReused, boolean onExplicitAction) {
    assert !myUpdating;
    myUpdating = true;
    try {
      doRefreshUi(mayCheckReused, onExplicitAction);
    }
    finally {
      myUpdating = false;
    }
  }

  private void doRefreshUi(boolean mayCheckReused, boolean onExplicitAction) {
    final boolean reused = mayCheckReused && checkReused();

    boolean selectionVisible = isSelectionVisible();

    boolean itemsChanged = updateList(onExplicitAction, reused);

    if (isVisible()) {
      LOG.assertTrue(!ApplicationManager.getApplication().isUnitTestMode());

      if (myEditor.getComponent().getRootPane() == null) {
        return;
      }

      updateScrollbarVisibility();

      if (myResizePending || itemsChanged) {
        myMaximumHeight = Integer.MAX_VALUE;
      }
      Rectangle rectangle = calculatePosition();
      myMaximumHeight = rectangle.height;

      if (myResizePending || itemsChanged) {
        myResizePending = false;
        pack();
      }
      HintManagerImpl.updateLocation(this, myEditor, rectangle.getLocation());

      if (reused || selectionVisible || onExplicitAction) {
        ensureSelectionVisible(false);
      }
    }
  }

  private void updateLookupLocation() {
    Rectangle rectangle = calculatePosition();
    myMaximumHeight = rectangle.height;
    HintManagerImpl.updateLocation(this, myEditor, rectangle.getLocation());
  }

  private void updateScrollbarVisibility() {
    boolean showSorting = isCompletion() && getListModel().getSize() >= 3;
    mySortingLabel.setVisible(showSorting);
    myScrollPane.setVerticalScrollBarPolicy(showSorting ? ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS : ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
  }

  public void markReused() {
    myAdComponent.clearAdvertisements();
    synchronized (myList) {
      myArranger = myArranger.createEmptyCopy();
    }
    requestResize();
  }

  public void addAdvertisement(@NotNull final String text, final @Nullable Color bgColor) {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (!myDisposed) {
          myAdComponent.addAdvertisement(text, bgColor);
          if (myShown) {
            requestResize();
            refreshUi(false, false);
          }
        }
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    } else {
      ApplicationManager.getApplication().invokeLater(runnable, myModalityState);
    }
  }

  public boolean isLookupDisposed() {
    return myDisposed;
  }

  public void checkValid() {
    if (myDisposed) {
      throw new AssertionError("Disposed at: " + disposeTrace);
    }
  }

  @Override
  public void showItemPopup(JBPopup hint) {
    final Rectangle bounds = getCurrentItemBounds();
    hint.show(new RelativePoint(getComponent(), new Point(bounds.x + bounds.width, bounds.y)));
  }

  @Override
  public boolean showElementActions() {
    if (!isVisible()) return false;

    final LookupElement element = getCurrentItem();
    if (element == null) {
      return false;
    }

    final Collection<LookupElementAction> actions = getActionsFor(element);
    if (actions.isEmpty()) {
      return false;
    }

    showItemPopup(JBPopupFactory.getInstance().createListPopup(new LookupActionsStep(actions, this, element)));
    return true;
  }

  private class LookupLayeredPane extends JBLayeredPane {
    final JPanel mainPanel = new JPanel(new BorderLayout());

    private LookupLayeredPane() {
      add(mainPanel, 0, 0);
      add(myIconPanel, 42, 0);
      add(mySortingLabel, 10, 0);

      setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(@Nullable Container parent) {
          int maxCellWidth = myLookupTextWidth + myCellRenderer.getIconIndent();
          int scrollBarWidth = myScrollPane.getPreferredSize().width - myScrollPane.getViewport().getPreferredSize().width;
          int listWidth = Math.min(scrollBarWidth + maxCellWidth, UISettings.getInstance().MAX_LOOKUP_WIDTH2);

          Dimension adSize = myAdComponent.getAdComponent().getPreferredSize();

          int panelHeight = myList.getPreferredScrollableViewportSize().height + adSize.height;
          if (getListModel().getSize() > myList.getVisibleRowCount() && myList.getVisibleRowCount() >= 5) {
            panelHeight -= myList.getFixedCellHeight() / 2;
          }
          return new Dimension(Math.max(listWidth, adSize.width), Math.min(panelHeight, myMaximumHeight));
        }

        @Override
        public void layoutContainer(Container parent) {
          Dimension size = getSize();
          mainPanel.setSize(size);
          mainPanel.validate();

          if (!myResizePending) {
            Dimension preferredSize = preferredLayoutSize(null);
            if (preferredSize.width != size.width) {
              UISettings.getInstance().MAX_LOOKUP_WIDTH2 = Math.max(500, size.width);
            }

            int listHeight = myList.getLastVisibleIndex() - myList.getFirstVisibleIndex() + 1;
            if (listHeight != getListModel().getSize() && listHeight != myList.getVisibleRowCount() && preferredSize.height != size.height) {
              UISettings.getInstance().MAX_LOOKUP_LIST_HEIGHT = Math.max(5, listHeight);
            }
          }

          myList.setFixedCellWidth(myScrollPane.getViewport().getWidth());
          layoutStatusIcons();
          layoutHint();
        }
      });
    }

    private void layoutStatusIcons() {
      int adHeight = myAdComponent.getAdComponent().getPreferredSize().height;
      Dimension buttonSize = adHeight > 0 || !mySortingLabel.isVisible() ? new Dimension(0, 0) : new Dimension(
        AllIcons.Ide.LookupRelevance.getIconWidth(), AllIcons.Ide.LookupRelevance.getIconHeight());
      myScrollBarIncreaseButton.setPreferredSize(buttonSize);
      myScrollBarIncreaseButton.setMinimumSize(buttonSize);
      myScrollBarIncreaseButton.setMaximumSize(buttonSize);
      JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
      scrollBar.revalidate();
      scrollBar.repaint();

      final Dimension iconSize = myProcessIcon.getPreferredSize();
      myIconPanel.setBounds(getWidth() - iconSize.width - (scrollBar.isVisible() ? scrollBar.getWidth() : 0), 0, iconSize.width, iconSize.height);

      final Dimension sortSize = mySortingLabel.getPreferredSize();
      final Point sbLocation = SwingUtilities.convertPoint(scrollBar, 0, 0, myLayeredPane);

      final int sortHeight = Math.max(adHeight, mySortingLabel.getPreferredSize().height);
      mySortingLabel.setBounds(sbLocation.x, getHeight() - sortHeight, sortSize.width, sortHeight);
    }

    void layoutHint() {
      if (myElementHint != null && getCurrentItem() != null) {
        final Rectangle bounds = getCurrentItemBounds();
        myElementHint.setSize(myElementHint.getPreferredSize());
        JScrollBar sb = myScrollPane.getVerticalScrollBar();
        myElementHint.setLocation(new Point(bounds.x + bounds.width - myElementHint.getWidth() + (sb.isVisible() ? sb.getWidth() : 0), bounds.y));
      }
    }

  }

  private class LookupHint extends JLabel {
    private final Border INACTIVE_BORDER = BorderFactory.createEmptyBorder(2, 2, 2, 2);
    private final Border ACTIVE_BORDER = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 1), BorderFactory.createEmptyBorder(1, 1, 1, 1));
    private LookupHint() {
      setOpaque(false);
      setBorder(INACTIVE_BORDER);
      setIcon(AllIcons.Actions.IntentionBulb);
      String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
              ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
      if (acceleratorsText.length() > 0) {
        setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", acceleratorsText));
      }

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          setBorder(ACTIVE_BORDER);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          setBorder(INACTIVE_BORDER);
        }
        @Override
        public void mousePressed(MouseEvent e) {
          if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
            showElementActions();
          }
        }
      });
    }
  }

  public Map<LookupElement,StringBuilder> getRelevanceStrings() {
    synchronized (myList) {
      return myPresentableArranger.getRelevanceStrings();
    }
  }

  private class ChangeLookupSorting extends ClickListener {

    @Override
    public boolean onClick(MouseEvent e, int clickCount) {
      DataContext context = DataManager.getInstance().getDataContext(mySortingLabel);
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(createSortingAction(true));
      group.add(createSortingAction(false));
      JBPopupFactory.getInstance().createActionGroupPopup("Change sorting", group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false).showInBestPositionFor(
        context);
      return true;
    }

    private AnAction createSortingAction(boolean checked) {
      boolean currentSetting = UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY;
      final boolean newSetting = checked ? currentSetting : !currentSetting;
      return new AnAction(newSetting ? "Sort lexicographically" : "Sort by relevance", null, checked ? PlatformIcons.CHECK_ICON : null) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CHANGE_SORTING);
          UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = newSetting;
          updateSorting();
        }
      };
    }
  }

  public enum FocusDegree { FOCUSED, SEMI_FOCUSED, UNFOCUSED }

}
