package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.CompletionPreferencePolicy;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
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
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.plaf.beg.BegPopupMenuBorder;
import com.intellij.ui.popup.PopupIcons;
import com.intellij.util.CollectConsumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.SortedList;
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
  private final SortedList<LookupElement> myItems;
  private final SortedMap<LookupItemWeightComparable, SortedList<LookupElement>> myItemsMap;
  private final Map<LookupElement, Collection<LookupElementAction>> myItemActions = new ConcurrentHashMap<LookupElement, Collection<LookupElementAction>>();
  private int myMinPrefixLength;
  private int myPreferredItemsCount;
  private RangeMarker myInitialOffset;
  private RangeMarker myInitialSelection;
  private long myShownStamp = -1;
  private String myInitialPrefix;
  @Nullable private final LookupItemPreferencePolicy myItemPreferencePolicy;

  private RangeMarker myLookupStartMarker;
  private int myOldLookupStartOffset;
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
  private final PsiElement myElement;
  private final AsyncProcessIcon myProcessIcon;
  private final Comparator<LookupElement> myComparator;
  private volatile boolean myCalculating;
  private final JLabel myAdComponent;
  private volatile String myAdText;
  private volatile int myLookupWidth = 50;
  private static final int LOOKUP_HEIGHT = Integer.getInteger("idea.lookup.height", 11).intValue();

  public LookupImpl(Project project, Editor editor, LookupElement[] items, @Nullable LookupItemPreferencePolicy itemPreferencePolicy){
    super(new JPanel(new BorderLayout()));
    myProject = project;
    myEditor = editor;
    myItemPreferencePolicy = itemPreferencePolicy;
    setInitialOffset(myEditor.getCaretModel().getOffset(), myEditor.getSelectionModel().getSelectionStart(), myEditor.getSelectionModel().getSelectionEnd());

    final Document document = myEditor.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    myElement = psiFile == null ? null : psiFile.findElementAt(myEditor.getCaretModel().getOffset());

    final PsiProximityComparator proximityComparator = new PsiProximityComparator(myElement == null ? psiFile : myElement);
    myComparator = new Comparator<LookupElement>() {
      public int compare(LookupElement o1, LookupElement o2) {
        LookupElement c1 = getCoreElement(o1);
        LookupElement c2 = getCoreElement(o2);

        if (c1 instanceof LookupItem && c2 instanceof LookupItem) {
          double priority1 = ((LookupItem)c1).getPriority();
          double priority2 = ((LookupItem)c2).getPriority();
          if (priority1 > priority2) return -1;
          if (priority2 > priority1) return 1;
        }

        int grouping1 = c1.getGrouping();
        int grouping2 = c2.getGrouping();
        if (grouping1 > grouping2) return -1;
        if (grouping2 > grouping1) return 1;

        int stringCompare = o1.getLookupString().compareToIgnoreCase(o2.getLookupString());
        if (stringCompare != 0) return stringCompare;

        return proximityComparator.compare(o1.getObject(), o2.getObject());
      }
    };
    myItems = new SortedList<LookupElement>(myComparator);
    myItemsMap = new TreeMap<LookupItemWeightComparable, SortedList<LookupElement>>();

    myProcessIcon = new AsyncProcessIcon("Completion progress");
    myProcessIcon.setVisible(false);
    myList = new JList(new DefaultListModel());
    myCellRenderer = new LookupCellRenderer(this);
    myList.setCellRenderer(myCellRenderer);

    for (final LookupElement item : items) {
      addItem(item);
    }

    myList.setFocusable(false);

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setBackground(LookupCellRenderer.BACKGROUND_COLOR);

    JScrollPane scrollPane = new JScrollPane(myList);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    getComponent().add(scrollPane, BorderLayout.NORTH);
    scrollPane.setBorder(null);

    JPanel bottomPanel = new JPanel(new BorderLayout());

    bottomPanel.add(myProcessIcon, BorderLayout.EAST);
    myAdComponent = HintUtil.createAdComponent(null);
    bottomPanel.add(myAdComponent, BorderLayout.CENTER);
    getComponent().add(bottomPanel, BorderLayout.SOUTH);
    getComponent().setBorder(new BegPopupMenuBorder());

    updateList();
    selectMostPreferableItem();
  }

  private LookupElement getCoreElement(LookupElement element) {
    while (element instanceof LookupElementDecorator) {
      element = ((LookupElementDecorator) element).getDelegate();
    }
    return element;
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

  public void addItem(LookupElement item) {
    final double priority = item instanceof LookupItem ? ((LookupItem)item).getPriority() : 0;
    final Comparable[] weight = getWeight(myItemPreferencePolicy, myElement, item);
    final LookupItemWeightComparable comparable = new LookupItemWeightComparable(priority, weight);

    final CollectConsumer<LookupElementAction> consumer = new CollectConsumer<LookupElementAction>();
    for (LookupActionProvider provider : LookupActionProvider.EP_NAME.getExtensions()) {
      provider.fillActions(item, this, consumer);
    }
    myItemActions.put(item, consumer.getResult());

    synchronized (myItems) {
      myItems.add(item);

      SortedList<LookupElement> list = myItemsMap.get(comparable);
      if (list == null) {
        myItemsMap.put(comparable, list = new SortedList<LookupElement>(myComparator));
      }
      list.add(item);
    }
    int maxWidth = myCellRenderer.updateMaximumWidth(item);
    myLookupWidth = Math.max(maxWidth, myLookupWidth);
  }

  public Collection<LookupElementAction> getActionsFor(LookupElement element) {
    final Collection<LookupElementAction> collection = myItemActions.get(element);
    return collection == null ? Collections.<LookupElementAction>emptyList() : collection;
  }

  @Nullable
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

  public LookupElement[] getItems(){
    synchronized (myItems) {
      return myItems.toArray(new LookupElement[myItems.size()]);
    }
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
    updateList();
  }

  private void updateList() {
    synchronized (myItems) {
      int minPrefixLength = myItems.isEmpty() ? 0 : Integer.MAX_VALUE;
      for (final LookupElement item : myItems) {
        minPrefixLength = Math.min(item.getPrefixMatcher().getPrefix().length(), minPrefixLength);
      }
      if (myMinPrefixLength != minPrefixLength) {
        myLookupStartMarker = null;
        myOldLookupStartOffset = -1;
      }
      myMinPrefixLength = minPrefixLength;

      Object oldSelected = !myDirty ? null : myList.getSelectedValue();
      DefaultListModel model = (DefaultListModel)myList.getModel();
      model.clear();

      Set<LookupElement> firstItems = new THashSet<LookupElement>();

      addExactPrefixItems(model, firstItems);

      boolean hasExactPrefixes = !firstItems.isEmpty();

      addMostRelevantItems(model, firstItems);

      final boolean hasPreselectedItem = addPreselectedItem(model, firstItems);

      myPreferredItemsCount = firstItems.size();

      addRemainingItemsLexicographically(model, firstItems);

      boolean isEmpty = model.getSize() == 0;
      if (isEmpty) {
        addEmptyItem(model);
      } else {
        myList.setFixedCellWidth(myLookupWidth);
      }
      myList.setFixedCellHeight(myCellRenderer.getListCellRendererComponent(myList, myList.getModel().getElementAt(0), 0, false, false).getPreferredSize().height);

      myList.setVisibleRowCount(Math.min(myList.getModel().getSize(), LOOKUP_HEIGHT));

      myAdComponent.setText(myAdText);

      if (!isEmpty) {
        if (oldSelected != null) {
          if (hasExactPrefixes || !ListScrollingUtil.selectItem(myList, oldSelected)) {
            selectMostPreferableItem();
          }
        }
        else {
          if (myPreselectedItem == EMPTY_LOOKUP_ITEM) {
            selectMostPreferableItem();
            myPreselectedItem = getCurrentItem();
          }
          else if (hasPreselectedItem && !hasExactPrefixes) {
            ListScrollingUtil.selectItem(myList, myPreselectedItem);
          }
          else {
            selectMostPreferableItem();
          }
        }
      }
    }
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

  private void addRemainingItemsLexicographically(DefaultListModel model, Set<LookupElement> firstItems) {
    for (LookupElement item : myItems) {
      if (!firstItems.contains(item) && prefixMatches(item)) {
        model.addElement(item);
      }
    }
  }

  private boolean addPreselectedItem(DefaultListModel model, Set<LookupElement> firstItems) {
    final boolean hasPreselectedItem = !myDirty && myPreselectedItem != EMPTY_LOOKUP_ITEM;
    if (hasPreselectedItem && !firstItems.contains(myPreselectedItem)) {
      firstItems.add(myPreselectedItem);
      model.addElement(myPreselectedItem);
    }
    return hasPreselectedItem;
  }

  private void addMostRelevantItems(DefaultListModel model, Set<LookupElement> firstItems) {
    for (final LookupItemWeightComparable comparable : myItemsMap.keySet()) {
      final List<LookupElement> suitable = new SmartList<LookupElement>();
      for (final LookupElement item : myItemsMap.get(comparable)) {
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

  private void addExactPrefixItems(DefaultListModel model, Set<LookupElement> firstItems) {
    for (final LookupItemWeightComparable comparable : myItemsMap.keySet()) {
      for (final LookupElement item : myItemsMap.get(comparable)) {
        if (isExactPrefixItem(item)) {
          model.addElement(item);
          firstItems.add(item);
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
    if (lookupStart < 0) {
      LOG.assertTrue(false, lookupStart + "; minprefix=" + myMinPrefixLength + "; offset=" + myEditor.getCaretModel().getOffset() + "; element=" + getPsiElement());
    }

    LogicalPosition pos = myEditor.offsetToLogicalPosition(lookupStart);
    Point location = myEditor.logicalPositionToXY(pos);
    location.y += myEditor.getLineHeight();
    JComponent editorComponent = myEditor.getComponent();
    JComponent internalComponent = myEditor.getContentComponent();
    final JRootPane rootPane = editorComponent.getRootPane();
    if (rootPane == null) {
      synchronized (myItems) {
        LOG.assertTrue(false, myItems);
      }
    }
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
    return myLookupStartMarker != null ? myLookupStartMarker.getStartOffset() : calcLookupStart();
  }

  public void show(){
    assert !myDisposed;

    int lookupStart = calcLookupStart();
    myOldLookupStartOffset = lookupStart;
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

        if (myOldLookupStartOffset != getLookupStart()) {
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

  private void selectMostPreferableItem(){
    final int index = doSelectMostPreferableItem(((DefaultListModel)myList.getModel()).toArray());
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
    Point layeredPanePoint=SwingUtilities.convertPoint(myList,itmBounds.x,itmBounds.y,getComponent());
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
      for (Iterator<LookupElement> it = myItems.iterator(); it.hasNext();) {
        LookupElement item = it.next();
        if (!item.setPrefixMatcher(item.getPrefixMatcher().cloneWithPrefix(newPrefix))) {
          it.remove();
        }
      }
      myAdditionalPrefix = "";
    }
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
    return myItemPreferencePolicy instanceof CompletionPreferencePolicy;
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
      new WriteCommandAction(myProject) {
        protected void run(Result result) throws Throwable {
          myEditor.getDocument()
              .replaceString(getLookupStart(), myEditor.getCaretModel().getOffset(), myInitialPrefix);
        }
      }.execute();
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

  private int doSelectMostPreferableItem(Object[] items) {
    if (myItemPreferencePolicy == null){
      return -1;
    }

    int prefItemIndex = -1;

    for(int i = 0; i < items.length; i++){
      LookupElement item = (LookupElement)items[i];
      final Object obj = item.getObject();
      if (obj instanceof PsiElement && !((PsiElement)obj).isValid()) continue;

      if (prefItemIndex == -1) {
        prefItemIndex = i;
      }
      else {
        int d = myItemPreferencePolicy.compare(item, (LookupElement)items[prefItemIndex]);
        if (d < 0) {
          prefItemIndex = i;
        }
      }
      if (isExactPrefixItem(item)) {
        break;
      }
    }
    return prefItemIndex;
  }

  private boolean isExactPrefixItem(LookupElement item) {
    return item.getAllLookupStrings().contains(item.getPrefixMatcher().getPrefix() + myAdditionalPrefix);
  }

  public void refreshUi() {
    updateList();

    if (isVisible() && !ApplicationManager.getApplication().isUnitTestMode()) {
      if (myEditor.getComponent().getRootPane() == null) {
        LOG.assertTrue(false, "Null root pane");
      }

      Point point = calculatePosition();
      Dimension preferredSize = getComponent().getPreferredSize();
      setBounds(point.x,point.y,preferredSize.width,preferredSize.height);

      HintManagerImpl.getInstanceImpl().adjustEditorHintPosition(this, myEditor, getComponent().getLocation());
    }
  }

  public LookupElement[] getSortedItems() {
    synchronized (myItems) {
      final LookupElement[] result = new LookupElement[myList.getModel().getSize()];
      ((DefaultListModel)myList.getModel()).copyInto(result);
      return result;
    }
  }

}
