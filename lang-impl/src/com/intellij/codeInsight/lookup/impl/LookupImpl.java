package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionPreferencePolicy;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeEventQueue;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.plaf.beg.BegPopupMenuBorder;
import com.intellij.util.SmartList;
import com.intellij.util.ui.AsyncProcessIcon;
import gnu.trove.THashSet;
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

  private static final LookupItem EMPTY_LOOKUP_ITEM = new LookupItem("preselect", "preselect");

  private final Project myProject;
  private final Editor myEditor;
  private final SortedSet<LookupElement> myItems;
  private final SortedMap<LookupItemWeightComparable, SortedSet<LookupElement>> myItemsMap;
  private int myMinPrefixLength;
  private int myPreferredItemsCount;
  private int myInitialOffset;
  private final LookupItemPreferencePolicy myItemPreferencePolicy;

  private RangeMarker myLookupStartMarker;
  private final JList myList;
  private final LookupCellRenderer myCellRenderer;
  private Boolean myPositionedAbove = null;

  private CaretListener myEditorCaretListener;
  private EditorMouseListener myEditorMouseListener;

  private final ArrayList<LookupListener> myListeners = new ArrayList<LookupListener>();

  private boolean myDisposed = false;
  private LookupElement myPreselectedItem = EMPTY_LOOKUP_ITEM;
  private boolean myDirty;
  private String myAdditionalPrefix = "";
  private PsiElement myElement;
  private final AsyncProcessIcon myProcessIcon;
  private final Comparator<? super LookupElement> myComparator;
  private volatile boolean myCalculating;

  public LookupImpl(Project project,
                    Editor editor,
                    LookupElement[] items, LookupItemPreferencePolicy itemPreferencePolicy, final String bottomText){
    super(new JPanel(new BorderLayout()));
    myProject = project;
    myEditor = editor;
    myItemPreferencePolicy = itemPreferencePolicy;
    myInitialOffset = myEditor.getSelectionModel().hasSelection() ? myEditor.getSelectionModel().getSelectionStart() : myEditor.getCaretModel().getOffset();

    final Document document = myEditor.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    myElement = psiFile == null ? null : psiFile.findElementAt(myEditor.getCaretModel().getOffset());

    final PsiProximityComparator proximityComparator = new PsiProximityComparator(myElement == null ? psiFile : myElement);
    myComparator = new Comparator<LookupElement>() {
      public int compare(LookupElement o1, LookupElement o2) {
        if (o1 instanceof LookupItem && o2 instanceof LookupItem) {
          double priority1 = ((LookupItem)o1).getPriority();
          double priority2 = ((LookupItem)o2).getPriority();
          if (priority1 > priority2) return -1;
          if (priority2 > priority1) return 1;

          int grouping1 = ((LookupItem)o1).getGrouping();
          int grouping2 = ((LookupItem)o2).getGrouping();
          if (grouping1 > grouping2) return -1;
          if (grouping2 > grouping1) return 1;
        }

        int stringCompare = o1.getLookupString().compareToIgnoreCase(o2.getLookupString());
        if (stringCompare != 0) return stringCompare;

        final int proximityCompare = proximityComparator.compare(o1.getObject(), o2.getObject());
        if (proximityCompare != 0) return proximityCompare;

        return o1.hashCode() - o2.hashCode();
      }
    };
    myItems = new TreeSet<LookupElement>(myComparator);
    myItemsMap = new TreeMap<LookupItemWeightComparable, SortedSet<LookupElement>>();

    myProcessIcon = new AsyncProcessIcon("Completion progress");
    myProcessIcon.setVisible(false);
    myList = new JList(new DefaultListModel());
    myCellRenderer = new LookupCellRenderer(this);
    myList.setCellRenderer(myCellRenderer);
    myList.setFixedCellWidth(50);

    for (final LookupElement item : items) {
      addItem(item);
    }

    updateList();
    
    myList.setFocusable(false);

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setBackground(LookupCellRenderer.BACKGROUND_COLOR);

    JScrollPane scrollPane = new JScrollPane(myList);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    getComponent().add(scrollPane, BorderLayout.NORTH);
    scrollPane.setBorder(null);

    JPanel bottomPanel = new JPanel(new BorderLayout());

    bottomPanel.add(myProcessIcon, BorderLayout.EAST);
    final JComponent adComponent = HintUtil.createAdComponent(bottomText);
    if (StringUtil.isNotEmpty(bottomText)) {
      adComponent.setPreferredSize(new Dimension(adComponent.getPreferredSize().width, myProcessIcon.getPreferredSize().height));
    }
    bottomPanel.add(adComponent, BorderLayout.CENTER);
    getComponent().add(bottomPanel, BorderLayout.SOUTH);
    getComponent().setBorder(new BegPopupMenuBorder());


    selectMostPreferableItem();
  }

  public AsyncProcessIcon getProcessIcon() {
    return myProcessIcon;
  }

  public void setCalculating(final boolean calculating) {
    myCalculating = calculating;
  }

  public int getPreferredItemsCount() {
    return myPreferredItemsCount;
  }

  public void markDirty() {
    myDirty = true;
    myPreselectedItem = null;
  }

  @TestOnly
  public void resort() {
    final ArrayList<LookupElement> items = new ArrayList<LookupElement>(myItems);
    myDirty = false;
    myPreselectedItem = EMPTY_LOOKUP_ITEM;
    myItemsMap.clear();
    myItems.clear();
    for (final LookupElement item : items) {
      addItem(item);
    }
    updateList();
  }

  public synchronized void addItem(LookupElement item) {
    myItems.add(item);
    addItemWeight(item);
    int maxWidth = myCellRenderer.updateMaximumWidth(item);
    myList.setFixedCellWidth(Math.max(maxWidth, myList.getFixedCellWidth()));
  }

  private void addItemWeight(final LookupElement item) {
    final Comparable[] weight = getWeight(myItemPreferencePolicy, myElement, item);
    final LookupItemWeightComparable key = new LookupItemWeightComparable(item instanceof LookupItem ? ((LookupItem)item).getPriority() : 0, weight);
    SortedSet<LookupElement> list = myItemsMap.get(key);
    if (list == null) myItemsMap.put(key, list = new TreeSet<LookupElement>(myComparator));
    list.add(item);
  }

  public LookupItemPreferencePolicy getItemPreferencePolicy() {
    return myItemPreferencePolicy;
  }

  private static Comparable[] getWeight(final LookupItemPreferencePolicy itemPreferencePolicy, final PsiElement context,
                                        final LookupElement item) {
    if (itemPreferencePolicy instanceof CompletionPreferencePolicy) {
      return ((CompletionPreferencePolicy)itemPreferencePolicy).getWeight(item);
    }
    Comparable i = null;
    if (item.getObject() instanceof PsiElement) {
      i = PsiProximityComparator.getProximity((PsiElement)item.getObject(), context);
    }
    return new Comparable[]{i};
  }

  public int getMinPrefixLength() {
    return myMinPrefixLength;
  }

  public JList getList() {
    return myList;
  }

  public synchronized LookupElement[] getItems(){
    return myItems.toArray(new LookupElement[myItems.size()]);
  }

  public String getAdditionalPrefix() {
    return myAdditionalPrefix;
  }

  public void setAdditionalPrefix(final String additionalPrefix) {
    myAdditionalPrefix = additionalPrefix;
    markDirty();
    updateList();
  }

  public final synchronized void updateList() {
    int minPrefixLength = myItems.isEmpty() ? 0 : Integer.MAX_VALUE;
    for (final LookupElement item : myItems) {
      minPrefixLength = Math.min(item.getPrefixMatcher().getPrefix().length(), minPrefixLength);
    }
    if (myMinPrefixLength != minPrefixLength) {
      myLookupStartMarker = null;
    }
    myMinPrefixLength = minPrefixLength;

    Object oldSelected = !myDirty ? null : myList.getSelectedValue();
    DefaultListModel model = (DefaultListModel)myList.getModel();
    model.clear();

    ArrayList<LookupElement> allItems = new ArrayList<LookupElement>();
    Set<LookupElement> firstItems = new THashSet<LookupElement>();

    final boolean hasPreselectedItem = !myDirty && myPreselectedItem != EMPTY_LOOKUP_ITEM;
    boolean addPreselected = hasPreselectedItem;

    for (final LookupItemWeightComparable comparable : myItemsMap.keySet()) {
      final List<LookupElement> suitable = new SmartList<LookupElement>();
      for (final LookupElement item : myItemsMap.get(comparable)) {
        if (prefixMatches(item)) {
          suitable.add(item);
        }
      }

      if (allItems.size() + suitable.size() + (addPreselected ? 1 : 0) > MAX_PREFERRED_COUNT) break;
      for (final LookupElement item : suitable) {
        allItems.add(item);
        firstItems.add(item);
        model.addElement(item);

        if (hasPreselectedItem && item == myPreselectedItem) {
          addPreselected = false;
        }
      }
    }
    if (addPreselected) {
      allItems.add(myPreselectedItem);
      firstItems.add(myPreselectedItem);
      model.addElement(myPreselectedItem);
    }

    myPreferredItemsCount = allItems.size();

    for (LookupElement item : myItems) {
      if (!firstItems.contains(item) && prefixMatches(item)) {
        model.addElement(item);
        allItems.add(item);
      }
    }
    boolean isEmpty = allItems.isEmpty();
    if (isEmpty) {
      LookupItem<String> item = new EmptyLookupItem(myCalculating ? " " : LangBundle.message("completion.no.suggestions"));
      item.setPrefixMatcher(new CamelHumpMatcher(""));
      if (!myCalculating) {
        final int maxWidth = myCellRenderer.updateMaximumWidth(item);
        myList.setFixedCellWidth(Math.max(maxWidth, myList.getFixedCellWidth()));
      }

      model.addElement(item);
      allItems.add(item);
    }
    myList.setFixedCellHeight(myCellRenderer.getListCellRendererComponent(myList, myList.getModel().getElementAt(0), 0, false, false).getPreferredSize().height);

    myList.setVisibleRowCount(Math.min(myList.getModel().getSize(), CodeInsightSettings.getInstance().LOOKUP_HEIGHT));

    if (!isEmpty) {
      if (oldSelected != null) {
        if (!ListScrollingUtil.selectItem(myList, oldSelected)) {
          selectMostPreferableItem();
        }
      } else {
        if (myPreselectedItem == EMPTY_LOOKUP_ITEM) {
          selectMostPreferableItem();
          myPreselectedItem = getCurrentItem();
        } else if (hasPreselectedItem) {
          ListScrollingUtil.selectItem(myList, myPreselectedItem);
        } else {
          selectMostPreferableItem();
        }
      }
    }
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
    LogicalPosition pos = myEditor.offsetToLogicalPosition(lookupStart);
    Point location = myEditor.logicalPositionToXY(pos);
    location.y += myEditor.getLineHeight();
    JComponent editorComponent = myEditor.getComponent();
    JComponent internalComponent = myEditor.getContentComponent();
    final JRootPane rootPane = editorComponent.getRootPane();
    LOG.assertTrue(rootPane != null, myItems);
    JLayeredPane layeredPane = rootPane.getLayeredPane();
    Point layeredPanePoint=SwingUtilities.convertPoint(internalComponent,location, layeredPane);
    layeredPanePoint.x -= myCellRenderer.getIconIndent();
    if (dim.width > layeredPane.getWidth()){
      dim.width = layeredPane.getWidth();
    }
    int wshift = layeredPane.getWidth() - (layeredPanePoint.x + dim.width);
    if (wshift < 0){
      layeredPanePoint.x += wshift;
    }

    int shiftLow = layeredPane.getHeight() - (layeredPanePoint.y + dim.height);
    int shiftHigh = layeredPanePoint.y - dim.height;
    myPositionedAbove = shiftLow < 0 && shiftLow < shiftHigh ? Boolean.TRUE : Boolean.FALSE;

    if (myPositionedAbove.booleanValue()){
      layeredPanePoint.y -= dim.height + myEditor.getLineHeight();
    }
    return layeredPanePoint;
  }

  public void finishLookup(final char completionChar) {
    final LookupElement item = (LookupElement)myList.getSelectedValue();
    doHide(false);
    if (item == null ||
        item instanceof EmptyLookupItem ||
        item.getObject() instanceof DeferredUserLookupValue && item instanceof LookupItem &&
        !((DeferredUserLookupValue)item.getObject()).handleUserSelection((LookupItem)item, myProject)) {
      fireItemSelected(null, completionChar);
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run(){
        EditorModificationUtil.deleteSelectedText(myEditor);
        final int caretOffset = myEditor.getCaretModel().getOffset();
        final String prefix = item.getPrefixMatcher().getPrefix();
        int lookupStart = caretOffset - prefix.length() - myAdditionalPrefix.length();

        final String lookupString = item.getLookupString();
        if (!lookupString.startsWith(prefix + myAdditionalPrefix)) {
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
    return myLookupStartMarker != null ? myLookupStartMarker.getStartOffset() : calcLookupStart();
  }

  public void show(){
    int lookupStart = calcLookupStart();
    myLookupStartMarker = myEditor.getDocument().createRangeMarker(lookupStart, lookupStart);
    myLookupStartMarker.setGreedyToLeft(true);

    myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        if (myLookupStartMarker != null && !myLookupStartMarker.isValid()){
          hide();
        }
      }
    }, this);

    myEditorCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e){
        int curOffset = myEditor.getCaretModel().getOffset();
        final LookupElement item = getCurrentItem();
        if (item == null || item == EMPTY_LOOKUP_ITEM) return;

        if (curOffset != myInitialOffset + myAdditionalPrefix.length()) {
          hide();
        }
      }
    };
    myEditor.getCaretModel().addCaretListener(myEditorCaretListener);

    myEditorMouseListener = new EditorMouseAdapter() {
      public void mouseClicked(EditorMouseEvent e){
        e.consume();
        hide();
      }
    };
    myEditor.addEditorMouseListener(myEditorMouseListener);

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e){
        LookupElement item = getCurrentItem();
        fireCurrentItemChanged(item);
      }
    });

    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e){
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
  }

  private int calcLookupStart() {
    int offset = myEditor.getSelectionModel().hasSelection()
                 ? myEditor.getSelectionModel().getSelectionStart()
                 : myEditor.getCaretModel().getOffset();
    return offset - myMinPrefixLength;
  }

  private void selectMostPreferableItem(){
    final int index = doSelectMostPreferableItem(myItemPreferencePolicy, ((DefaultListModel)myList.getModel()).toArray());
    myList.setSelectedIndex(index);

    if (index >= 0 && index < myList.getModel().getSize()){
      ListScrollingUtil.selectItem(myList, index);
    }
    else if (!myItems.isEmpty()) {
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
    Rectangle listBounds=myList.getBounds();
    final JRootPane pane = myList.getRootPane();
    if (pane == null) {
      LOG.assertTrue(false, myItems);
    }
    JLayeredPane layeredPane= pane.getLayeredPane();
    Point layeredPanePoint=SwingUtilities.convertPoint(myList,listBounds.x,listBounds.y,layeredPane);
    itmBounds.x = layeredPanePoint.x;
    itmBounds.y = layeredPanePoint.y;
    return itmBounds;
  }

  public void fireItemSelected(final LookupElement item, char completionChar){
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (item != null && myItemPreferencePolicy != null){
      myItemPreferencePolicy.itemSelected(item, this);
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
      div = divideString(lookupString, item.getPrefixMatcher());
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

    EditorModificationUtil.deleteSelectedText(myEditor);
    int offset = myEditor.getCaretModel().getOffset();
    if (beforeCaret != null) { // correct case, expand camel-humps
      final int start = offset - presentPrefix.length();
      myInitialOffset = start + beforeCaret.length();
      myEditor.getDocument().replaceString(start, offset, beforeCaret);
      presentPrefix = beforeCaret;
    }

    offset = myEditor.getCaretModel().getOffset();
    myEditor.getDocument().insertString(offset, afterCaret);

    final String newPrefix = presentPrefix + afterCaret;
    for (Iterator<LookupElement> it = myItems.iterator(); it.hasNext();) {
      LookupElement item = it.next();
      if (!item.setPrefixMatcher(item.getPrefixMatcher().cloneWithPrefix(newPrefix))) {
        it.remove();
      }
    }
    myAdditionalPrefix = "";
    updateList();

    offset += afterCaret.length();
    myInitialOffset = offset;
    myEditor.getCaretModel().moveToOffset(offset);
    return true;
  }

  public PsiFile getPsiFile() {
    return PsiDocumentManager.getInstance(myEditor.getProject()).getPsiFile(myEditor.getDocument());
  }

  public boolean isCompletion() {
    return myItemPreferencePolicy instanceof CompletionPreferencePolicy;
  }

  public PsiElement getPsiElement() {
    PsiFile file = getPsiFile();
    if (file == null) return null;

    int offset = getEditor().getCaretModel().getOffset();
    if (offset > 0) return file.findElementAt(offset - 1);

    return file.findElementAt(offset + 1);
  }

  public Editor getEditor() {
    return myEditor;
  }

  public boolean isPositionedAboveCaret(){
    return myPositionedAbove != null && myPositionedAbove.booleanValue();
  }

  public void hide(){
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (IdeEventQueue.getInstance().getPopupManager().closeAllPopups()) return;

    if (myDisposed) return;

    doHide(true);
  }

  private void doHide(final boolean fireCanceled) {
    assert !myDisposed;

    super.hide();

    Disposer.dispose(this);

    if (fireCanceled) {
      fireLookupCanceled();
    }
  }

  public void dispose() {
    assert !myDisposed;
    myDisposed = true;
    myProcessIcon.dispose();
    if (myEditorCaretListener != null) {
      myEditor.getCaretModel().removeCaretListener(myEditorCaretListener);
    }
    if (myEditorMouseListener != null) {
      myEditor.removeEditorMouseListener(myEditorMouseListener);
    }
  }

  public static int doSelectMostPreferableItem(final LookupItemPreferencePolicy itemPreferencePolicy, Object[] items) {
    if (itemPreferencePolicy == null){
      return -1;
    }
    else{
      LookupElement prefItem = null;
      int prefItemIndex = -1;

      for(int i = 0; i < items.length; i++){
        LookupElement item = (LookupElement)items[i];
        final Object obj = item.getObject();
        if (obj instanceof PsiElement && !((PsiElement)obj).isValid()) continue;
        if (prefItem == null){
          prefItem = item;
          prefItemIndex = i;
        }
        else{
          int d = itemPreferencePolicy.compare(item, prefItem);
          if (d < 0){
            prefItem = item;
            prefItemIndex = i;
          }
        }
      }
      return prefItem != null ? prefItemIndex : -1;
    }
  }

  public void adaptSize() {
    if (isVisible()) {
      HintManagerImpl.getInstanceImpl().adjustEditorHintPosition(this, myEditor, getComponent().getLocation());
    }
  }

  public synchronized LookupElement[] getSortedItems() {
    final LookupElement[] result = new LookupElement[myList.getModel().getSize()];
    ((DefaultListModel)myList.getModel()).copyInto(result);
    return result;
  }

}
