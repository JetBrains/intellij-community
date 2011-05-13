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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CompletionLookupArranger;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.ui.UISettings;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.plaf.beg.BegPopupMenuBorder;
import com.intellij.util.Alarm;
import com.intellij.util.CollectConsumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import gnu.trove.TObjectHashingStrategy;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class LookupImpl extends LightweightHint implements LookupEx, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.lookup.impl.LookupImpl");
  private static final int MAX_PREFERRED_COUNT = 5;

  private static final LookupItem EMPTY_LOOKUP_ITEM = LookupItem.fromString("preselect");
  private static final int LOOKUP_HEIGHT = Integer.getInteger("idea.lookup.height", 11).intValue();
  private static final Icon relevanceSortIcon = IconLoader.getIcon("/ide/lookupRelevance.png");
  private static final Icon lexiSortIcon = IconLoader.getIcon("/ide/lookupAlphanumeric.png");

  private final Project myProject;
  private final Editor myEditor;

  private int myPreferredItemsCount;
  private String myInitialPrefix;
  private LookupArranger myCustomArranger;

  private boolean myStableStart;
  @NotNull private RangeMarker myLookupStartMarker;
  private final JList myList = new JBList(new DefaultListModel());
  private final LookupCellRenderer myCellRenderer;
  private Boolean myPositionedAbove = null;

  private final ArrayList<LookupListener> myListeners = new ArrayList<LookupListener>();

  private long myStampShown = 0;
  private boolean myShown = false;
  private boolean myDisposed = false;
  private boolean myHidden = false;
  private LookupElement myPreselectedItem = EMPTY_LOOKUP_ITEM;
  private final List<LookupElement> myFrozenItems = new ArrayList<LookupElement>();
  private String mySelectionInvariant = null;
  private boolean mySelectionTouched;
  private boolean myFocused = true;
  private String myAdditionalPrefix = "";
  private final AsyncProcessIcon myProcessIcon = new AsyncProcessIcon("Completion progress");
  private final JPanel myIconPanel = new JPanel(new BorderLayout());
  private volatile boolean myCalculating;
  private final Advertiser myAdComponent;
  private volatile String myAdText;
  private volatile int myLookupTextWidth = 50;
  private boolean myReused;
  private boolean myChangeGuard;
  private LookupModel myModel = new LookupModel();
  private final Map<LookupElement, PrefixMatcher> myMatchers = new ConcurrentHashMap<LookupElement, PrefixMatcher>(TObjectHashingStrategy.IDENTITY);
  private LookupHint myElementHint = null;
  private Alarm myHintAlarm = new Alarm();
  private JLabel mySortingLabel;
  private final JScrollPane myScrollPane;
  private JButton myScrollBarIncreaseButton;
  private boolean myStartCompletionWhenNothingMatches;

  public LookupImpl(Project project, Editor editor, @NotNull LookupArranger arranger){
    super(new JPanel(new BorderLayout()));
    setForceShowAsPopup(true);
    setCancelOnClickOutside(false);
    myProject = project;
    myEditor = editor;

    myIconPanel.setVisible(false);
    myCellRenderer = new LookupCellRenderer(this);
    myList.setCellRenderer(myCellRenderer);

    myList.setFocusable(false);

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setBackground(LookupCellRenderer.BACKGROUND_COLOR);

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

    getComponent().add(myScrollPane, BorderLayout.NORTH);
    myScrollPane.setBorder(null);

    myAdComponent = new Advertiser();
    JComponent adComponent = myAdComponent.getAdComponent();
    adComponent.setBorder(new EmptyBorder(0, 1, 1, 2 + relevanceSortIcon.getIconWidth()));
    getComponent().add(adComponent, BorderLayout.SOUTH);
    getComponent().setBorder(new BegPopupMenuBorder());

    myIconPanel.setBackground(Color.LIGHT_GRAY);
    myIconPanel.add(myProcessIcon);

    updateLookupStart(0);

    final ListModel model = myList.getModel();
    addEmptyItem((DefaultListModel)model);
    updateListHeight(model);

    setArranger(arranger);

    addListeners();

    mySortingLabel = new JLabel();
    mySortingLabel.setBorder(new LineBorder(Color.LIGHT_GRAY));
    mySortingLabel.setOpaque(true);
    mySortingLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = !UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY;
        updateSorting();
      }
    });
    updateSorting();
  }

  private void updateSorting() {
    final boolean lexi = UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY;
    mySortingLabel.setIcon(lexi ? lexiSortIcon : relevanceSortIcon);
    mySortingLabel.setToolTipText(lexi ? "Click to sort variants by relevance" : "Click to sort variants alphabetically");
    myModel.setArranger(getActualArranger());

    resort();
  }

  public void setArranger(LookupArranger arranger) {
    myCustomArranger = arranger;
    myModel.setArranger(getActualArranger());
  }

  @Override
  public boolean isFocused() {
    return myFocused;
  }

  public void setFocused(boolean focused) {
    myFocused = focused;
  }

  public boolean isCalculating() {
    return myCalculating;
  }

  public void setCalculating(final boolean calculating) {
    myCalculating = calculating;
    myIconPanel.setVisible(calculating);
    if (calculating) {
      myProcessIcon.resume();
    } else {
      myProcessIcon.suspend();
    }
  }

  public int getPreferredItemsCount() {
    return myPreferredItemsCount;
  }

  public void markSelectionTouched() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    mySelectionTouched = true;
    myPreselectedItem = null;
  }

  @TestOnly
  public void setSelectionTouched(boolean selectionTouched) {
    mySelectionTouched = selectionTouched;
  }

  public void resort() {
    myFrozenItems.clear();
    myPreselectedItem = EMPTY_LOOKUP_ITEM;
    synchronized (myList) {
      ((DefaultListModel)myList.getModel()).clear();
    }

    final List<LookupElement> items = myModel.getItems();
    myModel.clearItems();
    for (final LookupElement item : items) {
      addItem(item, itemMatcher(item));
    }
    checkReused();
    updateList();
    ensureSelectionVisible();
  }

  public void addItem(LookupElement item, PrefixMatcher matcher) {
    myMatchers.put(item, matcher);
    myModel.addItem(item);

    updateLookupWidth(item);
    updateItemActions(item);
  }

  public void updateLookupWidth(LookupElement item) {
    final LookupElementPresentation presentation = renderItemApproximately(item);
    int maxWidth = myCellRenderer.updateMaximumWidth(presentation);
    myLookupTextWidth = Math.max(maxWidth, myLookupTextWidth);

    myModel.setItemPresentation(item, presentation);

  }

  public void updateItemActions(LookupElement item) {
    final CollectConsumer<LookupElementAction> consumer = new CollectConsumer<LookupElementAction>();
    for (LookupActionProvider provider : LookupActionProvider.EP_NAME.getExtensions()) {
      provider.fillActions(item, this, consumer);
    }
    myModel.setItemActions(item, consumer.getResult());
  }

  public Collection<LookupElementAction> getActionsFor(LookupElement element) {
    return myModel.getActionsFor(element);
  }

  public JList getList() {
    return myList;
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
    if (StringUtil.isNotEmpty(text)) {
      addAdvertisement(text);
    }
  }

  public String getAdvertisementText() {
    return myAdText;
  }


  public String getAdditionalPrefix() {
    return myAdditionalPrefix;
  }

  void appendPrefix(char c) {
    checkReused();
    checkValid();
    myAdditionalPrefix += c;
    myInitialPrefix = null;
    myFrozenItems.clear();
    refreshUi();
    ensureSelectionVisible();
    if (myStartCompletionWhenNothingMatches && myList.getModel().getSize() == 1 && myList.getModel().getElementAt(0) instanceof EmptyLookupItem) {
      CompletionAutoPopupHandler.scheduleAutoPopup(myProject, myEditor, getPsiFile());
    }
  }

  public void setStartCompletionWhenNothingMatches(boolean startCompletionWhenNothingMatches) {
    myStartCompletionWhenNothingMatches = startCompletionWhenNothingMatches;
  }

  private void ensureSelectionVisible() {
    ListScrollingUtil.ensureIndexIsVisible(myList, myList.getSelectedIndex(), 1);
  }

  boolean truncatePrefix(boolean preserveSelection) {
    final int len = myAdditionalPrefix.length();
    if (len == 0) return false;

    if (preserveSelection) {
      markSelectionTouched();
    }

    myAdditionalPrefix = myAdditionalPrefix.substring(0, len - 1);
    myInitialPrefix = null;
    myFrozenItems.clear();
    if (!myReused) {
      refreshUi();
      ensureSelectionVisible();
    }

    return true;
  }

  private void updateList() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    checkValid();

    final Pair<List<LookupElement>,Iterable<List<LookupElement>>> snapshot = myModel.getModelSnapshot();

    final List<LookupElement> items = matchingItems(snapshot);

    checkMinPrefixLengthChanges(items);

    boolean hasPreselected = !mySelectionTouched && items.contains(myPreselectedItem);
    LookupElement oldSelected = mySelectionTouched ? (LookupElement)myList.getSelectedValue() : null;
    String oldInvariant = mySelectionInvariant;

    LinkedHashSet<LookupElement> model = new LinkedHashSet<LookupElement>();
    model.addAll(getPrefixItems(items));
    model.addAll(myFrozenItems);
    addMostRelevantItems(model, snapshot.second);
    if (hasPreselected) {
      model.add(myPreselectedItem);
    }

    myPreferredItemsCount = model.size();
    myFrozenItems.clear();
    if (myShown) {
      myFrozenItems.addAll(model);
    }

    model.addAll(addRemainingItemsLexicographically(model, items));

    DefaultListModel listModel = (DefaultListModel)myList.getModel();
    synchronized (myList) {
      listModel.clear();

      if (!model.isEmpty()) {
        for (LookupElement element : model) {
          listModel.addElement(element);
        }
      }
      else {
        addEmptyItem(listModel);
      }
    }

    updateListHeight(listModel);

    if (!model.isEmpty()) {
      myList.setFixedCellWidth(Math.max(myLookupTextWidth + myCellRenderer.getIconIndent(), myAdComponent.getAdComponent().getPreferredSize().width));

      if (isFocused() && (!isExactPrefixItem(model.iterator().next()) || mySelectionTouched)) {
        restoreSelection(oldSelected, hasPreselected, oldInvariant);
      }
      else {
        myList.setSelectedIndex(0);
      }
    }
  }

  private List<LookupElement> matchingItems(Pair<List<LookupElement>, Iterable<List<LookupElement>>> snapshot) {
    final List<LookupElement> items = new ArrayList<LookupElement>();
    for (LookupElement element : snapshot.first) {
      if (prefixMatches(element)) {
        items.add(element);
      }
    }
    return items;
  }

  private boolean checkReused() {
    if (myReused) {
      myAdditionalPrefix = "";
      myFrozenItems.clear();
      myModel.collectGarbage();
      myReused = false;
      return true;
    }
    return false;
  }

  private void checkMinPrefixLengthChanges(Collection<LookupElement> items) {
    if (myStableStart) return;
    if (!myCalculating && !items.isEmpty()) {
      myStableStart = true;
    }

    int minPrefixLength = items.isEmpty() ? 0 : Integer.MAX_VALUE;
    for (final LookupElement item : items) {
      minPrefixLength = Math.min(itemMatcher(item).getPrefix().length(), minPrefixLength);
    }

    updateLookupStart(minPrefixLength);
  }

  private void restoreSelection(@Nullable LookupElement oldSelected, boolean choosePreselectedItem, @Nullable String oldInvariant) {
    if (oldSelected != null) {
      if (oldSelected.isValid()) {
        myList.setSelectedValue(oldSelected, false);
        if (myList.getSelectedValue() == oldSelected) {
          return;
        }
      }

      if (oldInvariant != null) {
        for (LookupElement element : getItems()) {
          if (oldInvariant.equals(myModel.getItemPresentationInvariant(element))) {
            myList.setSelectedValue(element, false);
            if (myList.getSelectedValue() == element) {
              return;
            }
          }
        }
      }
    }

    if (choosePreselectedItem) {
      myList.setSelectedValue(myPreselectedItem, false);
    } else {
      myList.setSelectedIndex(doSelectMostPreferableItem(getItems()));
    }

    if (myPreselectedItem != null && myShown) {
      myPreselectedItem = getCurrentItem();
    }
  }

  private void updateListHeight(ListModel model) {
    myList.setFixedCellHeight(myCellRenderer.getListCellRendererComponent(myList, model.getElementAt(0), 0, false, false).getPreferredSize().height);

    myList.setVisibleRowCount(Math.min(model.getSize(), LOOKUP_HEIGHT));
  }

  private void addEmptyItem(DefaultListModel model) {
    LookupItem<String> item = new EmptyLookupItem(myCalculating ? " " : LangBundle.message("completion.no.suggestions"));
    myMatchers.put(item, new CamelHumpMatcher(""));
    if (!myCalculating) {
      myList.setFixedCellWidth(Math.max(myCellRenderer.updateMaximumWidth(renderItemApproximately(item)), myLookupTextWidth));
    }

    model.addElement(item);
  }

  private static LookupElementPresentation renderItemApproximately(LookupElement item) {
    final LookupElementPresentation p = new LookupElementPresentation();
    item.renderElement(p);
    return p;
  }

  private static List<LookupElement> addRemainingItemsLexicographically(Set<LookupElement> firstItems, Collection<LookupElement> allItems) {
    List<LookupElement> model = new ArrayList<LookupElement>();
    for (LookupElement item : allItems) {
      if (!firstItems.contains(item)) {
        model.add(item);
      }
    }
    return model;
  }

  private void addMostRelevantItems(final Set<LookupElement> model, final Iterable<List<LookupElement>> sortedItems) {
    if (model.size() > MAX_PREFERRED_COUNT) return;

    for (final List<LookupElement> elements : sortedItems) {
      final List<LookupElement> suitable = new SmartList<LookupElement>();
      for (final LookupElement item : elements) {
        if (!model.contains(item) && prefixMatches(item)) {
          suitable.add(item);
        }
      }

      if (model.size() + suitable.size() > MAX_PREFERRED_COUNT) break;
      model.addAll(suitable);
    }
  }

  public boolean isFrozen(@NotNull LookupElement element) {
    return myFrozenItems.contains(element);
  }

  private List<LookupElement> getPrefixItems(final Collection<LookupElement> elements) {
    List<LookupElement> better = new ArrayList<LookupElement>();
    for (LookupElement element : elements) {
      if (isExactPrefixItem(element)) {
        better.add(element);
      }
    }

    final LookupArranger arranger = getActualArranger();
    final Comparator<LookupElement> itemComparator = arranger.getItemComparator();
    if (itemComparator != null) {
      Collections.sort(better, itemComparator);
    }

    final Classifier<LookupElement> classifier = arranger.createRelevanceClassifier();
    for (LookupElement element : better) {
      classifier.addElement(element);
    }
    return ContainerUtil.flatten(classifier.classify(better));
  }

  @NotNull
  @Override
  public String itemPattern(LookupElement element) {
    return itemMatcher(element).getPrefix() + myAdditionalPrefix;
  }

  private LookupArranger getActualArranger() {
    if (isCompletion() && UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY) {
      return LookupArranger.LEXICOGRAPHIC;
    }
    return myCustomArranger;
  }

  private boolean isExactPrefixItem(LookupElement item) {
    return item.getAllLookupStrings().contains(itemPattern(item));
  }

  private boolean prefixMatches(final LookupElement item) {
    if (!item.isValid()) return false;

    PrefixMatcher matcher = itemMatcher(item);
    if (myAdditionalPrefix.length() > 0) {
      matcher = matcher.cloneWithPrefix(itemPattern(item));
    }
    return matcher.prefixMatches(item);
  }

  @Override
  @NotNull
  public PrefixMatcher itemMatcher(LookupElement item) {
    PrefixMatcher matcher = myMatchers.get(item);
    if (matcher == null) {
      throw new AssertionError("Item not in lookup: item=" + item + "; lookup items=" + getItems());
    }
    return matcher;
  }

  /**
   * @return point in layered pane coordinate system.
   * @param component
   */
  public Point calculatePosition(final JComponent component) {
    Dimension dim = component.getPreferredSize();
    int lookupStart = getLookupStart();
    if (lookupStart < 0 || lookupStart > myEditor.getDocument().getTextLength()) {
      LOG.error(lookupStart + "; offset=" + myEditor.getCaretModel().getOffset() + "; element=" +
                getPsiElement());
    }

    LogicalPosition pos = myEditor.offsetToLogicalPosition(lookupStart);
    Point location = myEditor.logicalPositionToXY(pos);
    location.y += myEditor.getLineHeight();
    JComponent editorComponent = myEditor.getComponent();
    JComponent internalComponent = myEditor.getContentComponent();
    final JRootPane rootPane = editorComponent.getRootPane();
    if (rootPane == null) {
      LOG.error(myEditor.isDisposed() + "; shown=" + myShown + "; disposed=" + myDisposed + "; editorShowing=" + myEditor.getContentComponent().isShowing());
    }
    JLayeredPane layeredPane = rootPane.getLayeredPane();
    Point layeredPanePoint=SwingUtilities.convertPoint(internalComponent,location, layeredPane);
    Point originalPoint = new Point(layeredPanePoint);
    SwingUtilities.convertPointToScreen(originalPoint, layeredPane);
    layeredPanePoint.x -= myCellRenderer.getIconIndent();
    layeredPanePoint.x -= component.getInsets().left;

    int shiftLow = layeredPane.getHeight() - (layeredPanePoint.y + dim.height);
    int shiftHigh = layeredPanePoint.y - dim.height;
    if (!isPositionedAboveCaret()) {
      myPositionedAbove = shiftLow < 0 && shiftLow < shiftHigh;
    }
    if (isPositionedAboveCaret()) {
      layeredPanePoint.y -= dim.height + myEditor.getLineHeight();
      if (pos.line == 0) {
        layeredPanePoint.y += 1;
        //otherwise the lookup won't intersect with the editor and every editor's resize (e.g. after typing in console) will close the lookup
      }
    }

    final Point painPoint = new Point(layeredPanePoint);
    SwingUtilities.convertPointToScreen(painPoint, layeredPane);
    final Rectangle originScreenRect = ScreenUtil.getScreenRectangle(originalPoint);
    if (! originScreenRect.contains(painPoint)) {
      final Point p = ScreenUtil.findNearestPointOnBorder(originScreenRect, painPoint);
      SwingUtilities.convertPointFromScreen(p, layeredPane);
      return p;
    }

    return layeredPanePoint;
  }

  public void finishLookup(final char completionChar) {
    finishLookup(completionChar, (LookupElement)myList.getSelectedValue());
  }

  public void finishLookup(char completionChar, @Nullable final LookupElement item) {
    doHide(false, true);
    if (item == null ||
        item instanceof EmptyLookupItem ||
        item.getObject() instanceof DeferredUserLookupValue &&
        item.as(LookupItem.CLASS_CONDITION_KEY) != null &&
        !((DeferredUserLookupValue)item.getObject()).handleUserSelection(item.as(LookupItem.CLASS_CONDITION_KEY), myProject)) {
      fireItemSelected(null, completionChar);
      return;
    }

    final PsiFile file = getPsiFile();
    if (file != null && !WriteCommandAction.ensureFilesWritable(myProject, Arrays.asList(file))) {
      fireItemSelected(null, completionChar);
      return;
    }

    final String prefix = itemPattern(item);
    boolean plainMatch = ContainerUtil.or(item.getAllLookupStrings(), new Condition<String>() {
      @Override
      public boolean value(String s) {
        return StringUtil.startsWithIgnoreCase(s, prefix);
      }
    });
    if (!plainMatch) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CAMEL_HUMPS);
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        insertLookupString(item, prefix);
      }
    });

    fireItemSelected(item, completionChar);
  }

  private void insertLookupString(LookupElement item, final String prefix) {
    PrefixMatcher matcher = itemMatcher(item);

    String lookupString = item.getLookupString();
    if (!item.isCaseSensitive() && matcher.prefixMatches(item) && StringUtil.startsWithIgnoreCase(lookupString, prefix)) {
      lookupString = handleCaseInsensitiveVariant(prefix, lookupString);
    }

    EditorModificationUtil.deleteSelectedText(myEditor);
    final int caretOffset = myEditor.getCaretModel().getOffset();
    int lookupStart = caretOffset - prefix.length();

    int len = myEditor.getDocument().getTextLength();
    LOG.assertTrue(lookupStart >= 0 && lookupStart <= len, "ls: " + lookupStart + "caret: " + caretOffset + " prefix:" + prefix + " doc: " + len);
    LOG.assertTrue(caretOffset >= 0 && caretOffset <= len, "co: " + caretOffset + " doc: " + len);

    myEditor.getDocument().replaceString(lookupStart, caretOffset, lookupString);

    int offset = lookupStart + lookupString.length();
    myEditor.getCaretModel().moveToOffset(offset);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();
  }

  private static String handleCaseInsensitiveVariant(final String prefix, @NotNull final String lookupString) {
    final int length = prefix.length();
    if (length == 0) return lookupString;
    boolean isAllLower = true;
    boolean isAllUpper = true;
    boolean sameCase = true;
    for (int i = 0; i < length && (isAllLower || isAllUpper || sameCase); i++) {
      final char c = prefix.charAt(i);
      isAllLower = isAllLower && Character.isLowerCase(c);
      isAllUpper = isAllUpper && Character.isUpperCase(c);
      sameCase = sameCase && Character.isLowerCase(c) == Character.isLowerCase(lookupString.charAt(i));
    }
    if (sameCase) return lookupString;
    if (isAllLower) return lookupString.toLowerCase();
    if (isAllUpper) return lookupString.toUpperCase();
    return lookupString;
  }

  public int getLookupStart() {
    LOG.assertTrue(myLookupStartMarker.isValid());
    return myLookupStartMarker.getStartOffset();
  }

  public void performGuardedChange(Runnable change) {
    checkValid();
    assert myLookupStartMarker.isValid();
    assert !myChangeGuard;

    myChangeGuard = true;
    RangeMarkerEx marker = (RangeMarkerEx) myEditor.getDocument().createRangeMarker(myLookupStartMarker.getStartOffset(), myLookupStartMarker.getEndOffset());
    marker.trackInvalidation(true);
    try {
      change.run();
    }
    finally {
      marker.trackInvalidation(false);
      myChangeGuard = false;
    }
    checkValid();
    LOG.assertTrue(myLookupStartMarker.isValid(), "invalid lookup start");
    LOG.assertTrue(marker.isValid(), "invalid marker");
    if (isVisible()) {
      updateLookupBounds();
    }
    checkValid();
  }

  @Override
  public boolean vetoesHiding() {
    return myChangeGuard;
  }

  public boolean isShown() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myShown;
  }

  public void show(){
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkValid();
    LOG.assertTrue(!myShown);
    myShown = true;
    myStampShown = System.currentTimeMillis();

    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    myAdComponent.showRandomText();

    getComponent().setBorder(null);
    updateScrollbarVisibility();

    Point p = calculatePosition(getComponent());
    HintManagerImpl.getInstanceImpl().showEditorHint(this, myEditor, p, HintManagerImpl.HIDE_BY_ESCAPE | HintManagerImpl.UPDATE_BY_SCROLLING, 0, false,
                                                     HintManagerImpl.createHintHint(myEditor, p, this, HintManager.UNDER).setAwtTooltip(false));
    LOG.assertTrue(isVisible(), "!visible, disposed=" + myDisposed);
    LOG.assertTrue(myList.isShowing(), "!showing, disposed=" + myDisposed);

    final JLayeredPane layeredPane = getComponent().getRootPane().getLayeredPane();
    layeredPane.add(myIconPanel, 42, 0);
    layeredPane.add(mySortingLabel, 10, 0);

    layoutStatusIcons();
  }

  public boolean mayBeNoticed() {
    return myStampShown > 0 && System.currentTimeMillis() - myStampShown > 300;
  }

  private void addListeners() {
    myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        if (!myChangeGuard) {
          hide();
        }
      }
    }, this);

    final CaretListener caretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e){
        if (!myChangeGuard) {
          hide();
        }
      }
    };
    final SelectionListener selectionListener = new SelectionListener() {
      public void selectionChanged(final SelectionEvent e) {
        if (!myChangeGuard) {
          hide();
        }
      }
    };
    final EditorMouseListener mouseListener = new EditorMouseAdapter() {
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

    myList.addListSelectionListener(new ListSelectionListener() {
      private LookupElement oldItem = null;

      public void valueChanged(ListSelectionEvent e){
        myHintAlarm.cancelAllRequests();

        final LookupElement item = getCurrentItem();
        if (oldItem != item) {
          mySelectionInvariant = item == null ? null : myModel.getItemPresentationInvariant(item);
          fireCurrentItemChanged(item);
        }
        if (item != null) {
          updateHint(item);
        }
        oldItem = item;
      }
    });

    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e){
        setFocused(true);
        markSelectionTouched();

        if (e.getClickCount() == 2){
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              finishLookup(NORMAL_SELECT_CHAR);
            }
          }, "", null);
        }
      }
    });
  }

  private void updateHint(@NotNull final LookupElement item) {
    if (myElementHint != null) {
      final JRootPane rootPane = getComponent().getRootPane();
      if (rootPane != null) {
        rootPane.getLayeredPane().remove(myElementHint);
        rootPane.revalidate();
        rootPane.repaint();
      }
      myElementHint = null;
    }
    if (!isFocused()) {
      return;
    }

    final Collection<LookupElementAction> actions = myModel.getActionsFor(item);
    if (!actions.isEmpty()) {
      myHintAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          assert !myDisposed;
          final JRootPane rootPane = getComponent().getRootPane();
          if (rootPane == null) return;

          myElementHint = new LookupHint();
          final JLayeredPane layeredPane = rootPane.getLayeredPane();
          layeredPane.add(myElementHint, 0, 0);
          final Rectangle bounds = getCurrentItemBounds();
          myElementHint.setSize(myElementHint.getPreferredSize());
          myElementHint.setLocation(new Point(bounds.x + bounds.width - myElementHint.getWidth(), bounds.y));
        }
      }, 500);
    }
  }

  private int updateLookupStart(int myMinPrefixLength) {
    int offset = myEditor.getSelectionModel().hasSelection()
                 ? myEditor.getSelectionModel().getSelectionStart()
                 : myEditor.getCaretModel().getOffset();
    int start = Math.max(offset - myMinPrefixLength - myAdditionalPrefix.length(), 0);
    myLookupStartMarker = myEditor.getDocument().createRangeMarker(start, start);
    myLookupStartMarker.setGreedyToLeft(true);
    return start;
  }

  @Nullable
  public LookupElement getCurrentItem(){
    LookupElement item = (LookupElement)myList.getSelectedValue();
    return item instanceof EmptyLookupItem ? null : item;
  }

  public void setCurrentItem(LookupElement item){
    markSelectionTouched();
    myList.setSelectedValue(item, false);
  }

  public void addLookupListener(LookupListener listener){
    myListeners.add(listener);
  }

  public void removeLookupListener(LookupListener listener){
    myListeners.remove(listener);
  }

  public Rectangle getCurrentItemBounds(){
    int index = myList.getSelectedIndex();
    if (index < 0) {
      LOG.error("No selected element, size=" + myList.getModel().getSize() + "; items" + getItems());
    }
    Rectangle itmBounds = myList.getCellBounds(index, index);
    if (itmBounds == null){
      LOG.error("No bounds for " + index + "; size=" + myList.getModel().getSize());
      return null;
    }
    Point layeredPanePoint=SwingUtilities.convertPoint(myList,itmBounds.x,itmBounds.y,getComponent());
    itmBounds.x = layeredPanePoint.x;
    itmBounds.y = layeredPanePoint.y;
    return itmBounds;
  }

  public void fireItemSelected(@Nullable final LookupElement item, char completionChar){
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (item != null) {
      getActualArranger().itemSelected(item, this);
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

  private void fireLookupCanceled(final boolean explicitly) {
    if (!myListeners.isEmpty()){
      LookupEvent event = new LookupEvent(this, explicitly);
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
      LookupEvent event = new LookupEvent(this, item, (char)0);
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
    if (explicitlyInvoked) {
      setFocused(true);
    }

    if (explicitlyInvoked && myCalculating) return false;
    if (!explicitlyInvoked && mySelectionTouched) return false;

    ListModel listModel = myList.getModel();
    if (listModel.getSize() <= 1) return false;

    if (listModel.getSize() == 0) return false;

    final LookupElement firstItem = (LookupElement)listModel.getElementAt(0);
    if (listModel.getSize() == 1 && firstItem instanceof EmptyLookupItem) return false;

    final PrefixMatcher firstItemMatcher = itemMatcher(firstItem);
    final String oldPrefix = firstItemMatcher.getPrefix();
    final String presentPrefix = oldPrefix + myAdditionalPrefix;
    final PrefixMatcher matcher = firstItemMatcher.cloneWithPrefix(presentPrefix);
    String lookupString = firstItem.getLookupString();
    int div = divideString(lookupString, matcher);
    if (div < 0) return false;

    String beforeCaret = lookupString.substring(0, div);
    String afterCaret = lookupString.substring(div);


    for (int i = 1; i < listModel.getSize(); i++) {
      LookupElement item = (LookupElement)listModel.getElementAt(i);
      if (!oldPrefix.equals(itemMatcher(item).getPrefix())) return false;

      lookupString = item.getLookupString();
      div = divideString(lookupString, itemMatcher(item).cloneWithPrefix(presentPrefix));
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
    }
    else {
      myInitialPrefix = null;
    }

    final String finalBeforeCaret = beforeCaret;
    final String finalAfterCaret = afterCaret;
    Runnable runnable = new Runnable() {
      public void run() {
        doInsertCommonPrefix(presentPrefix, finalBeforeCaret, finalAfterCaret);
      }
    };
    performGuardedChange(runnable);
    return true;
  }

  private void doInsertCommonPrefix(String presentPrefix, String beforeCaret, String afterCaret) {
    EditorModificationUtil.deleteSelectedText(myEditor);
    int offset = myEditor.getCaretModel().getOffset();
    if (beforeCaret != null) { // correct case, expand camel-humps
      final int start = offset - presentPrefix.length();
      myAdditionalPrefix = "";
      myEditor.getDocument().replaceString(start, offset, beforeCaret);
      presentPrefix = beforeCaret;
    }

    offset = myEditor.getCaretModel().getOffset();
    myEditor.getDocument().insertString(offset, afterCaret);

    final String newPrefix = presentPrefix + afterCaret;

    Map<LookupElement, PrefixMatcher> newItems = myModel.retainMatchingItems(newPrefix, this);
    myMatchers.clear();
    myMatchers.putAll(newItems);

    myAdditionalPrefix = "";

    offset += afterCaret.length();
    myEditor.getCaretModel().moveToOffset(offset);
    refreshUi();
  }

  @Nullable
  public PsiFile getPsiFile() {
    return PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
  }

  public boolean isCompletion() {
    return myCustomArranger instanceof CompletionLookupArranger;
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

  @TestOnly
  public void setPositionedAbove(boolean positionedAbove) {
    myPositionedAbove = positionedAbove;
  }

  public boolean isPositionedAboveCaret(){
    return myPositionedAbove != null && myPositionedAbove.booleanValue();
  }

  public boolean isSelectionTouched() {
    return mySelectionTouched;
  }

  public void hide(){
    hideLookup(true);
  }

  public void hideLookup(boolean explicitly) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myDisposed) return;

    doHide(true, explicitly);
  }

  private void doHide(final boolean fireCanceled, final boolean explicitly) {
    if (myChangeGuard) {
      LOG.error("Disposing under a change guard");
    }

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
    if (myInitialPrefix != null) {
      myEditor.getDocument().replaceString(getLookupStart(), myEditor.getCaretModel().getOffset(), myInitialPrefix);
    }
  }

  String disposeTrace = null;

  public void dispose() {
    assert ApplicationManager.getApplication().isDispatchThread();
    assert myHidden;
    if (myDisposed) {
      LOG.error(disposeTrace);
      return;
    }

    Disposer.dispose(myProcessIcon);
    Disposer.dispose(myHintAlarm);

    myDisposed = true;
    disposeTrace = DebugUtil.currentStackTrace();
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

    final int index = getActualArranger().suggestPreselectedItem(items);
    assert index >= 0 && index < items.size();
    return index;
  }

  public void refreshUi() {
    final boolean reused = checkReused();

    updateList();

    if (isVisible()) {
      LOG.assertTrue(!ApplicationManager.getApplication().isUnitTestMode());

      if (myEditor.getComponent().getRootPane() == null) {
        LOG.error("Null root pane");
      }

      updateScrollbarVisibility();
      updateLookupBounds();

      if (reused) {
        ensureSelectionVisible();
      }
    }
  }

  private void updateLookupBounds() {
    HintManagerImpl.adjustEditorHintPosition(this, myEditor, calculatePosition(getComponent()));
    layoutStatusIcons();
  }

  private void layoutStatusIcons() {
    final JLayeredPane layeredPane = getComponent().getRootPane().getLayeredPane();

    final Dimension iconSize = myProcessIcon.getPreferredSize();
    myIconPanel.setBounds(layeredPane.getWidth() - iconSize.width, 0, iconSize.width, iconSize.height);

    final Dimension sortSize = mySortingLabel.getPreferredSize();
    final Point sbLocation = SwingUtilities.convertPoint(myScrollPane.getVerticalScrollBar(), 0, 0, layeredPane);

    int adHeight = myAdComponent.getAdComponent().getPreferredSize().height;
    final int sortHeight = Math.max(adHeight, mySortingLabel.getPreferredSize().height);
    mySortingLabel.setBounds(sbLocation.x, layeredPane.getHeight() - sortHeight, sortSize.width, sortHeight);

    Dimension buttonSize = adHeight > 0 ? new Dimension(0, 0) : new Dimension(relevanceSortIcon.getIconWidth(), relevanceSortIcon.getIconHeight());
    myScrollBarIncreaseButton.setPreferredSize(buttonSize);
    myScrollBarIncreaseButton.setMinimumSize(buttonSize);
    myScrollBarIncreaseButton.setMaximumSize(buttonSize);
    myScrollPane.getVerticalScrollBar().revalidate();
    myScrollPane.getVerticalScrollBar().repaint();
  }

  private void updateScrollbarVisibility() {
    boolean showSorting = isCompletion() && getList().getModel().getSize() >= 3;
    mySortingLabel.setVisible(showSorting);
    myScrollPane.setVerticalScrollBarPolicy(showSorting ? ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS : ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
  }

  @TestOnly
  public LookupArranger getArranger() {
    return getActualArranger();
  }

  public void markReused() {
    myReused = true;
    myModel.clearItems();
    myAdComponent.clearAdvertisements();
    myPreselectedItem = null;
  }

  public void addAdvertisement(@NotNull String text) {
    myAdComponent.addAdvertisement(text);
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

  private class LookupHint extends JLabel {
    private final Border INACTIVE_BORDER = BorderFactory.createEmptyBorder(4, 4, 4, 4);
    private final Border ACTIVE_BORDER = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 1), BorderFactory.createEmptyBorder(3, 3, 3, 3));
    private LookupHint() {
      setOpaque(false);
      setBorder(INACTIVE_BORDER);
      setIcon(IconLoader.findIcon("/actions/intentionBulb.png"));
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

  public LinkedHashMap<LookupElement,StringBuilder> getRelevanceStrings() {
    return myModel.getRelevanceStrings();
  }

}
