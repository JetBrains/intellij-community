/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.CollectConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class LookupImpl extends LightweightHint implements LookupEx, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.lookup.impl.LookupImpl");

  private final LookupOffsets myOffsets;
  private final Project myProject;
  private final Editor myEditor;
  private final Object myLock = new Object();
  private final JBList myList = new JBList(new CollectionListModel<LookupElement>()) {
    @Override
    protected void processKeyEvent(@NotNull final KeyEvent e) {
      final char keyChar = e.getKeyChar();
      if (keyChar == KeyEvent.VK_ENTER || keyChar == KeyEvent.VK_TAB) {
        IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), true).doWhenDone(
          () -> IdeEventQueue.getInstance().getKeyEventDispatcher().dispatchKeyEvent(e));
        return;
      }

      super.processKeyEvent(e);
    }

    @NotNull
    @Override
    protected ExpandableItemsHandler<Integer> createExpandableItemsHandler() {
      return new CompletionExtender(this);
    }
  };
  final LookupCellRenderer myCellRenderer;

  private final List<LookupListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private PrefixChangeListener myPrefixChangeListener = new PrefixChangeListener.Adapter() {};

  private long myStampShown = 0;
  private boolean myShown = false;
  private boolean myDisposed = false;
  private boolean myHidden = false;
  private boolean mySelectionTouched;
  private FocusDegree myFocusDegree = FocusDegree.FOCUSED;
  private volatile boolean myCalculating;
  private final Advertiser myAdComponent;
  volatile int myLookupTextWidth = 50;
  private boolean myChangeGuard;
  private volatile LookupArranger myArranger;
  private LookupArranger myPresentableArranger;
  private final Map<LookupElement, Font> myCustomFonts = ContainerUtil.createConcurrentWeakMap(10, 0.75f, Runtime.getRuntime().availableProcessors(),
    ContainerUtil.identityStrategy());
  private boolean myStartCompletionWhenNothingMatches;
  boolean myResizePending;
  private boolean myFinishing;
  boolean myUpdating;
  private LookupUi myUi;

  public LookupImpl(Project project, Editor editor, @NotNull LookupArranger arranger) {
    super(new JPanel(new BorderLayout()));
    setForceShowAsPopup(true);
    setCancelOnClickOutside(false);
    setResizable(true);
    AbstractPopup.suppressMacCornerFor(getComponent());

    myProject = project;
    myEditor = InjectedLanguageUtil.getTopLevelEditor(editor);
    myArranger = arranger;
    myPresentableArranger = arranger;

    DaemonCodeAnalyzer.getInstance(myProject).disableUpdateByTimer(this);

    myCellRenderer = new LookupCellRenderer(this);
    myList.setCellRenderer(myCellRenderer);

    myList.setFocusable(false);
    myList.setFixedCellWidth(50);

    // a new top level frame just got the focus. This is important to prevent screen readers
    // from announcing the title of the top level frame when the list is shown (or hidden),
    // as they usually do when a new top-level frame receives the focus.
    AccessibleContextUtil.setParent(myList, myEditor.getContentComponent());

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setBackground(LookupCellRenderer.BACKGROUND_COLOR);

    myList.getExpandableItemsHandler();

    myAdComponent = new Advertiser();

    myOffsets = new LookupOffsets(myEditor);

    final CollectionListModel<LookupElement> model = getListModel();
    addEmptyItem(model);
    updateListHeight(model);

    addListeners();
  }

  private CollectionListModel<LookupElement> getListModel() {
    //noinspection unchecked
    return (CollectionListModel<LookupElement>)myList.getModel();
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
    if (myUi != null) {
      myUi.setCalculating(calculating);
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

    withLock(() -> {
      myPresentableArranger.prefixChanged(this);
      getListModel().removeAll();
      return null;
    });

    if (addAgain) {
      for (final LookupElement item : items) {
        addItem(item, itemMatcher(item));
      }
    }
    refreshUi(true, true);
  }

  public boolean addItem(LookupElement item, PrefixMatcher matcher) {
    LookupElementPresentation presentation = renderItemApproximately(item);
    if (containsDummyIdentifier(presentation.getItemText()) ||
        containsDummyIdentifier(presentation.getTailText()) ||
        containsDummyIdentifier(presentation.getTypeText())) {
      return false;
    }

    updateLookupWidth(item, presentation);
    withLock(() -> {
      myArranger.registerMatcher(item, matcher);
      myArranger.addElement(item, presentation);
      return null;
    });
    return true;
  }

  private static boolean containsDummyIdentifier(@Nullable final String s) {
    return s != null && s.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED);
  }

  public void updateLookupWidth(LookupElement item) {
    updateLookupWidth(item, renderItemApproximately(item));
  }

  private void updateLookupWidth(LookupElement item, LookupElementPresentation presentation) {
    final Font customFont = myCellRenderer.getFontAbleToDisplay(presentation);
    if (customFont != null) {
      myCustomFonts.put(item, customFont);
    }
    int maxWidth = myCellRenderer.updateMaximumWidth(presentation, item);
    myLookupTextWidth = Math.max(maxWidth, myLookupTextWidth);
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
    final CollectConsumer<LookupElementAction> consumer = new CollectConsumer<>();
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
    return withLock(() -> ContainerUtil.findAll(getListModel().toList(), element -> !(element instanceof EmptyLookupItem)));
  }

  public String getAdditionalPrefix() {
    return myOffsets.getAdditionalPrefix();
  }

  void appendPrefix(char c) {
    checkValid();
    myOffsets.appendPrefix(c);
    withLock(() -> {
      myPresentableArranger.prefixChanged(this);
      return null;
    });
    requestResize();
    refreshUi(false, true);
    ensureSelectionVisible(true);
    myPrefixChangeListener.afterAppend(c);
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
      ScrollingUtil.ensureIndexIsVisible(myList, myList.getSelectedIndex(), 1);
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

    ScrollingUtil.ensureRangeIsVisible(myList, top, top + myList.getLastVisibleIndex() - firstVisibleIndex);
  }

  boolean truncatePrefix(boolean preserveSelection) {
    if (!myOffsets.truncatePrefix()) {
      return false;
    }

    if (preserveSelection) {
      markSelectionTouched();
    }

    boolean shouldUpdate = withLock(() -> {
      myPresentableArranger.prefixChanged(this);
      return myPresentableArranger == myArranger;
    });
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

    Pair<List<LookupElement>, Integer> pair = withLock(() -> myPresentableArranger.arrangeItems(this, onExplicitAction || reused));
    List<LookupElement> items = pair.first;
    Integer toSelect = pair.second;
    if (toSelect == null || toSelect < 0 || items.size() > 0 && toSelect >= items.size()) {
      LOG.error("Arranger " + myPresentableArranger + " returned invalid selection index=" + toSelect + "; items=" + items);
      toSelect = 0;
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

  private boolean isSelectionVisible() {
    return ScrollingUtil.isIndexFullyVisible(myList, myList.getSelectedIndex());
  }

  private boolean checkReused() {
    return withLock(() -> {
      if (myPresentableArranger != myArranger) {
        myPresentableArranger = myArranger;
        myOffsets.clearAdditionalPrefix();
        myPresentableArranger.prefixChanged(this);
        return true;
      }
      return false;
    });
  }

  private void updateListHeight(ListModel model) {
    myList.setFixedCellHeight(myCellRenderer.getListCellRendererComponent(myList, model.getElementAt(0), 0, false, false).getPreferredSize().height);

    myList.setVisibleRowCount(Math.min(model.getSize(), UISettings.getInstance().MAX_LOOKUP_LIST_HEIGHT));
  }

  private void addEmptyItem(CollectionListModel<LookupElement> model) {
    LookupElement item = new EmptyLookupItem(myCalculating ? " " : LangBundle.message("completion.no.suggestions"), false);
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
    if (element instanceof EmptyLookupItem) return "";
    return myPresentableArranger.itemPattern(element);
  }

  @Override
  @NotNull
  public PrefixMatcher itemMatcher(@NotNull LookupElement item) {
    if (item instanceof EmptyLookupItem) {
      return new CamelHumpMatcher("");
    }
    return myPresentableArranger.itemMatcher(item);
  }

  public void finishLookup(final char completionChar) {
    finishLookup(completionChar, (LookupElement)myList.getSelectedValue());
  }

  public void finishLookup(char completionChar, @Nullable final LookupElement item) {
    //noinspection deprecation,unchecked
    if (item == null ||
        !item.isValid() ||
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
    boolean plainMatch = ContainerUtil.or(item.getAllLookupStrings(), s -> StringUtil.containsIgnoreCase(s, prefix));
    if (!plainMatch) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CAMEL_HUMPS);
    }

    myFinishing = true;
    ApplicationManager.getApplication().runWriteAction(() -> {
      myEditor.getDocument().startGuardedBlockChecking();
      try {
        insertLookupString(item, getPrefixLength(item));
      }
      finally {
        myEditor.getDocument().stopGuardedBlockChecking();
      }
    });

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
    final String lookupString = getCaseCorrectedLookupString(item);

    final Editor hostEditor = getTopLevelEditor();
    hostEditor.getCaretModel().runForEachCaret(new CaretAction() {
      @Override
      public void perform(Caret caret) {
        EditorModificationUtil.deleteSelectedText(hostEditor);
        final int caretOffset = hostEditor.getCaretModel().getOffset();

        int offset = insertLookupInDocumentWindowIfNeeded(caretOffset, prefix, lookupString);
        hostEditor.getCaretModel().moveToOffset(offset);
        hostEditor.getSelectionModel().removeSelection();
      }
    });

    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private int insertLookupInDocumentWindowIfNeeded(int caretOffset, int prefix, String lookupString) {
    DocumentWindow document = getInjectedDocument(caretOffset);
    if (document == null) return insertLookupInDocument(caretOffset, myEditor.getDocument(), prefix, lookupString);
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    int offset = document.hostToInjected(caretOffset);
    int lookupStart = Math.min(offset, Math.max(offset - prefix, 0));
    int diff = -1;
    if (file != null) {
      List<TextRange> ranges = InjectedLanguageManager.getInstance(myProject)
        .intersectWithAllEditableFragments(file, TextRange.create(lookupStart, offset));
      if (!ranges.isEmpty()) {
        diff = ranges.get(0).getStartOffset() - lookupStart;
        if (ranges.size() == 1 && diff == 0) diff = -1;
      }
    }
    if (diff == -1) return insertLookupInDocument(caretOffset, myEditor.getDocument(), prefix, lookupString);
    return document.injectedToHost(
      insertLookupInDocument(offset, document, prefix - diff, diff == 0 ? lookupString : lookupString.substring(diff))
    );
  }

  private static int insertLookupInDocument(int caretOffset, Document document, int prefix, String lookupString) {
    int lookupStart = Math.min(caretOffset, Math.max(caretOffset - prefix, 0));
    int len = document.getTextLength();
    LOG.assertTrue(lookupStart >= 0 && lookupStart <= len,
                   "ls: " + lookupStart + " caret: " + caretOffset + " prefix:" + prefix + " doc: " + len);
    LOG.assertTrue(caretOffset >= 0 && caretOffset <= len, "co: " + caretOffset + " doc: " + len);
    document.replaceString(lookupStart, caretOffset, lookupString);
    return lookupStart + lookupString.length();
  }

  private String getCaseCorrectedLookupString(LookupElement item) {
    String lookupString = item.getLookupString();
    if (item.isCaseSensitive()) {
      return lookupString;
    }

    final String prefix = itemPattern(item);
    final int length = prefix.length();
    if (length == 0 || !itemMatcher(item).prefixMatches(prefix)) return lookupString;
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
    checkValid();
    assert !myChangeGuard : "already in change";

    myEditor.getDocument().startGuardedBlockChecking();
    myChangeGuard = true;
    boolean result;
    try {
      result = myOffsets.performGuardedChange(change);
    }
    finally {
      myEditor.getDocument().stopGuardedBlockChecking();
      myChangeGuard = false;
    }
    if (!result || myDisposed) {
      hideLookup(false);
      return false;
    }
    if (isVisible()) {
      HintManagerImpl.updateLocation(this, myEditor, myUi.calculatePosition().getLocation());
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
      hideLookup(false);
      return false;
    }

    myAdComponent.showRandomText();

    myUi = new LookupUi(this, myAdComponent, myList, myProject);
    myUi.setCalculating(myCalculating);
    Point p = myUi.calculatePosition().getLocation();
    try {
      HintManagerImpl.getInstanceImpl().showEditorHint(this, myEditor, p, HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING, 0, false,
                                                       HintManagerImpl.createHintHint(myEditor, p, this, HintManager.UNDER).setAwtTooltip(false));
    }
    catch (Exception e) {
      LOG.error(e);
    }

    if (!isVisible() || !myList.isShowing()) {
      hideLookup(false);
      return false;
    }

    return true;
  }

  public Advertiser getAdvertiser() {
    return myAdComponent;
  }

  public boolean mayBeNoticed() {
    return myStampShown > 0 && System.currentTimeMillis() - myStampShown > 300;
  }

  private void addListeners() {
    myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (!myChangeGuard && !myFinishing) {
          hideLookup(false);
        }
      }
    }, this);

    final CaretListener caretListener = new CaretAdapter() {
      @Override
      public void caretPositionChanged(CaretEvent e) {
        if (!myChangeGuard && !myFinishing) {
          hideLookup(false);
        }
      }
    };
    final SelectionListener selectionListener = new SelectionListener() {
      @Override
      public void selectionChanged(final SelectionEvent e) {
        if (!myChangeGuard && !myFinishing) {
          hideLookup(false);
        }
      }
    };
    final EditorMouseListener mouseListener = new EditorMouseAdapter() {
      @Override
      public void mouseClicked(EditorMouseEvent e){
        e.consume();
        hideLookup(false);
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
      public void valueChanged(@NotNull ListSelectionEvent e){
        if (!myUpdating) {
          final LookupElement item = getCurrentItem();
          fireCurrentItemChanged(oldItem, item);
          oldItem = item;
        }
      }

    });

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        setFocusDegree(FocusDegree.FOCUSED);
        markSelectionTouched();

        if (clickCount == 2){
          CommandProcessor.getInstance().executeCommand(myProject, () -> finishLookup(NORMAL_SELECT_CHAR), "", null);
        }
        return true;
      }
    }.installOn(myList);
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
    myArranger.itemSelected(item, completionChar);
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

  private void fireCurrentItemChanged(@Nullable LookupElement oldItem, @Nullable LookupElement currentItem) {
    if (oldItem != currentItem && !myListeners.isEmpty()) {
      LookupEvent event = new LookupEvent(this, currentItem, (char)0);
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
    if (!performGuardedChange(() -> {
      EditorModificationUtil.deleteSelectedText(myEditor);
      int offset = myEditor.getCaretModel().getOffset();
      final int start = offset - presentPrefix.length();
      myEditor.getDocument().replaceString(start, offset, newPrefix);
      myOffsets.clearAdditionalPrefix();
      myEditor.getCaretModel().moveToOffset(start + newPrefix.length());
    })) {
      return;
    }
    withLock(() -> {
      myPresentableArranger.prefixReplaced(this, newPrefix);
      return null;
    });
    refreshUi(true, true);
  }

  @Override
  @Nullable
  public PsiFile getPsiFile() {
    return PsiDocumentManager.getInstance(myProject).getPsiFile(getEditor().getDocument());
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
    Editor editor = getEditor();
    if (editor instanceof EditorWindow) {
      offset = editor.logicalPositionToOffset(((EditorWindow)editor).hostToInjected(myEditor.offsetToLogicalPosition(offset)));
    }
    if (offset > 0) return file.findElementAt(offset - 1);

    return file.findElementAt(0);
  }

  @Nullable
  private DocumentWindow getInjectedDocument(int offset) {
    PsiFile hostFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    if (hostFile != null) {
      // inspired by com.intellij.codeInsight.editorActions.TypedHandler.injectedEditorIfCharTypedIsSignificant()
      for (DocumentWindow documentWindow : InjectedLanguageUtil.getCachedInjectedDocuments(hostFile)) {
        if (documentWindow.isValid() && documentWindow.containsRange(offset, offset)) {
          return documentWindow;
        }
      }
    }
    return null;
  }

  @Override
  @NotNull 
  public Editor getEditor() {
    DocumentWindow documentWindow = getInjectedDocument(myEditor.getCaretModel().getOffset());
    if (documentWindow != null) {
      PsiFile injectedFile = PsiDocumentManager.getInstance(myProject).getPsiFile(documentWindow);
      return InjectedLanguageUtil.getInjectedEditorForInjectedFile(myEditor, injectedFile);
    }
    return myEditor;
  }

  @Override
  @NotNull
  public Editor getTopLevelEditor() {
    return myEditor;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean isPositionedAboveCaret(){
    return myUi != null && myUi.isPositionedAboveCaret();
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
    myOffsets.restorePrefix();
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
    myDisposed = true;
    disposeTrace = DebugUtil.currentStackTrace() + "\n============";
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    staticDisposeTrace = disposeTrace;
  }

  public void refreshUi(boolean mayCheckReused, boolean onExplicitAction) {
    assert !myUpdating;
    LookupElement prevItem = getCurrentItem();
    myUpdating = true;
    try {
      final boolean reused = mayCheckReused && checkReused();
      boolean selectionVisible = isSelectionVisible();
      boolean itemsChanged = updateList(onExplicitAction, reused);
      if (isVisible()) {
        LOG.assertTrue(!ApplicationManager.getApplication().isUnitTestMode());
        myUi.refreshUi(selectionVisible, itemsChanged, reused, onExplicitAction);
      }
    }
    finally {
      myUpdating = false;
      fireCurrentItemChanged(prevItem, getCurrentItem());
    }
  }

  public void markReused() {
    withLock(() -> myArranger = myArranger.createEmptyCopy());
    requestResize();
  }

  public void addAdvertisement(@NotNull final String text, final @Nullable Color bgColor) {
    if (containsDummyIdentifier(text)) {
      return;
    }

    myAdComponent.addAdvertisement(text, bgColor);
    requestResize();
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

  @NotNull
  public Map<LookupElement, List<Pair<String, Object>>> getRelevanceObjects(@NotNull Iterable<LookupElement> items, boolean hideSingleValued) {
    return withLock(() -> myPresentableArranger.getRelevanceObjects(items, hideSingleValued));
  }

  private <T> T withLock(Computable<T> computable) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      HeavyProcessLatch.INSTANCE.stopThreadPrioritizing();
    }
    synchronized (myLock) {
      return computable.compute();
    }
  }

  @SuppressWarnings("unused")
  public void setPrefixChangeListener(PrefixChangeListener listener) {
    myPrefixChangeListener = listener;
  }

  public enum FocusDegree { FOCUSED, SEMI_FOCUSED, UNFOCUSED }

}
