// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction;
import com.intellij.codeInsight.template.impl.actions.NextVariableAction;
import com.intellij.codeWithMe.ClientId;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.client.ClientProjectSession;
import com.intellij.openapi.client.ClientSessionsManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.CollectConsumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.intellij.codeInsight.lookup.LookupElement.LOOKUP_ELEMENT_SHOW_TIMESTAMP_MILLIS;
import static kotlinx.coroutines.SupervisorKt.SupervisorJob;

public class LookupImpl extends LightweightHint implements LookupEx, Disposable, LookupElementListPresenter {
  private static final Logger LOG = Logger.getInstance(LookupImpl.class);

  private final LookupOffsets myOffsets;
  private final Editor editor;
  private final Object uiLock = new Object();
  private final JBList<LookupElement> list = new LookupList();
  final LookupCellRenderer cellRenderer;

  private final List<LookupListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<PrefixChangeListener> myPrefixChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final LookupPreview myPreview = new LookupPreview(this);
  // keeping our own copy of editor's font preferences, which can be used in non-EDT threads (to avoid race conditions)
  private final FontPreferences myFontPreferences = new FontPreferencesImpl();

  private final long myCreatedTimestamp;
  private final ClientProjectSession mySession;
  private long myStampShown = 0;
  protected boolean myShown = false;
  private boolean myHidden = false;
  private boolean mySelectionTouched;
  private LookupFocusDegree myLookupFocusDegree = LookupFocusDegree.FOCUSED;
  private volatile boolean myCalculating;
  private final Advertiser myAdComponent;
  private int myGuardedChanges;
  private volatile LookupArranger myArranger;
  private LookupArranger myPresentableArranger;
  private boolean myStartCompletionWhenNothingMatches;
  boolean myResizePending;
  private boolean myFinishing;
  boolean myUpdating;
  private LookupUi myUi;
  private LookupPresentation myPresentation = new LookupPresentation.Builder().build();
  private final AtomicInteger myDummyItemCount = new AtomicInteger();
  private final EmptyLookupItem myDummyItem = new EmptyLookupItem(CommonBundle.message("tree.node.loading"), true);
  private boolean myFirstElementAdded = false;

  final CoroutineScope coroutineScope = CoroutineScopeKt.CoroutineScope(SupervisorJob(null).plus(Dispatchers.getDefault()));

  @ApiStatus.Internal
  public LookupImpl(ClientProjectSession session, Editor editor, @NotNull LookupArranger arranger) {
    super(new JPanel(new BorderLayout()));
    setForceShowAsPopup(true);
    setCancelOnClickOutside(false);
    setResizable(true);

    mySession = session;
    this.editor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
    myArranger = arranger;
    myPresentableArranger = arranger;
    this.editor.getColorsScheme().getFontPreferences().copyTo(myFontPreferences);

    DaemonCodeAnalyzer.getInstance(session.getProject()).disableUpdateByTimer(this);

    cellRenderer = new LookupCellRenderer(this, this.editor.getContentComponent());
    cellRenderer.itemAdded(myDummyItem, LookupElementPresentation.renderElement(myDummyItem));
    list.setCellRenderer(cellRenderer);

    list.setFocusable(false);
    list.setFixedCellWidth(50);
    list.setBorder(null);

    // a new top level frame just got the focus. This is important to prevent screen readers
    // from announcing the title of the top level frame when the list is shown (or hidden),
    // as they usually do when a new top-level frame receives the focus.
    // This is not relevant on Mac. This breaks JBR a11y on Mac.
    if (SystemInfoRt.isWindows) {
      AccessibleContextUtil.setParent(list, this.editor.getContentComponent());
    }

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setBackground(LookupCellRenderer.BACKGROUND_COLOR);

    if (ExperimentalUI.isNewUI()) {
      myAdComponent = new NewUILookupAdvertiser();
    }
    else {
      myAdComponent = new Advertiser();
      myAdComponent.setBackground(LookupCellRenderer.BACKGROUND_COLOR);
    }

    myOffsets = new LookupOffsets(this.editor);

    final CollectionListModel<LookupElement> model = getListModel();
    addEmptyItem(model);
    updateListHeight(model);

    addListeners();

    myCreatedTimestamp = System.currentTimeMillis();
  }

  private CollectionListModelWithBatchUpdate<LookupElement> getListModel() {
    return (CollectionListModelWithBatchUpdate<LookupElement>)list.getModel();
  }

  @SuppressWarnings("unused") // used plugins
  public LookupArranger getArranger() {
    return myArranger;
  }

  public void setArranger(LookupArranger arranger) {
    myArranger = arranger;
  }

  @ApiStatus.Internal
  public @NotNull ClientProjectSession getSession() {
    return mySession;
  }

  @Override
  public boolean isFocused() {
    return getLookupFocusDegree() == LookupFocusDegree.FOCUSED;
  }

  @Override
  public @NotNull LookupPresentation getPresentation() {
    return myPresentation;
  }

