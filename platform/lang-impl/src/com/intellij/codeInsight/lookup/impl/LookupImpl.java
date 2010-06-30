/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.CompletionLookupArranger;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.plaf.beg.BegPopupMenuBorder;
import com.intellij.ui.popup.PopupIcons;
import com.intellij.util.CollectConsumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.SortedList;
import com.intellij.util.ui.AsyncProcessIcon;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class LookupImpl extends LightweightHint implements Lookup, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.lookup.impl.LookupImpl");
  private static final int MAX_PREFERRED_COUNT = 5;

  private static final LookupItem EMPTY_LOOKUP_ITEM = LookupItem.fromString("preselect");

  private final Project myProject;
  private final Editor myEditor;

  private final Map<LookupElement, Collection<LookupElementAction>> myItemActions = new ConcurrentHashMap<LookupElement, Collection<LookupElementAction>>();
  private int myMinPrefixLength;
  private int myPreferredItemsCount;
  private RangeMarker myInitialOffset;
  private RangeMarker myInitialSelection;
  private long myShownStamp = -1;
  private String myInitialPrefix;
  private final LookupArranger myArranger;
  private final ArrayList<LookupElement> myItems;
  @Nullable private List<LookupElement> mySortedItems;

  private RangeMarker myLookupStartMarker;
  private boolean myShouldUpdateBounds;
  private final JList myList;
  private final LookupCellRenderer myCellRenderer;
  private Boolean myPositionedAbove = null;

  private CaretListener myEditorCaretListener;
  private SelectionListener myEditorSelectionListener;
  private EditorMouseListener myEditorMouseListener;

  private final ArrayList<LookupListener> myListeners = new ArrayList<LookupListener>();

  private boolean myDisposed = false;
  private boolean myHidden = false;
  private LookupElement myPreselectedItem = EMPTY_LOOKUP_ITEM;
  private boolean myDirty;
  private String myAdditionalPrefix = "";
  private final AsyncProcessIcon myProcessIcon;
  private volatile boolean myCalculating;
  private final JLabel myAdComponent;
  private volatile String myAdText;
  private volatile int myLookupWidth = 50;
  private static final int LOOKUP_HEIGHT = Integer.getInteger("idea.lookup.height", 11).intValue();

  public LookupImpl(Project project, Editor editor, @NotNull LookupArranger arranger){
    super(new JPanel(new BorderLayout()));
    myProject = project;
    myEditor = editor;
    myArranger = arranger;
    myItems = new ArrayList<LookupElement>();

    setInitialOffset(myEditor.getCaretModel().getOffset(), myEditor.getSelectionModel().getSelectionStart(), myEditor.getSelectionModel().getSelectionEnd());

    myProcessIcon = new AsyncProcessIcon("Completion progress");
    myProcessIcon.setVisible(false);
    myList = new JBList(new DefaultListModel());
    myCellRenderer = new LookupCellRenderer(this);
    myList.setCellRenderer(myCellRenderer);

    myList.setFocusable(false);

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setBackground(LookupCellRenderer.BACKGROUND_COLOR);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    getComponent().add(scrollPane, BorderLayout.NORTH);
    scrollPane.setBorder(null);

    JPanel bottomPanel = new JPanel(new BorderLayout());

    bottomPanel.add(myProcessIcon, BorderLayout.EAST);
    myAdComponent = HintUtil.createAdComponent(null);
    bottomPanel.add(myAdComponent, BorderLayout.CENTER);
    getComponent().add(bottomPanel, BorderLayout.SOUTH);
    getComponent().setBorder(new BegPopupMenuBorder());

    final ListModel model = myList.getModel();
    addEmptyItem((DefaultListModel)model);
    updateListHeight(model);
  }

  public AsyncProcessIcon getProcessIcon() {
    return myProcessIcon;
  }

  public boolean isCalculating() {
    return myCalculating;
  }

  public void setCalculating(final boolean calculating) {
    myCalculating = calculating;
  }

  public int getPreferredItemsCount() {
    return myPreferredItemsCount;
  }

  public void markDirty() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    myDirty = true;
    myPreselectedItem = null;
  }

  @TestOnly
  public void resort() {
    myDirty = false;
    myPreselectedItem = EMPTY_LOOKUP_ITEM;
    final ArrayList<LookupElement> items;
    synchronized (myItems) {
      items = new ArrayList<LookupElement>(myItems);
      myItems.clear();
      mySortedItems = null;
    }
    for (final LookupElement item : items) {
      addItem(item);
    }
    updateList();
  }

  public void addItem(LookupElement item) {
    final CollectConsumer<LookupElementAction> consumer = new CollectConsumer<LookupElementAction>();
    for (LookupActionProvider provider : LookupActionProvider.EP_NAME.getExtensions()) {
      provider.fillActions(item, this, consumer);
    }
    myItemActions.put(item, consumer.getResult());

    int maxWidth = myCellRenderer.updateMaximumWidth(item);
    myLookupWidth = Math.max(maxWidth, myLookupWidth);

    synchronized (myItems) {
      myItems.add(item);
      mySortedItems = null;
    }
  }

  public Collection<LookupElementAction> getActionsFor(LookupElement element) {
    final Collection<LookupElementAction> collection = myItemActions.get(element);
    return collection == null ? Collections.<LookupElementAction>emptyList() : collection;
  }

  public int getMinPrefixLength() {
    return myMinPrefixLength;
  }

  public JList getList() {
    return myList;
  }

  @NotNull
  private List<LookupElement> getSortedItems() {
    synchronized (myItems) {
      List<LookupElement> sortedItems = mySortedItems;
      if (sortedItems == null) {
        myArranger.sortItems(sortedItems = new ArrayList<LookupElement>(myItems));
        mySortedItems = sortedItems;
      }
      return sortedItems;
    }
  }

  public List<LookupElement> getItems() {
    final ArrayList<LookupElement> result = new ArrayList<LookupElement>();
    final Object[] objects;
    synchronized (myList) {
      objects = ((DefaultListModel)myList.getModel()).toArray();
    }
    for (final Object object : objects) {
      if (!(object instanceof EmptyLookupItem)) {
        result.add((LookupElement) object);
      }
    }
    return result;
  }

  public void setAdvertisementText(@Nullable String text) {
    myAdText = text;
  }

  public String getAdvertisementText() {
    return myAdText;
  }


  public String getAdditionalPrefix() {
    return myAdditionalPrefix;
  }

  public void setAdditionalPrefix(final String additionalPrefix) {
    myAdditionalPrefix = additionalPrefix;
    myInitialPrefix = null;
    markDirty();
    refreshUi();
  }

  private void updateList() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    final List<LookupElement> items = getSortedItems();
    SortedMap<Comparable, List<LookupElement>> itemsMap = new TreeMap<Comparable, List<LookupElement>>();
    int minPrefixLength = items.isEmpty() ? 0 : Integer.MAX_VALUE;
    for (final LookupElement item : items) {
      minPrefixLength = Math.min(item.getPrefixMatcher().getPrefix().length(), minPrefixLength);

      final Comparable relevance = myArranger.getRelevance(item);
      List<LookupElement> list = itemsMap.get(relevance);
      if (list == null) {
        itemsMap.put(relevance, list = new ArrayList<LookupElement>());
      }
      list.add(item);
    }

    if (myMinPrefixLength != minPrefixLength) {
      myLookupStartMarker = null;
    }
    myMinPrefixLength = minPrefixLength;

    Object oldSelected = !myDirty ? null : myList.getSelectedValue();
    boolean hasExactPrefixes;
    final boolean hasPreselectedItem;
    final boolean hasItems;
    DefaultListModel model = (DefaultListModel)myList.getModel();
    final LookupElement preselectedItem = myPreselectedItem;
    synchronized (myList) {
      model.clear();

      Set<LookupElement> firstItems = new THashSet<LookupElement>();

      hasExactPrefixes = addExactPrefixItems(model, firstItems, items);
      addMostRelevantItems(model, firstItems, itemsMap.values());
      hasPreselectedItem = addPreselectedItem(model, firstItems, preselectedItem);
      myPreferredItemsCount = firstItems.size();

      addRemainingItemsLexicographically(model, firstItems, items);

      hasItems = model.getSize() != 0;
      if (!hasItems) {
        addEmptyItem(model);
      }
    }

    updateListHeight(model);

    myAdComponent.setText(myAdText);

    if (hasItems) {
      myList.setFixedCellWidth(Math.max(myLookupWidth, myAdComponent.getPreferredSize().width));
    }

    if (hasItems) {
      if (oldSelected != null) {
        if (hasExactPrefixes || !ListScrollingUtil.selectItem(myList, oldSelected)) {
          selectMostPreferableItem();
        }
      }
      else {
        if (preselectedItem == EMPTY_LOOKUP_ITEM) {
          selectMostPreferableItem();
          myPreselectedItem = getCurrentItem();
        }
        else if (hasPreselectedItem && !hasExactPrefixes) {
          ListScrollingUtil.selectItem(myList, preselectedItem);
        }
        else {
          selectMostPreferableItem();
        }
      }
    }
  }

  private void updateListHeight(ListModel model) {
    myList.setFixedCellHeight(myCellRenderer.getListCellRendererComponent(myList, model.getElementAt(0), 0, false, false).getPreferredSize().height);

    myList.setVisibleRowCount(Math.min(model.getSize(), LOOKUP_HEIGHT));
  }

  private void addEmptyItem(DefaultListModel model) {
    LookupItem<String> item = new EmptyLookupItem(myCalculating ? " " : LangBundle.message("completion.no.suggestions"));
    item.setPrefixMatcher(new CamelHumpMatcher(""));
    if (!myCalculating) {
      final int maxWidth = myCellRenderer.updateMaximumWidth(item);
      myList.setFixedCellWidth(Math.max(maxWidth, myLookupWidth));
    }

    model.addElement(item);
  }

  private void addRemainingItemsLexicographically(DefaultListModel model, Set<LookupElement> firstItems, List<LookupElement> myItems) {
    for (LookupElement item : myItems) {
      if (!firstItems.contains(item) && prefixMatches(item)) {
        model.addElement(item);
      }
    }
  }

  private boolean addPreselectedItem(DefaultListModel model, Set<LookupElement> firstItems, @Nullable final LookupElement preselectedItem) {
    final boolean hasPreselectedItem = !myDirty && preselectedItem != EMPTY_LOOKUP_ITEM && preselectedItem != null;
    if (hasPreselectedItem && !firstItems.contains(preselectedItem)) {
      firstItems.add(preselectedItem);
      model.addElement(preselectedItem);
    }
    return hasPreselectedItem;
  }

  private void addMostRelevantItems(DefaultListModel model, Set<LookupElement> firstItems, final Collection<List<LookupElement>> sortedItems) {
    for (final List<LookupElement> elements : sortedItems) {
      final List<LookupElement> suitable = new SmartList<LookupElement>();
      for (final LookupElement item : elements) {
        if (!firstItems.contains(item) && prefixMatches(item)) {
          suitable.add(item);
        }
      }

      if (firstItems.size() + suitable.size() > MAX_PREFERRED_COUNT) break;
      for (final LookupElement item : suitable) {
        firstItems.add(item);
        model.addElement(item);
      }
    }
  }

  private boolean addExactPrefixItems(DefaultListModel model, Set<LookupElement> firstItems, final List<LookupElement> elements) {
    List<LookupElement> sorted = new SortedList<LookupElement>(new Comparator<LookupElement>() {
      public int compare(LookupElement o1, LookupElement o2) {
        //noinspection unchecked
        return myArranger.getRelevance(o1).compareTo(myArranger.getRelevance(o2));
      }
    });
    for (final LookupElement item : elements) {
      if (isExactPrefixItem(item)) {
        sorted.add(item);

      }
    }
    for (final LookupElement item : sorted) {
      model.addElement(item);
      firstItems.add(item);
    }

    return !firstItems.isEmpty();
  }

  private boolean isExactPrefixItem(LookupElement item) {
    return item.getAllLookupStrings().contains(item.getPrefixMatcher().getPrefix() + myAdditionalPrefix);
  }

  private boolean prefixMatches(final LookupElement item) {
    if (myAdditionalPrefix.length() == 0) return item.isPrefixMatched();

    return item.getPrefixMatcher().cloneWithPrefix(item.getPrefixMatcher().getPrefix() + myAdditionalPrefix).prefixMatches(item);
  }

  /**
   * @return point in layered pane coordinate system.
   */
  public Point calculatePosition(){
    Dimension dim = getComponent().getPreferredSize();
    int lookupStart = getLookupStart();
    if (lookupStart < 0) {
      LOG.error(lookupStart + "; minprefix=" + myMinPrefixLength + "; offset=" + myEditor.getCaretModel().getOffset() + "; element=" +
                getPsiElement());
    }

    LogicalPosition pos = myEditor.offsetToLogicalPosition(lookupStart);
    Point location = myEditor.logicalPositionToXY(pos);
    location.y += myEditor.getLineHeight();
    JComponent editorComponent = myEditor.getComponent();
    JComponent internalComponent = myEditor.getContentComponent();
    final JRootPane rootPane = editorComponent.getRootPane();
    if (rootPane == null) {
      LOG.error(myArranger);
    }
    JLayeredPane layeredPane = rootPane.getLayeredPane();
    Point layeredPanePoint=SwingUtilities.convertPoint(internalComponent,location, layeredPane);
    layeredPanePoint.x -= myCellRenderer.getIconIndent();
    layeredPanePoint.x -= getComponent().getInsets().left;
    if (dim.width > layeredPane.getWidth()){
      dim.width = layeredPane.getWidth();
    }
    int wshift = layeredPane.getWidth() - (layeredPanePoint.x + dim.width);
    if (wshift < 0){
      layeredPanePoint.x += wshift;
    }

    int shiftLow = layeredPane.getHeight() - (layeredPanePoint.y + dim.height);
    int shiftHigh = layeredPanePoint.y - dim.height;
    if (!isPositionedAboveCaret()) {
      myPositionedAbove = shiftLow < 0 && shiftLow < shiftHigh ? Boolean.TRUE : Boolean.FALSE;
    }
    if (isPositionedAboveCaret()) {
      layeredPanePoint.y -= dim.height + myEditor.getLineHeight();
      if (pos.line == 0) {
        layeredPanePoint.y += 1;
        //otherwise the lookup won't intersect with the editor and every editor's resize (e.g. after typing in console) will close the lookup
      }
    }
    return layeredPanePoint;
  }

  public void finishLookup(final char completionChar) {
    if (myShownStamp > 0 && System.currentTimeMillis() - myShownStamp < 42 && !ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    final LookupElement item = (LookupElement)myList.getSelectedValue();
    doHide(false);
    if (item == null ||
        item instanceof EmptyLookupItem ||
        item.getObject() instanceof DeferredUserLookupValue &&
        item.as(LookupItem.class) != null &&
        !((DeferredUserLookupValue)item.getObject()).handleUserSelection(item.as(LookupItem.class), myProject)) {
      fireItemSelected(null, completionChar);
      return;
    }

    final PsiFile file = getPsiFile();
    if (file != null && !WriteCommandAction.ensureFilesWritable(myProject, Arrays.asList(file))) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        EditorModificationUtil.deleteSelectedText(myEditor);
        final int caretOffset = myEditor.getCaretModel().getOffset();
        final String prefix = item.getPrefixMatcher().getPrefix();
        int lookupStart = caretOffset - prefix.length() - myAdditionalPrefix.length();

        final String lookupString = item.getLookupString();
        if (!StringUtil.startsWithConcatenationOf(lookupString, prefix, myAdditionalPrefix)) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.camelHumps");
        }

        myEditor.getDocument().replaceString(lookupStart, caretOffset, lookupString);

        int offset = lookupStart + lookupString.length();
        myEditor.getCaretModel().moveToOffset(offset);
        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        myEditor.getSelectionModel().removeSelection();
      }
    });

    fireItemSelected(item, completionChar);
  }

  public int getLookupStart() {
    if (myLookupStartMarker == null) {
      final int start = calcLookupStart();
      myLookupStartMarker = myEditor.getDocument().createRangeMarker(start, start);
      myLookupStartMarker.setGreedyToLeft(true);
      myShouldUpdateBounds = true;
    }

    return myLookupStartMarker.getStartOffset();
  }

  @Override
  protected void beforeShow() {
    if (isRealPopup()) {
      getComponent().setBorder(null);
    }
  }

  public void show(){
    assert !myDisposed;

    myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        if (myLookupStartMarker != null && !myLookupStartMarker.isValid()){
          hide();
        }
      }
    }, this);

    myEditorCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e){
        caretOrSelectionChanged();
      }
    };
    myEditorSelectionListener = new SelectionListener() {
      public void selectionChanged(final SelectionEvent e) {
        caretOrSelectionChanged();
      }
    };
    myEditor.getCaretModel().addCaretListener(myEditorCaretListener);
    myEditor.getSelectionModel().addSelectionListener(myEditorSelectionListener);

    myEditorMouseListener = new EditorMouseAdapter() {
      public void mouseClicked(EditorMouseEvent e){
        e.consume();
        hide();
      }
    };
    myEditor.addEditorMouseListener(myEditorMouseListener);

    myList.addListSelectionListener(new ListSelectionListener() {
      private LookupElement oldItem = null; 

      public void valueChanged(ListSelectionEvent e){
        LookupElement item = getCurrentItem();
        if (oldItem != item) {
          fireCurrentItemChanged(item);
        }
        oldItem = item;
      }
    });

    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e){
        final Point point = e.getPoint();
        final int i = myList.locationToIndex(point);
        if (i >= 0) {
          final LookupElement selected = (LookupElement)myList.getModel().getElementAt(i);
          if (selected != null) {
            final Collection<LookupElementAction> actions = getActionsFor(selected);
            if (!actions.isEmpty() &&
                e.getClickCount() == 1 &&
                point.x >= myList.getCellBounds(i, i).width - PopupIcons.EMPTY_ICON.getIconWidth()) {
              ShowLookupActionsHandler.showItemActions(LookupImpl.this, actions);
              return;
            }
          }
        }

        if (e.getClickCount() == 2){
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              finishLookup(NORMAL_SELECT_CHAR);
            }
          }, "", null);
        }
      }
    });

    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    Point p = calculatePosition();
    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    hintManager.showEditorHint(this, myEditor, p, HintManagerImpl.HIDE_BY_ESCAPE | HintManagerImpl.UPDATE_BY_SCROLLING, 0, false);

    myShownStamp = System.currentTimeMillis();
  }

  private void caretOrSelectionChanged() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myEditor.isDisposed() || myProject.isDisposed()) return;

        if (!myInitialOffset.isValid() || !myInitialSelection.isValid()) {
          hide();
          return;
        }

        final SelectionModel selectionModel = myEditor.getSelectionModel();
        if (selectionModel.hasSelection()) {
          if (myInitialSelection.getStartOffset() != selectionModel.getSelectionStart() ||
              myInitialSelection.getEndOffset() != selectionModel.getSelectionEnd()) {
            hide();
            return;
          }
        } else {
          if (myEditor.getCaretModel().getOffset() != myInitialOffset.getStartOffset() + myAdditionalPrefix.length()) {
            hide();
            return;
          }
        }

        if (myShouldUpdateBounds) {
          myShouldUpdateBounds = false;
          refreshUi();
        }
      }
    });
  }

  private int calcLookupStart() {
    int offset = myEditor.getSelectionModel().hasSelection()
                 ? myEditor.getSelectionModel().getSelectionStart()
                 : myEditor.getCaretModel().getOffset();
    return Math.max(offset - myMinPrefixLength, 0);
  }

  private void selectMostPreferableItem() {
    final List<LookupElement> sortedItems = getItems();
    final int index = doSelectMostPreferableItem(sortedItems);
    myList.setSelectedIndex(index);

    if (index >= 0 && index < myList.getModel().getSize()){
      ListScrollingUtil.selectItem(myList, index);
    }
    else if (!sortedItems.isEmpty()) {
      ListScrollingUtil.selectItem(myList, 0);
    }
  }

  @Nullable
  public LookupElement getCurrentItem(){
    LookupElement item = (LookupElement)myList.getSelectedValue();
    return item instanceof EmptyLookupItem ? null : item;
  }

  public void setCurrentItem(LookupElement item){
    ListScrollingUtil.selectItem(myList, item);
  }

  public void addLookupListener(LookupListener listener){
    myListeners.add(listener);
  }

  public void removeLookupListener(LookupListener listener){
    myListeners.remove(listener);
  }

  public Rectangle getCurrentItemBounds(){
    int index = myList.getSelectedIndex();
    Rectangle itmBounds = myList.getCellBounds(index, index);
    if (itmBounds == null){
      return null;
    }
    Point layeredPanePoint=SwingUtilities.convertPoint(myList,itmBounds.x,itmBounds.y,getComponent());
    itmBounds.x = layeredPanePoint.x;
    itmBounds.y = layeredPanePoint.y;
    return itmBounds;
  }

  public void fireItemSelected(final LookupElement item, char completionChar){
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (item != null) {
      myArranger.itemSelected(item, this);
    }

    if (!myListeners.isEmpty()){
      LookupEvent event = new LookupEvent(this, item, completionChar);
      LookupListener[] listeners = myListeners.toArray(new LookupListener[myListeners.size()]);
      for (LookupListener listener : listeners) {
        try {
          listener.itemSelected(event);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private void fireLookupCanceled(){
    if (!myListeners.isEmpty()){
      LookupEvent event = new LookupEvent(this, null);
      LookupListener[] listeners = myListeners.toArray(new LookupListener[myListeners.size()]);
      for (LookupListener listener : listeners) {
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
      LookupEvent event = new LookupEvent(this, item);
      LookupListener[] listeners = myListeners.toArray(new LookupListener[myListeners.size()]);
      for (LookupListener listener : listeners) {
        listener.currentItemChanged(event);
      }
    }
  }

  private static int divideString(String lookupString, PrefixMatcher matcher) {
    for (int i = matcher.getPrefix().length(); i <= lookupString.length(); i++) {
      if (matcher.prefixMatches(lookupString.substring(0, i))) {
        return i;
      }
    }
    return -1;
  }

  public boolean fillInCommonPrefix(boolean explicitlyInvoked) {
    if (explicitlyInvoked && myCalculating) return false;
    if (!explicitlyInvoked && myDirty) return false;

    ListModel listModel = myList.getModel();
    if (listModel.getSize() <= 1) return false;

    if (listModel.getSize() == 0) return false;

    final LookupElement firstItem = (LookupElement)listModel.getElementAt(0);
    if (listModel.getSize() == 1 && firstItem instanceof EmptyLookupItem) return false;

    final PrefixMatcher firstItemMatcher = firstItem.getPrefixMatcher();
    final String oldPrefix = firstItemMatcher.getPrefix();
    String presentPrefix = oldPrefix + myAdditionalPrefix;
    final PrefixMatcher matcher = firstItemMatcher.cloneWithPrefix(presentPrefix);
    String lookupString = firstItem.getLookupString();
    int div = divideString(lookupString, matcher);
    if (div < 0) return false;

    String beforeCaret = lookupString.substring(0, div);
    String afterCaret = lookupString.substring(div);


    for (int i = 1; i < listModel.getSize(); i++) {
      LookupElement item = (LookupElement)listModel.getElementAt(i);
      if (!oldPrefix.equals(item.getPrefixMatcher().getPrefix())) return false;

      lookupString = item.getLookupString();
      div = divideString(lookupString, item.getPrefixMatcher().cloneWithPrefix(presentPrefix));
      if (div < 0) return false;

      String _afterCaret = lookupString.substring(div);
      if (beforeCaret != null) {
        if (div != beforeCaret.length() || !lookupString.startsWith(beforeCaret)) {
          beforeCaret = null;
        }
      }

      while (afterCaret.length() > 0) {
        if (_afterCaret.startsWith(afterCaret)) {
          break;
        }
        afterCaret = afterCaret.substring(0, afterCaret.length() - 1);
      }
      if (afterCaret.length() == 0) return false;
    }

    if (myAdditionalPrefix.length() == 0 && myInitialPrefix == null && !explicitlyInvoked) {
      myInitialPrefix = presentPrefix;
    } else {
      myInitialPrefix = null;
    }

    EditorModificationUtil.deleteSelectedText(myEditor);
    int offset = myEditor.getCaretModel().getOffset();
    if (beforeCaret != null) { // correct case, expand camel-humps
      final int start = offset - presentPrefix.length();
      setInitialOffset(offset, offset, offset);
      myAdditionalPrefix = "";
      myEditor.getDocument().replaceString(start, offset, beforeCaret);
      presentPrefix = beforeCaret;
    }

    offset = myEditor.getCaretModel().getOffset();
    myEditor.getDocument().insertString(offset, afterCaret);

    final String newPrefix = presentPrefix + afterCaret;
    synchronized (myItems) {
      for (Iterator<LookupElement> iterator = myItems.iterator(); iterator.hasNext();) {
        LookupElement item = iterator.next();
        if (!item.setPrefixMatcher(item.getPrefixMatcher().cloneWithPrefix(newPrefix))) {
          iterator.remove();
          mySortedItems = null;
        }
      }
    }
    myAdditionalPrefix = "";
    myAdditionalPrefix = "";
    updateList();

    offset += afterCaret.length();
    setInitialOffset(offset, offset, offset);
    myEditor.getCaretModel().moveToOffset(offset);
    return true;
  }

  private void setInitialOffset(int offset, int selStart, int selEnd) {
    myInitialOffset = myEditor.getDocument().createRangeMarker(TextRange.from(offset, 0));
    myInitialSelection = myEditor.getDocument().createRangeMarker(new TextRange(selStart, selEnd));
  }

  public PsiFile getPsiFile() {
    return PsiDocumentManager.getInstance(myEditor.getProject()).getPsiFile(myEditor.getDocument());
  }

  public boolean isCompletion() {
    return myArranger instanceof CompletionLookupArranger;
  }

  public PsiElement getPsiElement() {
    PsiFile file = getPsiFile();
    if (file == null) return null;

    int offset = getLookupStart();
    if (offset > 0) return file.findElementAt(offset - 1);

    return file.findElementAt(0);
  }

  public Editor getEditor() {
    return myEditor;
  }

  public boolean isPositionedAboveCaret(){
    return myPositionedAbove != null && myPositionedAbove.booleanValue();
  }

  public void hide(){
    ApplicationManager.getApplication().assertIsDispatchThread();

    //if (IdeEventQueue.getInstance().getPopupManager().closeAllPopups()) return;

    if (myDisposed) return;

    doHide(true);
  }

  private void doHide(final boolean fireCanceled) {
    assert !myDisposed;
    myHidden = true;

    super.hide();

    Disposer.dispose(this);

    if (fireCanceled) {
      fireLookupCanceled();
    }
  }

  public void restorePrefix() {
    if (myInitialPrefix != null) {
      myEditor.getDocument().replaceString(getLookupStart(), myEditor.getCaretModel().getOffset(), myInitialPrefix);
    }
  }

  public void dispose() {
    assert myHidden;
    assert !myDisposed;
    myDisposed = true;
    myProcessIcon.dispose();
    if (myEditorCaretListener != null) {
      myEditor.getCaretModel().removeCaretListener(myEditorCaretListener);
      myEditor.getSelectionModel().removeSelectionListener(myEditorSelectionListener);
    }
    if (myEditorMouseListener != null) {
      myEditor.removeEditorMouseListener(myEditorMouseListener);
    }
  }

  private int doSelectMostPreferableItem(List<LookupElement> items) {
    if (items.isEmpty()) {
      return -1;
    }

    if (items.size() == 1) {
      return 0;
    }

    for (int i = 0; i < items.size(); i++) {
      LookupElement item = items.get(i);
      if (isExactPrefixItem(item)) {
        return i;
      }
    }

    final int index = myArranger.suggestPreselectedItem(items);
    assert index >= 0 && index < items.size();
    return index;
  }

  public void refreshUi() {
    updateList();

    if (isVisible() && !ApplicationManager.getApplication().isUnitTestMode()) {
      if (myEditor.getComponent().getRootPane() == null) {
        LOG.error("Null root pane");
      }

      Point point = calculatePosition();
      updateBounds(point.x,point.y);

      HintManagerImpl.adjustEditorHintPosition(this, myEditor, point);
    }
  }

  @TestOnly
  public LookupArranger getLookupModel() {
    return myArranger;
  }
}
