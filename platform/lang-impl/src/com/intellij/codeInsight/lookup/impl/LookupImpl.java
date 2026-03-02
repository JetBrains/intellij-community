// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionItemLookupElement;
import com.intellij.codeInsight.completion.CompletionLookupArrangerImpl;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.FinishCompletionInfo;
import com.intellij.codeInsight.completion.LookupElementListPresenter;
import com.intellij.codeInsight.completion.ModCompletionInserter;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.ShowHideIntentionIconLookupAction;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupArranger;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementInsertStopper;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.lookup.LookupGroupArranger;
import com.intellij.codeInsight.lookup.LookupListener;
import com.intellij.codeInsight.lookup.LookupPresentation;
import com.intellij.codeInsight.lookup.LookupUtil;
import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction;
import com.intellij.codeInsight.template.impl.actions.NextVariableAction;
import com.intellij.codeWithMe.ClientId;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.PowerSaveMode;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DependentTransientComponent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.client.ClientProjectSession;
import com.intellij.openapi.client.ClientSessionsUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.CollectConsumer;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.Advertiser;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
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
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.intellij.codeInsight.completion.FusCompletionKeys.LOOKUP_ELEMENT_SHOW_TIMESTAMP_MILLIS;
import static kotlinx.coroutines.SupervisorKt.SupervisorJob;

public class LookupImpl extends LightweightHint implements LookupEx, Disposable, LookupElementListPresenter {
  private static final Logger LOG = Logger.getInstance(LookupImpl.class);

  private final LookupOffsets myOffsets;
  private final Editor editor;
  private final Object uiLock = new Object();
  private final JBList<LookupElement> list = new LookupList();
  @VisibleForTesting
  @ApiStatus.Internal
  public final LookupCellRenderer cellRenderer;

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

  /**
   * `myArranger` is the arranger used for collecting items.
   * Can be changed by calling {@link #setArranger} from any thread.
   * Changing arranger usually means that the completion process is updated (e.g., prefix changed, the completion type changed, etc.)
   */
  private volatile @NotNull LookupArranger myArranger;

  /**
   * An arranger that is used for rendering. It's synchronized (i.e., replaced) with {@link #myArranger} during rendering.
   * See {@link #checkReused()}.
   * Accessed on EDT only. Note though, that {@link #myArranger} is usually the same instance, but it is accessed on any thread.
   */
  private LookupArranger myPresentableArranger;

  private boolean myStartCompletionWhenNothingMatches;
  boolean myResizePending;
  private boolean myFinishingCompletionATM;
  boolean myUpdating;
  private LookupUi myUi;
  private LookupPresentation myPresentation = new LookupPresentation.Builder().build();
  private final AtomicInteger myDummyItemCount = new AtomicInteger();
  private final EmptyLookupItem myDummyItem = new EmptyLookupItem(CommonBundle.message("tree.node.loading"), true);
  private boolean myFirstElementAdded = false;
  private boolean myShowIfMeaningless = false;
  private final LookupDisplayStrategy myDisplayStrategy;

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
    myDisplayStrategy = LookupDisplayStrategy.getStrategy(editor);

    DaemonCodeAnalyzer.getInstance(session.getProject()).disableUpdateByTimer(this);

    cellRenderer = new LookupCellRenderer(this, this.editor.getContentComponent());
    cellRenderer.itemAdded(myDummyItem, LookupElementPresentation.renderElement(myDummyItem));
    list.setCellRenderer(cellRenderer);
    list.setSelectionModel(new NonSelectableListSelectionModel(list));
    list.setFocusable(false);
    list.setFixedCellWidth(50);
    list.setBorder(JBUI.Borders.empty());

    // a new top level frame just got the focus. This is important to prevent screen readers
    // from announcing the title of the top-level frame when the list is shown (or hidden),
    // as they usually do when a new top-level frame receives the focus.
    // This is not relevant on Mac. This breaks JBR a11y on Mac.
    if (SystemInfoRt.isWindows) {
      AccessibleContextUtil.setParent(list, this.editor.getContentComponent());
    }

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setBackground(getBackgroundColor());