  @Override
  public void setPresentation(@NotNull LookupPresentation presentation) {
    myPresentation = presentation;
    refreshUi(false, true);
  }

  @Override
  public @NotNull LookupFocusDegree getLookupFocusDegree() {
    return myLookupFocusDegree;
  }

  public void setLookupFocusDegree(@NotNull LookupFocusDegree lookupFocusDegree) {
    LOG.debug("Set lookup focus degree to " + lookupFocusDegree);
    myLookupFocusDegree = lookupFocusDegree;
    for (LookupListener listener : myListeners) {
      listener.focusDegreeChanged();
    }
  }

  public boolean isCalculating() {
    return myCalculating;
  }

  public void setCalculating(boolean calculating) {
    myCalculating = calculating;
    if (myUi != null) {
      myUi.setCalculating(calculating);
    }
  }

  public void markSelectionTouched() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ThreadingAssertions.assertEventDispatchThread();
    }
    mySelectionTouched = true;
    list.repaint();
  }

  @TestOnly
  public void setSelectionTouched(boolean selectionTouched) {
    mySelectionTouched = selectionTouched;
  }

  @Override
  public int getSelectedIndex() {
    return list.getSelectedIndex();
  }

  public void setSelectedIndex(int index) {
    list.setSelectedIndex(index);
    list.ensureIndexIsVisible(index);
  }

  public int getDummyItemCount() {
    return myDummyItemCount.get();
  }

  public void setDummyItemCount(int count) {
    myDummyItemCount.set(count);
  }

  public @NotNull LookupElement getDummyItem() {
    return myDummyItem;
  }

  public void resort(boolean addAgain) {
    final List<LookupElement> items = getItems();

    myPresentableArranger.prefixChanged(this);
    getListModel().performBatchUpdate(model -> {
      synchronized (uiLock) {
        model.removeAll();
      }
    });

    if (addAgain) {
      for (final LookupElement item : items) {
        addItem(item, itemMatcher(item));
      }
    }
    refreshUi(true, true);
  }

  public boolean addItem(LookupElement item, PrefixMatcher matcher) {
    LookupElementPresentation presentation = LookupElementPresentation.renderElement(item);
    if (containsDummyIdentifier(presentation.getItemText()) ||
        containsDummyIdentifier(presentation.getTailText()) ||
        containsDummyIdentifier(presentation.getTypeText())) {
      return false;
    }

    cellRenderer.itemAdded(item, presentation);
    LookupArranger arranger = myArranger;
    arranger.registerMatcher(item, matcher);
    arranger.addElement(item, presentation);
    return true;
  }

  // Used by external plugins
  @SuppressWarnings("unused")
  public void scheduleItemUpdate(@NotNull LookupElement item) {
    // this check significantly affects perfomance with enabled assertions
    if (LOG.isTraceEnabled()){
      LOG.assertTrue(getItems().contains(item), "Item isn't present in lookup");
    }
    cellRenderer.updateItemPresentation(item);
  }

  private void addDummyItems(int count) {
    for (int i = count; i > 0; i--) {
      getListModel().add(myDummyItem);
    }
  }

  private static boolean containsDummyIdentifier(final @Nullable String s) {
    return s != null && s.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED);
  }

  public void updateLookupWidth() {
    cellRenderer.scheduleUpdateLookupWidthFromVisibleItems();
  }

  public void requestResize() {
    ThreadingAssertions.assertEventDispatchThread();
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

  public JList<LookupElement> getList() {
    return list;
  }

  @Override
  public List<LookupElement> getItems() {
    synchronized (uiLock) {
      return ContainerUtil.findAll(getListModel().toList(), element -> !(element instanceof EmptyLookupItem));
    }
  }

  @Override
  public @NotNull String getAdditionalPrefix() {
    return myOffsets.getAdditionalPrefix();
  }

  public void fireBeforeAppendPrefix(char c) {
    for (PrefixChangeListener listener : myPrefixChangeListeners) {
      listener.beforeAppend(c);
    }
  }

  public void appendPrefix(char c) {
    appendPrefix(c, true);
  }

  public void appendPrefix(char c, boolean refreshUi) {
    checkValid();
    myOffsets.appendPrefix(c);
    myPresentableArranger.prefixChanged(this);
    requestResize();
    if (refreshUi) {
      refreshUi(false, true);
      ensureSelectionVisible(true);
    }
    for (PrefixChangeListener listener : myPrefixChangeListeners) {
      listener.afterAppend(c);
    }
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
      ScrollingUtil.ensureIndexIsVisible(list, list.getSelectedIndex(), 1);
      return;
    }

    // selected item should be at the top of the visible list
    int top = list.getSelectedIndex();
    if (top > 0) {
      top--; // show one element above the selected one to give the hint that there are more available via scrolling
    }

    int firstVisibleIndex = list.getFirstVisibleIndex();
    if (firstVisibleIndex == top) {
      return;
    }

    ScrollingUtil.ensureRangeIsVisible(list, top, top + list.getLastVisibleIndex() - firstVisibleIndex);
  }

  public void truncatePrefix(boolean preserveSelection, int hideOffset) {
    truncatePrefix(preserveSelection, hideOffset, true);
  }

  public void truncatePrefix(boolean preserveSelection, int hideOffset, boolean refreshUi) {
    if (!myOffsets.truncatePrefix()) {
      myArranger.prefixTruncated(this, hideOffset);
      return;
    }
    myPrefixChangeListeners.forEach((listener -> listener.beforeTruncate()));

    if (preserveSelection) {
      markSelectionTouched();
    }

    myPresentableArranger.prefixChanged(this);
    requestResize();
    if (refreshUi && myPresentableArranger == myArranger) {
      refreshUi(false, true);
      ensureSelectionVisible(true);
    }

    for (PrefixChangeListener listener : myPrefixChangeListeners) {
      listener.afterTruncate();
    }
  }

  void moveToCaretPosition() {
    myOffsets.destabilizeLookupStart();
    refreshUi(false, true);
  }

  private boolean updateList(boolean onExplicitAction, boolean reused) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ThreadingAssertions.assertEventDispatchThread();
    }
    checkValid();

    CollectionListModelWithBatchUpdate<LookupElement> listModel = getListModel();

    Pair<List<LookupElement>, Integer> pair = myPresentableArranger.arrangeItems(this, onExplicitAction || reused);
    List<LookupElement> items = pair.first;
    Integer toSelect = pair.second;
    if (toSelect == null || toSelect < 0 || !items.isEmpty() && toSelect >= items.size()) {
      LOG.error("Arranger " + myPresentableArranger + " returned invalid selection index=" + toSelect + "; items=" + items);
      toSelect = 0;
    }
    if (!myPresentation.getMostRelevantOnTop()) {
      items = ContainerUtil.reverse(items);
      toSelect = Math.max(0, items.size() - toSelect - 1);
    }

    if (!myFirstElementAdded && !items.isEmpty()) {
      myFirstElementAdded = true;
      for (LookupListener listener : myListeners) {
        listener.firstElementShown();
      }
    }

    myOffsets.checkMinPrefixLengthChanges(items, this);
    List<LookupElement> oldModel = listModel.toList();

    List<LookupElement> finalItems = items;
    listModel.performBatchUpdate(model -> {
      synchronized (uiLock) {
        model.removeAll();
        if (!finalItems.isEmpty()) {
          Long currentTimeMillis = System.currentTimeMillis();
          for (LookupElement item : finalItems) {
            item.putUserDataIfAbsent(LOOKUP_ELEMENT_SHOW_TIMESTAMP_MILLIS, currentTimeMillis);
          }
          model.add(finalItems);
          addDummyItems(myDummyItemCount.get());
        }
        else {
          addEmptyItem(model);
        }
      }
    });

    updateListHeight(listModel);

    list.setSelectedIndex(toSelect);
    if (ScreenReader.isActive()) {
      AccessibleContext context = list.getAccessibleContext();
      Accessible child = context.getAccessibleChild(list.getSelectedIndex());
      context.firePropertyChange(AccessibleContext.ACCESSIBLE_ACTIVE_DESCENDANT_PROPERTY, null, child);
    }
    return !ContainerUtil.equalsIdentity(oldModel, items);
  }

  public boolean isSelectionVisible() {
    return ScrollingUtil.isIndexFullyVisible(list, list.getSelectedIndex());
  }

  private boolean checkReused() {
    EDT.assertIsEdt();
    if (myPresentableArranger != myArranger) {
      myPresentableArranger = myArranger;

      clearIfLookupAndArrangerPrefixesMatch();

      myPresentableArranger.prefixChanged(this);
      return true;
    }
    return false;
  }

  //some items may have passed to myArranger from CompletionProgressIndicator for an older prefix
  //these items won't be cleared during appending a new prefix (mayCheckReused = false)
  //so these 'out of dated' items which were matched against an old prefix, should be now matched against the new, updated lookup prefix.
  private void clearIfLookupAndArrangerPrefixesMatch() {
    boolean isCompletionArranger = myArranger instanceof CompletionLookupArrangerImpl;
    if (isCompletionArranger) {
      final String lastLookupArrangersPrefix = ((CompletionLookupArrangerImpl)myArranger).getLastLookupPrefix();
      if (lastLookupArrangersPrefix != null && !lastLookupArrangersPrefix.equals(getAdditionalPrefix())) {
        LOG.trace("prefixes don't match, do not clear lookup additional prefix");
      }
      else {
        myOffsets.clearAdditionalPrefix();
      }
    }
    else {
      myOffsets.clearAdditionalPrefix();
    }
  }

  private void updateListHeight(ListModel<LookupElement> model) {
    list.setFixedCellHeight(
      cellRenderer.getListCellRendererComponent(list, model.getElementAt(0), 0, false, false).getPreferredSize().height);
    list.setVisibleRowCount(Math.min(model.getSize(), myPresentation.getMaxVisibleItemsCount()));
  }

  private void addEmptyItem(CollectionListModel<? super LookupElement> model) {
    LookupElement item = new EmptyLookupItem(myCalculating ? " " : LangBundle.message("completion.no.suggestions"), false);
    model.add(item);

    cellRenderer.itemAdded(item, LookupElementPresentation.renderElement(item));
    requestResize();
  }

  @Override
  public @NotNull String itemPattern(@NotNull LookupElement element) {
    if (element instanceof EmptyLookupItem) return "";
    return myPresentableArranger.itemPattern(element);
  }

  @Override
  public @NotNull PrefixMatcher itemMatcher(@NotNull LookupElement item) {
    if (item instanceof EmptyLookupItem) {
      return new CamelHumpMatcher("");
    }
    return myPresentableArranger.itemMatcher(item);
  }

  public void finishLookup(final char completionChar) {
    finishLookup(completionChar, list.getSelectedValue());
  }

  public void finishLookup(char completionChar, final @Nullable LookupElement item) {
    LOG.assertTrue(!ApplicationManager.getApplication().isWriteAccessAllowed(), "finishLookup should be called without a write action");
    final PsiFile file = getPsiFile();
    boolean writableOk = file == null || FileModificationService.getInstance().prepareFileForWrite(file);
    if (isLookupDisposed()) { // ensureFilesWritable could close us by showing a dialog
      return;
    }

    if (!writableOk) {
      hideWithItemSelected(null, completionChar);
      return;
    }
    CommandProcessor.getInstance().executeCommand(mySession.getProject(), () -> {
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-346621, EA-1083788")) {
        finishLookupInWritableFile(completionChar, item);
      }
    }, null, null);
  }

  void finishLookupInWritableFile(char completionChar, @Nullable LookupElement item) {
    if (item == null || !item.isValid() || item instanceof EmptyLookupItem) {
      hideWithItemSelected(null, completionChar);
      return;
    }
    if (item.getUserData(CodeCompletionHandlerBase.DIRECT_INSERTION) != null) {
      hideWithItemSelected(item, completionChar);
      return;
    }

    if (isLookupDisposed()) { // DeferredUserLookupValue could close us in any way
      return;
    }

    final String prefix = itemPattern(item);
    boolean plainMatch = ContainerUtil.or(item.getAllLookupStrings(), s -> StringUtil.containsIgnoreCase(s, prefix));
    if (!plainMatch) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CAMEL_HUMPS);
    }

    myFinishing = true;
    if (fireBeforeItemSelected(item, completionChar)) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        editor.getDocument().startGuardedBlockChecking();
        try {
          insertLookupString(item, getPrefixLength(item));
        }
        finally {
          editor.getDocument().stopGuardedBlockChecking();
        }
      });
    }

    if (isLookupDisposed()) { // any document listeners could close us
      return;
    }

    doHide(false, true);

    fireItemSelected(item, completionChar);
  }

  private void hideWithItemSelected(LookupElement lookupItem, char completionChar) {
    fireBeforeItemSelected(lookupItem, completionChar);
    doHide(false, true);
    fireItemSelected(lookupItem, completionChar);
  }

  public int getPrefixLength(LookupElement item) {
    return myOffsets.getPrefixLength(item, this);
  }

  protected void insertLookupString(LookupElement item, final int prefix) {
    insertLookupString(mySession.getProject(), getTopLevelEditor(), item, itemMatcher(item), itemPattern(item), prefix);
  }

  public static void insertLookupString(final Project project,
                                        Editor editor, LookupElement item,
                                        PrefixMatcher matcher, String itemPattern, final int prefixLength) {
    final String lookupString = LookupUtil.getCaseCorrectedLookupString(item, matcher, itemPattern);

    final Editor hostEditor = editor;
    hostEditor.getCaretModel().runForEachCaret(__ -> {
      EditorModificationUtilEx.deleteSelectedText(hostEditor);
      final int caretOffset = hostEditor.getCaretModel().getOffset();

      int offset;
      try {
        offset = LookupUtil.insertLookupInDocumentWindowIfNeeded(project, editor, caretOffset, prefixLength, lookupString);
      }
      catch (AssertionError ae) {
        String classes = StreamEx.iterate(
            item, Objects::nonNull, i -> i instanceof LookupElementDecorator ? ((LookupElementDecorator<?>)i).getDelegate() : null)
          .map(le -> le.getClass().getName()).joining(" -> ");
        LOG.error("When completing " + item + " (" + classes + ")", ae);
        return;
      }
      hostEditor.getCaretModel().moveToOffset(offset);
      hostEditor.getSelectionModel().removeSelection();
    });

    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  @Override
  public int getLookupStart() {
    return myOffsets.getLookupStart(disposeTrace);
  }

  public int getLookupOriginalStart() {
    return myOffsets.getLookupOriginalStart();
  }

  @Override
  public boolean performGuardedChange(Runnable change) {
    checkValid();

    editor.getDocument().startGuardedBlockChecking();
    myGuardedChanges++;
    boolean result;
    try {
      result = myOffsets.performGuardedChange(change);
    }
    finally {
      editor.getDocument().stopGuardedBlockChecking();
      myGuardedChanges--;
    }
    if (!result || isLookupDisposed()) {
      hideLookup(false);
      return false;
    }
    if (isVisible() && editor.getContentComponent().isShowing()) {
      HintManagerImpl.updateLocation(this, editor, myUi.calculatePosition().getLocation());
    }
    checkValid();
    return true;
  }

  @Override
  public boolean vetoesHiding() {
    // the second condition means that the Lookup belongs to another connected client
    return myGuardedChanges > 0 || mySession != ClientSessionsManager.getProjectSession(mySession.getProject()) || LookupImplVetoPolicy.anyVetoesHiding(this);
  }

  public boolean isAvailableToUser() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return myShown;
    }
    return isVisible();
  }

  @Override
  public boolean isShown() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ThreadingAssertions.assertEventDispatchThread();
    }
    return myShown;
  }

  public boolean showLookup() {
    ThreadingAssertions.assertEventDispatchThread();
    checkValid();
    LOG.assertTrue(!myShown);
    myShown = true;
    myStampShown = System.currentTimeMillis();
    fireLookupShown();

    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return true;

    if (!UIUtil.isShowing(editor.getContentComponent())) {
      hideLookup(false);
      return false;
    }

    return WriteIntentReadAction.compute((Computable<Boolean>)() -> {
      LookupUsageTracker.trackLookup(myCreatedTimestamp, this);
      return doShowLookup();
    });
  }

  /**
   * @return The timestamp (in milliseconds) at which the lookup window was shown.
   * If the window has not been shown, 0 is returned.
   */
  public long getShownTimestampMillis() {
    return myStampShown;
  }

  /**
   * @return The timestamp (in milliseconds) at which the lookup was obtained.
   */
  public long getCreatedTimestampMillis() {
    return myCreatedTimestamp;
  }

  protected boolean doShowLookup() {
    myAdComponent.showRandomText();
    if (Boolean.TRUE.equals(editor.getUserData(AutoPopupController.NO_ADS))) {
      myAdComponent.clearAdvertisements();
    }

    Boolean showBottomPanel = editor.getUserData(AutoPopupController.SHOW_BOTTOM_PANEL_IN_LOOKUP_UI);
    myUi = new LookupUi(this, myAdComponent, list, showBottomPanel == null || showBottomPanel);
    myUi.setCalculating(myCalculating);
    Point p = myUi.calculatePosition().getLocation();
    if (ScreenReader.isActive()) {
      list.setFocusable(true);
      setFocusRequestor(list);

      AnActionEvent actionEvent =
        AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_POPUP, null, ((EditorImpl)editor).getDataContext());
      delegateActionToEditor(IdeActions.ACTION_EDITOR_BACKSPACE, null, actionEvent);
      delegateActionToEditor(IdeActions.ACTION_EDITOR_ESCAPE, null, actionEvent);
      delegateActionToEditor(IdeActions.ACTION_EDITOR_TAB, () -> new ChooseItemAction.Replacing(), actionEvent);
      delegateActionToEditor(IdeActions.ACTION_EDITOR_ENTER,
        /* e.g. rename popup comes initially unfocused */
                             () -> getLookupFocusDegree() == LookupFocusDegree.UNFOCUSED
                                   ? new NextVariableAction()
                                   : new ChooseItemAction.FocusedOnly(),
                             actionEvent);
      delegateActionToEditor(IdeActions.ACTION_EDITOR_MOVE_CARET_UP, null, actionEvent);
      delegateActionToEditor(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN, null, actionEvent);
      delegateActionToEditor(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, null, actionEvent);
      delegateActionToEditor(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, null, actionEvent);
      delegateActionToEditor(IdeActions.ACTION_RENAME, null, actionEvent);
    }
    try {
      HintManagerImpl.getInstanceImpl().showEditorHint(
        this, editor, p, HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING, 0, false,
        HintManagerImpl.createHintHint(editor, p, this, HintManager.UNDER).
          setRequestFocus(ScreenReader.isActive()).
          setAwtTooltip(false));
    }
    catch (Exception e) {
      LOG.error(e);
    }

    if (!isVisible() || !list.isShowing()) {
      hideLookup(false);
      return false;
    }

    return true;
  }

  private void fireLookupShown() {
    if (!myListeners.isEmpty()) {
      LookupEvent event = new LookupEvent(this, false);
      for (LookupListener listener : myListeners) {
        listener.lookupShown(event);
      }
    }
  }

  private void delegateActionToEditor(@NotNull String actionID,
                                      @Nullable Supplier<? extends AnAction> delegateActionSupplier,
                                      @NotNull AnActionEvent actionEvent) {
    AnAction action = ActionManager.getInstance().getAction(actionID);
    DumbAwareAction.create(e -> ActionUtil.performActionDumbAwareWithCallbacks(
      delegateActionSupplier == null ? action : delegateActionSupplier.get(), actionEvent)
    ).registerCustomShortcutSet(action.getShortcutSet(), list);
  }

  public Advertiser getAdvertiser() {
    return myAdComponent;
  }

  public boolean mayBeNoticed() {
    return myStampShown > 0 && System.currentTimeMillis() - myStampShown > 300;
  }

  private void addListeners() {
    editor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        if (canHideOnChange()) {
          hideLookup(false);
        }
      }
    }, this);

    final EditorMouseListener mouseListener = new EditorMouseListener() {
      @Override
      public void mouseClicked(@NotNull EditorMouseEvent e) {
        e.consume();
        hideLookup(false);
      }
    };

    editor.getCaretModel().addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        if (canHideOnChange()) {
          hideLookup(false);
        }
      }
    }, this);
    editor.getSelectionModel().addSelectionListener(new SelectionListener() {
      @Override
      public void selectionChanged(final @NotNull SelectionEvent e) {
        if (canHideOnChange()) {
          hideLookup(false);
        }
      }
    }, this);
    editor.addEditorMouseListener(mouseListener, this);

    JComponent editorComponent = editor.getContentComponent();
    if (editorComponent.isShowing()) {
      Disposer.register(this, UiNotifyConnector.installOn(editorComponent, new Activatable() {
        @Override
        public void hideNotify() {
          hideLookup(false);
        }
      }));

      Window window = ComponentUtil.getWindow(editorComponent);
      if (window != null) {
        ComponentListener windowListener = new ComponentAdapter() {
          @Override
          public void componentMoved(ComponentEvent event) {
            hideLookup(false);
          }
        };

        window.addComponentListener(windowListener);
        Disposer.register(this, () -> window.removeComponentListener(windowListener));
      }
    }

    list.addListSelectionListener(new ListSelectionListener() {
      private LookupElement oldItem = null;

      @Override
      public void valueChanged(@NotNull ListSelectionEvent e) {
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
        setLookupFocusDegree(LookupFocusDegree.FOCUSED);
        markSelectionTouched();
        if (ScreenReader.isActive()) {
          list.requestFocus();
        }
        else {
          editorComponent.requestFocus();
        }

        if (clickCount == 2) {
          // try to finish completion item using the action subsystem to avoid the difference between mouse and shortcut complete
          AnAction completeAction = ActionManager.getInstance().getAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM);
          // the execution is wrapped into a command inside EditorAction
          if (completeAction != null && editor instanceof EditorEx) {
            AnActionEvent dataContext =
              AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, ((EditorEx)editor).getDataContext());
            ActionUtil.performActionDumbAwareWithCallbacks(completeAction, dataContext);
          }
          else {
            CommandProcessor.getInstance()
              .executeCommand(mySession.getProject(), () -> finishLookup(NORMAL_SELECT_CHAR), "", null, editor.getDocument());
          }
        }
        return true;
      }
    }.installOn(list);

    addPrefixChangeListener(new PrefixChangeListener() {
      @Override
      public void afterAppend(char c) {
        cellRenderer.scheduleUpdateLookupWidthFromVisibleItems();
      }
    }, this);
  }

  private boolean canHideOnChange() {
    return myGuardedChanges == 0 && !myFinishing && !vetoesHidingOnChange() && ClientId.isCurrentlyUnderLocalId();
  }

  private boolean vetoesHidingOnChange() {
    return LookupImplVetoPolicy.anyVetoesHidingOnChange(this);
  }

  @Override
  public @Nullable LookupElement getCurrentItem() {
    synchronized (uiLock) {
      LookupElement item = list.getSelectedValue();
      return item instanceof EmptyLookupItem ? null : item;
    }
  }

  @Override
  public LookupElement getCurrentItemOrEmpty() {
    return list.getSelectedValue();
  }

  @Override
  public void setCurrentItem(LookupElement item) {
    markSelectionTouched();
    list.setSelectedValue(item, false);
  }

  @Override
  public void addLookupListener(LookupListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeLookupListener(LookupListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public Rectangle getCurrentItemBounds() {
    int index = list.getSelectedIndex();
    if (index < 0) {
      LOG.error("No selected element, size=" + getListModel().getSize() + "; items" + getItems());
    }
    Rectangle itemBounds = list.getCellBounds(index, index);
    if (itemBounds == null) {
      LOG.error("No bounds for " + index + "; size=" + getListModel().getSize());
      return null;
    }

    return SwingUtilities.convertRectangle(list, itemBounds, getComponent());
  }

  private boolean fireBeforeItemSelected(final @Nullable LookupElement item, char completionChar) {
    boolean result = true;
    if (!myListeners.isEmpty()) {
      LookupEvent event = new LookupEvent(this, item, completionChar);
      for (LookupListener listener : myListeners) {
        try {
          if (!listener.beforeItemSelected(event)) result = false;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
    return result;
  }

  public void fireItemSelected(final @Nullable LookupElement item, char completionChar) {
    if (item != null && item.requiresCommittedDocuments()) {
      PsiDocumentManager.getInstance(mySession.getProject()).commitAllDocuments();
    }
    myArranger.itemSelected(item, completionChar);
    if (!myListeners.isEmpty()) {
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
    if (!myListeners.isEmpty()) {
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
    myPreview.updatePreview(currentItem);
  }

  private void fireUiRefreshed() {
    for (LookupListener listener : myListeners) {
      listener.uiRefreshed();
    }
  }

  public void replacePrefix(final String presentPrefix, final String newPrefix) {
    if (!performGuardedChange(() -> {
      EditorModificationUtilEx.deleteSelectedText(editor);
      int offset = editor.getCaretModel().getOffset();
      final int start = offset - presentPrefix.length();
      editor.getDocument().replaceString(start, offset, newPrefix);
      myOffsets.clearAdditionalPrefix();
      editor.getCaretModel().moveToOffset(start + newPrefix.length());
    })) {
      return;
    }
    myPresentableArranger.prefixReplaced(this, newPrefix);
    refreshUi(true, true);
  }

  @Override
  public @Nullable PsiFile getPsiFile() {
    return PsiDocumentManager.getInstance(mySession.getProject()).getPsiFile(getEditor().getDocument());
  }

  @Override
  public boolean isCompletion() {
    return myArranger.isCompletion();
  }

  @Override
  public PsiElement getPsiElement() {
    PsiFile file = getPsiFile();
    if (file == null) return null;

    int offset = getLookupStart();
    Editor editor = getEditor();
    if (editor instanceof EditorWindow) {
      offset = editor.logicalPositionToOffset(((EditorWindow)editor).hostToInjected(this.editor.offsetToLogicalPosition(offset)));
    }
    if (offset > 0) return file.findElementAt(offset - 1);

    return file.findElementAt(0);
  }

  private static @Nullable DocumentWindow getInjectedDocument(Project project, Editor editor, int offset) {
    PsiFile hostFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (hostFile != null) {
      // inspired by com.intellij.codeInsight.editorActions.TypedHandler.injectedEditorIfCharTypedIsSignificant()
      List<DocumentWindow> injected = InjectedLanguageManager.getInstance(project)
        .getCachedInjectedDocumentsInRange(hostFile, TextRange.create(offset, offset));
      for (DocumentWindow documentWindow : injected) {
        if (documentWindow.isValid() && documentWindow.containsRange(offset, offset)) {
          return documentWindow;
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull Editor getEditor() {
    DocumentWindow documentWindow = getInjectedDocument(mySession.getProject(), editor, editor.getCaretModel().getOffset());
    if (documentWindow != null) {
      PsiFile injectedFile = PsiDocumentManager.getInstance(mySession.getProject()).getPsiFile(documentWindow);
      return InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injectedFile);
    }
    return editor;
  }

  @Override
  public @NotNull Editor getTopLevelEditor() {
    return editor;
  }

  @Override
  public @NotNull Project getProject() {
    return mySession.getProject();
  }

  @Override
  public boolean isPositionedAboveCaret() {
    return myUi != null && myUi.isPositionedAboveCaret();
  }

  @Override
  public boolean isSelectionTouched() {
    return mySelectionTouched;
  }

  @Override
  public int getLastVisibleIndex() {
    return list.getLastVisibleIndex();
  }

  public List<LookupElement> getVisibleItems() {
    ThreadingAssertions.assertEventDispatchThread();

    var itemsCount = list.getItemsCount();
    if (!myShown || itemsCount == 0) return Collections.emptyList();

    synchronized (uiLock) {
      int lowerItemIndex = list.getFirstVisibleIndex();
      int higherItemIndex = list.getLastVisibleIndex();
      if (lowerItemIndex < 0 || higherItemIndex < 0) return Collections.emptyList();

      return getListModel().toList().subList(lowerItemIndex, Math.min(higherItemIndex + 1, itemsCount));
    }
  }


  @Override
  public List<String> getAdvertisements() {
    return myAdComponent.getAdvertisements();
  }

  @Override
  public void hide() {
    hideLookup(true);
  }

  @Override
  public void hideLookup(boolean explicitly) {
    ThreadingAssertions.assertEventDispatchThread();

    if (myHidden) return;

    doHide(true, explicitly);
  }

  private void doHide(final boolean fireCanceled, final boolean explicitly) {
    if (isLookupDisposed()) {
      LOG.error(formatDisposeTrace());
    }
    if (!mySession.getClientId().equals(ClientId.getCurrent())) {
      LOG.error(ClientId.getCurrent() + " tries to hide lookup of " + mySession.getClientId());
    }
    else {
      myHidden = true;

      try {
        super.hide();

        Disposer.dispose(this);
        ToolTipManager.sharedInstance().unregisterComponent(list);
        assert isLookupDisposed();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    if (fireCanceled) {
      fireLookupCanceled(explicitly);
    }
  }

  @Override
  protected void onPopupCancel() {
    hide();
  }

  private Throwable disposeTrace = null;

  @Override
  public void dispose() {
    CoroutineScopeKt.cancel(coroutineScope, null);
    ThreadingAssertions.assertEventDispatchThread();
    assert myHidden;

    myOffsets.disposeMarkers();
    disposeTrace = new Throwable();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Disposing lookup:", disposeTrace);
    }
  }

  private String formatDisposeTrace() {
    return ExceptionUtil.getThrowableText(disposeTrace) + "\n============";
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
        cellRenderer.refreshUi();
        myUi.refreshUi(selectionVisible, itemsChanged, reused, onExplicitAction);
      }
    }
    finally {
      myUpdating = false;
      fireCurrentItemChanged(prevItem, getCurrentItem());
      fireUiRefreshed();
    }
  }

  public void markReused() {
    EDT.assertIsEdt();
    myArranger = myArranger.createEmptyCopy();
    requestResize();
  }

  public void addAdvertisement(@NotNull @NlsContexts.PopupAdvertisement String text, @Nullable Icon icon) {
    if (!containsDummyIdentifier(text)) {
      myAdComponent.addAdvertisement(text, icon);
      requestResize();
    }
  }

  public boolean isLookupDisposed() {
    return disposeTrace != null;
  }

  public void checkValid() {
    if (isLookupDisposed()) {
      throw new AssertionError("Disposed at: " + formatDisposeTrace());
    }
  }

  @Override
  public void showElementActions(@Nullable InputEvent event) {
    if (!isVisible()) return;

    LookupElement element = getCurrentItem();
    if (element == null) {
      return;
    }

    Collection<LookupElementAction> actions = getActionsFor(element);
    if (actions.isEmpty()) {
      return;
    }

    UIEventLogger.LookupShowElementActions.log(mySession.getProject());

    Rectangle itemBounds = getCurrentItemBounds();
    Rectangle visibleRect = SwingUtilities.convertRectangle(list, list.getVisibleRect(), getComponent());
    ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(new LookupActionsStep(actions, this, element));
    Point p = (itemBounds.intersects(visibleRect) || event == null)
              ? new Point(itemBounds.x + itemBounds.width, itemBounds.y)
              : SwingUtilities.convertPoint(event.getComponent(), new Point(0, event.getComponent().getHeight() + JBUIScale.scale(2)),
                                            getComponent());

    listPopup.show(new RelativePoint(getComponent(), p));
  }

  public @NotNull Map<LookupElement, List<Pair<String, Object>>> getRelevanceObjects(@NotNull Iterable<? extends LookupElement> items,
                                                                                     boolean hideSingleValued) {
    return myPresentableArranger.getRelevanceObjects(items, hideSingleValued);
  }

  public void setPrefixChangeListener(PrefixChangeListener listener) {
    myPrefixChangeListeners.add(listener);
  }

  public void addPrefixChangeListener(PrefixChangeListener listener, Disposable parentDisposable) {
    ContainerUtil.add(listener, myPrefixChangeListeners, parentDisposable);
  }


  /**
   * see {@link LookupCellRenderer.ItemPresentationCustomizer}
   */
  @ApiStatus.Internal
  public void addPresentationCustomizer(@NotNull LookupCellRenderer.ItemPresentationCustomizer customizer) {
    cellRenderer.addPresentationCustomizer$intellij_platform_lang_impl(customizer);
  }

  FontPreferences getFontPreferences() {
    return myFontPreferences;
  }

  private static final class NewUILookupAdvertiser extends Advertiser {
    private NewUILookupAdvertiser() {
      setBorder(JBUI.Borders.empty());
      setForeground(JBUI.CurrentTheme.CompletionPopup.Advertiser.foreground());
      setBackground(JBUI.CurrentTheme.CompletionPopup.Advertiser.background());
    }

    @Override
    protected Font adFont() {
      Font font = StartupUiUtil.getLabelFont();
      RelativeFont relativeFont = RelativeFont.NORMAL.scale(JBUI.CurrentTheme.CompletionPopup.Advertiser.fontSizeOffset());
      return relativeFont.derive(font);
    }
  }

  /**
   * List implementation for lookup. Normally, this list is not focused. However,
   * it gains focus when "Screen Reader" mode is enabled. So we need to delegate
   * key events and declare a permanent component to provide proper data context for actions.
   */
  private final class LookupList extends JBList<LookupElement> implements DependentTransientComponent {
    LookupList() { super(new CollectionListModelWithBatchUpdate<>()); }

    @Override
    protected void processKeyEvent(final @NotNull KeyEvent e) {
      editor.getContentComponent().dispatchEvent(e);
    }

    @Override
    protected @NotNull ExpandableItemsHandler<Integer> createExpandableItemsHandler() {
      return new CompletionExtender(this);
    }

    @Override
    public @NotNull Component getPermanentComponent() {
      return editor.getContentComponent();
    }
  }
}