    if (ExperimentalUI.isNewUI()) {
      myAdComponent = new NewUILookupAdvertiser();
    }
    else {
      myAdComponent = new Advertiser();
      myAdComponent.setBackground(getBackgroundColor());
    }

    myOffsets = new LookupOffsets(this.editor);

    CollectionListModel<LookupElement> model = getListModel();
    addEmptyItem(model);
    updateListHeight(model);

    addListeners();

    myCreatedTimestamp = System.currentTimeMillis();
  }

  @ApiStatus.Internal
  protected @NotNull Color getBackgroundColor() {
    return myDisplayStrategy.getBackgroundColor();
  }

  private @NotNull CollectionListModelWithBatchUpdate<LookupElement> getListModel() {
    return (CollectionListModelWithBatchUpdate<LookupElement>)list.getModel();
  }

  public @NotNull LookupArranger getArranger() {
    return myArranger;
  }

  public void setArranger(@NotNull LookupArranger arranger) {
    reuseAdditionalMatcher(myArranger, arranger);
    myArranger = arranger;
  }

  private static void reuseAdditionalMatcher(@NotNull LookupArranger oldArranger, @NotNull LookupArranger newArranger) {
    Predicate<LookupElement> oldMatcher = oldArranger.getAdditionalMatcher();
    if (oldMatcher != null) {
      newArranger.setAdditionalMatcher(oldMatcher);
    }
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

  /**
   * let LookupImpl know that the user changed the selected element
   * @see #isSelectionTouched()
   */
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
    List<LookupElement> items = getItems();

    myPresentableArranger.prefixChanged(this);
    getListModel().performBatchUpdate(model -> {
      synchronized (uiLock) {
        model.removeAll();
      }
    });

    if (addAgain) {
      for (LookupElement item : items) {
        addItem(item, itemMatcher(item));
      }
    }
    refreshUi(true, true);
  }

  /**
   * @return true if the item was added
   */
  public boolean addItem(@NotNull LookupElement item, @NotNull PrefixMatcher matcher) {
    LookupElementPresentation presentation = LookupElementPresentation.renderElement(item);
    if (containsDummyIdentifier(presentation.getItemText()) ||
        containsDummyIdentifier(presentation.getTailText()) ||
        containsDummyIdentifier(presentation.getTypeText())) {
      return false;
    }

    cellRenderer.itemAdded(item, presentation);
    LookupArranger arranger = myArranger;
    arranger.invokeWhenLookupElementAdded(item, () -> cellRenderer.itemAddedToArranger(item));
    arranger.registerMatcher(item, matcher);
    arranger.addElement(item, presentation);
    return true;
  }

  public void scheduleItemUpdate(@NotNull LookupElement item) {
    // this check significantly affects performance with enabled assertions
    if (LOG.isTraceEnabled()) {
      LOG.assertTrue(getItems().contains(item), "Item isn't present in lookup");
    }
    cellRenderer.updateItemPresentation(item);
  }

  private void addDummyItems(int count) {
    for (int i = count; i > 0; i--) {
      getListModel().add(myDummyItem);
    }
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  public boolean isShowIfMeaningless() {
    return myShowIfMeaningless;
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  public void showIfMeaningless() {
    myShowIfMeaningless = true;
  }

  @ApiStatus.Internal
  public void cancelRendering(@NotNull LookupElement element) {
    cellRenderer.cancelRendering(element);
  }

  private static boolean containsDummyIdentifier(@Nullable String s) {
    return s != null && s.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED);
  }

  public void updateLookupWidth() {
    cellRenderer.scheduleUpdateLookupWidthFromVisibleItems();
  }

  public void requestResize() {
    ThreadingAssertions.assertEventDispatchThread();
    myResizePending = true;
  }

  public @NotNull Collection<LookupElementAction> getActionsFor(LookupElement element) {
    CollectConsumer<LookupElementAction> consumer = new CollectConsumer<>();
    for (LookupActionProvider provider : LookupActionProvider.EP_NAME.getExtensions()) {
      provider.fillActions(element, this, consumer);
    }
    if (!consumer.getResult().isEmpty()) {
      consumer.consume(new ShowHideIntentionIconLookupAction());
    }
    return consumer.getResult();
  }

  public @NotNull JList<LookupElement> getList() {
    return list;
  }

  @Override
  public @Unmodifiable @NotNull List<LookupElement> getItems() {
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

    if (myPresentableArranger instanceof LookupGroupArranger lookupGroupArranger) {
      lookupGroupArranger.synchronizeGroupSupport(myPresentation.getMostRelevantOnTop());
    }
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

    cellRenderer.scheduleVisibleItemsExpensiveRendering();

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

  /**
   * @return true if this lookup is reused my another completion process
   */
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

  //some items may have passed to myArranger from CompletionProgressIndicator for an older prefix.
  //these items won't be cleared during appending a new prefix (mayCheckReused = false),
  //so these 'out of dated' items which were matched against an old prefix, should be now matched against the new, updated lookup prefix.
  private void clearIfLookupAndArrangerPrefixesMatch() {
    boolean isCompletionArranger = myArranger instanceof CompletionLookupArrangerImpl;
    if (isCompletionArranger) {
      String lastLookupArrangersPrefix = ((CompletionLookupArrangerImpl)myArranger).getLastLookupPrefix();
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

  private void updateListHeight(@NotNull ListModel<LookupElement> model) {
    int index = 0;
    LookupElement element = model.getElementAt(0);
    if (element.as(SeparatorLookupElement.class) != null && model.getSize() > 1) {
      index = 1;
      element = model.getElementAt(index);
    }
    list.setFixedCellHeight(
      cellRenderer.getListCellRendererComponent(list, element, index, false, false).getPreferredSize().height);
    list.setVisibleRowCount(Math.min(model.getSize(), myPresentation.getMaxVisibleItemsCount()));
  }

  private void addEmptyItem(@NotNull CollectionListModel<? super LookupElement> model) {
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

  public void finishLookup(char completionChar) {
    finishLookup(completionChar, list.getSelectedValue());
  }

  public void finishLookup(char completionChar, @Nullable LookupElement item) {
    LOG.assertTrue(!ApplicationManager.getApplication().isWriteAccessAllowed(), "finishLookup should be called without a write action");
    PsiFile file = getPsiFile();
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

  private static void insertItem(char completionChar,
                                 @NotNull Editor editor,
                                 int start, 
                                 @NotNull PsiFile psiFile,
                                 CompletionItemLookupElement wrapper) {
    ModCompletionItem.InsertionContext insertionContext = new ModCompletionItem.InsertionContext(
      completionChar == REPLACE_SELECT_CHAR ? ModCompletionItem.InsertionMode.OVERWRITE : ModCompletionItem.InsertionMode.INSERT,
      completionChar);
    ActionContext actionContext = ActionContext.from(editor, psiFile);
    ActionContext finalActionContext = actionContext
      .withOffset(start)
      .withSelection(TextRange.create(start, actionContext.offset()));
    Project project = actionContext.project();
    ModCommand command = wrapper.getCachedCommand(finalActionContext, insertionContext);
    if (command == null) {
      command = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> ReadAction.nonBlocking(
          () -> wrapper.computeCommand(finalActionContext, insertionContext)).executeSynchronously(),
        AnalysisBundle.message("complete"), true, project);
    }
    ModCompletionInserter.executeModCommand(editor, psiFile, start, actionContext.offset(), command);
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

    String prefix = itemPattern(item);
    boolean plainMatch = ContainerUtil.or(item.getAllLookupStrings(), s -> StringUtil.containsIgnoreCase(s, prefix));
    if (!plainMatch) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CAMEL_HUMPS);
    }

    myFinishingCompletionATM = true;
    if (fireBeforeItemSelected(item, completionChar)) {
      if (item instanceof CompletionItemLookupElement wrapper) {
        PsiFile file = Objects.requireNonNull(getPsiFile(), "PsiFile must be known for ModCommand completion");
        editor.getCaretModel().runForEachCaret(__ -> {
          insertItem(completionChar, editor, editor.getCaretModel().getOffset() - getPrefixLength(item), file, wrapper);
        });
      } else {
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
    }

    if (isLookupDisposed()) { // any document listeners could close us
      return;
    }

    doHide(false, true);

    fireItemSelected(item, completionChar);
  }

  @ApiStatus.Internal
  public void hideWithItemSelected(@Nullable LookupElement lookupItem, char completionChar) {
    fireBeforeItemSelected(lookupItem, completionChar);
    doHide(false, true);
    fireItemSelected(lookupItem, completionChar);
  }

  public int getPrefixLength(@NotNull LookupElement item) {
    return myOffsets.getPrefixLength(item, this);
  }

  protected void insertLookupString(@NotNull LookupElement item, int prefixLength) {
    insertLookupString(mySession.getProject(), getTopLevelEditor(), item, itemMatcher(item), itemPattern(item), prefixLength);
  }

  public static void insertLookupString(@NotNull Project project,
                                        @NotNull Editor editor,
                                        @NotNull LookupElement item,
                                        @NotNull PrefixMatcher matcher,
                                        @NotNull String itemPattern,
                                        int prefixLength) {
    String lookupString = LookupUtil.getCaseCorrectedLookupString(item, matcher, itemPattern);

    item.putUserData(CodeCompletionHandlerBase.ITEM_PATTERN_AND_PREFIX_LENGTH, new FinishCompletionInfo(itemPattern, prefixLength));

    editor.getCaretModel().runForEachCaret(__ -> {
      EditorModificationUtilEx.deleteSelectedText(editor);
      int caretOffset = editor.getCaretModel().getOffset();
      LookupElementInsertStopper element = item.as(LookupElementInsertStopper.class);
      if (element == null || !element.shouldStopLookupInsertion()) {
        int offset;
        try {
          offset = LookupUtil.insertLookupInDocumentWindowIfNeeded(project, editor, caretOffset, prefixLength, lookupString);
        }
        catch (AssertionError ae) {
          reportErrorAfterInsert(item, ae);
          return;
        }
        editor.getCaretModel().moveToOffset(offset);
      }
      editor.getSelectionModel().removeSelection();
    });

    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private static void reportErrorAfterInsert(@NotNull LookupElement item, @NotNull AssertionError ae) {
    @SuppressWarnings("RedundantTypeArguments") // type argument is needed to suppress incorrect nullability issue
    String classes = StreamEx
      .<LookupElement>iterate(item, Objects::nonNull, i -> {
        return i instanceof LookupElementDecorator ? ((LookupElementDecorator<?>)i).getDelegate() : null;
      })
      .map(le -> le.getClass().getName()).joining(" -> ");
    LOG.error("When completing " + item + " (" + classes + ")", ae);
  }

  @Override
  public int getLookupStart() {
    return myOffsets.getLookupStart(disposeTrace);
  }

  public int getLookupOriginalStart() {
    return myOffsets.getLookupOriginalStart();
  }

  @Override
  public boolean performGuardedChange(@NotNull Runnable change) {
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
      updateLocation(myUi.calculatePosition().getLocation());
    }
    checkValid();
    return true;
  }

  private boolean hasGuardedChanges() {
    return myGuardedChanges > 0;
  }

  @ApiStatus.Internal
  protected void updateLocation(@NotNull Point p) {
    myDisplayStrategy.updateLocation(this, editor, p);
  }

  @Override
  public boolean vetoesHiding() {
    if (isLookupDisposed()) return false;
    // the second condition means that the Lookup belongs to another connected client
    return hasGuardedChanges() ||
           mySession != ClientSessionsUtil.getCurrentSessionOrNull(mySession.getProject()) ||
           LookupImplVetoPolicy.anyVetoesHiding(this);
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

    return WriteIntentReadAction.compute(() -> {
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

      @SuppressWarnings("removal")
      AnActionEvent actionEvent =
        AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_POPUP, null, ((EditorImpl)editor).getDataContext());
      delegateActionToEditor(IdeActions.ACTION_EDITOR_BACKSPACE, null, actionEvent);
      delegateActionToEditor(IdeActions.ACTION_EDITOR_ESCAPE, null, actionEvent);
      delegateActionToEditor(IdeActions.ACTION_EDITOR_TAB, () -> new ChooseItemAction.Replacing(), actionEvent);
      delegateActionToEditor(IdeActions.ACTION_EDITOR_ENTER,
        /* e.g., rename popup comes initially unfocused */
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
      myDisplayStrategy.showLookup(this, editor, p);
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
    DumbAwareAction.create(e -> ActionUtil.performAction(
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
    editor.getUiDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        if (canHideOnChange()) {
          hideLookup(false);
        }
      }
    }, this);

    EditorMouseListener mouseListener = new EditorMouseListener() {
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
      public void selectionChanged(@NotNull SelectionEvent e) {
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
          LookupElement item = getCurrentItem();
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
            @SuppressWarnings("removal")
            AnActionEvent dataContext =
              AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, ((EditorEx)editor).getDataContext());
            ActionUtil.performAction(completeAction, dataContext);
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
    return !hasGuardedChanges() && !myFinishingCompletionATM && !vetoesHidingOnChange() && ClientId.isCurrentlyUnderLocalId();
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
  public @Nullable LookupElement getCurrentItemOrEmpty() {
    return list.getSelectedValue();
  }

  @Override
  public void setCurrentItem(@Nullable LookupElement item) {
    markSelectionTouched();
    list.setSelectedValue(item, false);
  }

  @Override
  public void addLookupListener(@NotNull LookupListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeLookupListener(@NotNull LookupListener listener) {
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

  /**
   * @return false if the lookup string must not be inserted
   */
  private boolean fireBeforeItemSelected(@Nullable LookupElement item, char completionChar) {
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

  public void fireItemSelected(@Nullable LookupElement item, char completionChar) {
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

  private void fireLookupCanceled(boolean explicitly) {
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
    cacheModCommandResult(currentItem);
    myPreview.updatePreview(currentItem);
  }

  private void cacheModCommandResult(@Nullable LookupElement currentItem) {
    if (currentItem instanceof CompletionItemLookupElement wrapper && !PowerSaveMode.isEnabled()) {
      PsiFile file = getPsiFile();
      if (file != null) {
        ActionContext actionContext = ActionContext.from(editor, file);
        int start = actionContext.offset() - getPrefixLength(currentItem);
        ActionContext finalActionContext = actionContext
          .withOffset(start)
          .withSelection(TextRange.create(start, actionContext.offset()));
        // Cache current item result
        ReadAction.nonBlocking(
          () -> wrapper.computeCommand(finalActionContext, ModCompletionItem.DEFAULT_INSERTION_CONTEXT))
          .expireWith(this)
          .submit(AppExecutorUtil.getAppExecutorService());
      }
    }
  }

  private void fireUiRefreshed() {
    for (LookupListener listener : myListeners) {
      listener.uiRefreshed();
    }
  }

  public void replacePrefix(@NotNull String presentPrefix, @NotNull String newPrefix) {
    if (!performGuardedChange(() -> {
      EditorModificationUtilEx.deleteSelectedText(editor);
      int offset = editor.getCaretModel().getOffset();
      int start = offset - presentPrefix.length();
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
  public @Nullable PsiElement getPsiElement() {
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

  public @NotNull List<LookupElement> getVisibleItems() {
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

  @ApiStatus.Internal
  public List<LookupElement> getItemsForAsyncRendering() {
    ThreadingAssertions.assertEventDispatchThread();

    var itemsCount = list.getItemsCount();
    if (itemsCount == 0) return Collections.emptyList();

    synchronized (uiLock) {
      int lowerItemIndex = list.getFirstVisibleIndex();
      int higherItemIndex = list.getLastVisibleIndex();
      if (lowerItemIndex < 0 || higherItemIndex < 0) return Collections.emptyList();

      int delta = 15;
      var items = getListModel().toList();
      return items.subList(Math.max(lowerItemIndex - delta, 0), Math.min(higherItemIndex + 1 + delta, itemsCount));
    }
  }

  @Override
  public @Unmodifiable List<String> getAdvertisements() {
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

  private void doHide(boolean fireCanceled, boolean explicitly) {
    AssertionError invalidError = prepareErrorIfInvalid();
    if (invalidError != null) {
      LOG.error(invalidError);
    }
    if (!mySession.getClientId().equals(ClientId.getCurrent())) {
      LOG.error(ClientId.getCurrent() + " tries to hide lookup of " + mySession.getClientId());
    }
    else {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Lookup hide: " + this + "; fireCanceled=" + fireCanceled + "; explicitly=" + explicitly);
      }

      myHidden = true;

      try {
        myDisplayStrategy.hideLookup(this, editor);

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

  /**
   * @param mayCheckReused   pass {@code true} if you want refresh because lookup is reused for another completion process (e.g., prefix has changed, the completion type has changed, etc.)
   * @param onExplicitAction the method is called on explicit user action
   */
  public void refreshUi(boolean mayCheckReused, boolean onExplicitAction) {
    assert !myUpdating;
    LookupElement prevItem = getCurrentItem();
    myUpdating = true;
    try {
      boolean reused = mayCheckReused && checkReused();
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
    LookupArranger copy = myArranger.createEmptyCopy();
    setArranger(copy);

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
    AssertionError error = prepareErrorIfInvalid();
    if (error != null) {
      throw error;
    }
  }

  private @Nullable AssertionError prepareErrorIfInvalid() {
    if (!isLookupDisposed()) {
      return null;
    }

    AssertionError error = new AssertionError("Lookup is disposed (see suppressed exception)");
    error.addSuppressed(disposeTrace);
    return error;
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

  public void setPrefixChangeListener(@NotNull PrefixChangeListener listener) {
    myPrefixChangeListeners.add(listener);
  }

  public void addPrefixChangeListener(@NotNull PrefixChangeListener listener, @NotNull Disposable parentDisposable) {
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
    protected void processKeyEvent(@NotNull KeyEvent e) {
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


  private static class NonSelectableListSelectionModel extends DefaultListSelectionModel implements
                                                                                         ScrollingUtil.ImpossibleListSelectionModel {
    private final JList<?> list;

    private NonSelectableListSelectionModel(JList<?> list) {
      this.list = list;
    }

    @Override
    public void setSelectionInterval(int index0, int index1) {
      // If either endpoint is a separator, do not select
      if (isSeparator(index0)) {
        int next = findNextSelectable(index0);
        if (next != -1) {
          super.setSelectionInterval(next, next);
        }
        return;
      }
      super.setSelectionInterval(index0, index1);
    }

    private boolean isSeparator(int index) {
      ListModel<?> model = list.getModel();
      if (model.getSize() <= index || index < 0) return false;
      Object value = model.getElementAt(index);
      if (value instanceof LookupElement lookupElement && LookupCellRendererKt.isSeparator(lookupElement)) {
        return true;
      }
      return false;
    }

    private int findNextSelectable(int start) {
      ListModel<?> model = list.getModel();
      int size = model.getSize();
      for (int i = start + 1; i >= 0 && i < size; i++) {
        if (!isSeparator(i)) return i;
      }
      return -1;
    }

    @Override
    public boolean canBeSelected(int index) {
      return !isSeparator(index);
    }
  }
}
