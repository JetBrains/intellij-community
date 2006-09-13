package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionBundle;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.util.containers.HashMap;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Matcher;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class LookupImpl extends LightweightHint implements Lookup {
  static final Object EMPTY_ITEM_ATTRIBUTE = Key.create("emptyItem");
  static final Object ALL_METHODS_ATTRIBUTE = Key.create("allMethods");

  public static final Key<LookupImpl> LOOKUP_IN_EDITOR_KEY = Key.create("LOOKUP_IN_EDITOR_KEY");

  private final Project myProject;
  private final Editor myEditor;
  private final LookupItem[] myItems;
  private String myPrefix;
  private final LookupItemPreferencePolicy myItemPreferencePolicy;
  private final CharFilter myCharFilter;

  private RangeMarker myLookupStartMarker;
  private String myInitialPrefix;
  private JList myList;
  private LookupCellRenderer myCellRenderer;
  private Boolean myPositionedAbove = null;

  private CaretListener myEditorCaretListener;
  private EditorMouseListener myEditorMouseListener;
  private DocumentListener myDocumentListener;

  private ArrayList<LookupListener> myListeners = new ArrayList<LookupListener>();
  private HashMap myUserMap = new HashMap();

  private boolean myCanceled = true;
  private boolean myDisposed = false;
  private int myIndex;

  public LookupImpl(Project project,
                    Editor editor,
                    LookupItem[] items,
                    String prefix,
                    LookupItemPreferencePolicy itemPreferencePolicy,
                    CharFilter filter){
    super(new JPanel(new BorderLayout()));
    myProject = project;
    myEditor = editor;
    myItems = items;
    myPrefix = prefix;
    myItemPreferencePolicy = itemPreferencePolicy;
    myCharFilter = filter;

    myEditor.putUserData(LOOKUP_IN_EDITOR_KEY, this);

    if (myPrefix == null){
      myPrefix = "";
    }
    myInitialPrefix = myPrefix;

    myList = new JList() ;
    myList.setFocusable(false);

    myCellRenderer = new LookupCellRenderer(this);
    myList.setCellRenderer(myCellRenderer);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    updateList();

    myList.setBackground(LookupCellRenderer.BACKGROUND_COLOR);

    JScrollPane scrollPane = new JScrollPane(myList);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(new com.intellij.ui.plaf.beg.BegPopupMenuBorder());
    getComponent().add(scrollPane, BorderLayout.CENTER);

    myEditorCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e){
        int curOffset = myEditor.getCaretModel().getOffset();
        if (curOffset != myLookupStartMarker.getStartOffset() + myPrefix.length()){
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

    myDocumentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        if (!myLookupStartMarker.isValid()){
          hide();
        }
      }
    };
    myEditor.getDocument().addDocumentListener(myDocumentListener);

    myList.addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e){
          LookupItem item = (LookupItem)myList.getSelectedValue();
          if (item != null && item.getAttribute(EMPTY_ITEM_ATTRIBUTE) != null){
            item = null;
          }
          fireCurrentItemChanged(item);
        }
      }
    );

    myList.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e){
          if (e.getClickCount() == 2){
            CommandProcessor.getInstance().executeCommand(
                myProject, new Runnable() {
                public void run() {
                  finishLookup(NORMAL_SELECT_CHAR);
                }
              },
                "",
                null
            );
          }
        }
      }
    );
    selectMostPreferableItem();

    final Application application = ApplicationManager.getApplication();

    if (!application.isUnitTestMode()) {
      application.invokeLater(
        new Runnable() {
          public void run(){
            if (myIndex >= 0 && myIndex < myList.getModel().getSize()){
              ListScrollingUtil.selectItem(myList, myIndex);
            }
            else if(myItems.length > 0){
              ListScrollingUtil.selectItem(myList, 0);
            }
          }
        }
      );
    }
  }


  Project getProject(){
    return myProject;
  }

  String getPrefix(){
    return myPrefix;
  }

  void setPrefix(String prefix){
    myPrefix = prefix;
  }

  String getInitialPrefix(){
    return myInitialPrefix;
  }

  JList getList(){
    return myList;
  }

  public LookupItem[] getItems(){
    return myItems;
  }

  void updateList(){
    final PatternMatcher matcher = new Perl5Matcher();
    final Pattern pattern = CompletionUtil.createCamelHumpsMatcher(myPrefix);
    Object oldSelected = myList.getSelectedValue();
    DefaultListModel model = new DefaultListModel();
    ArrayList<LookupItem> array = new ArrayList<LookupItem>();
    final String prefix = myPrefix;
    for (LookupItem item : myItems) {
      String text = item.getLookupString();
      if (StringUtil.startsWithIgnoreCase(text, prefix) || matcher.matches(text, pattern)) {
        model.addElement(item);
        array.add(item);
      }
    }
    boolean isEmpty = array.size() == 0;
    if (isEmpty){
      LookupItem item = new LookupItem(CompletionBundle.message("completion.no.suggestions"), "");
      item.setAttribute(EMPTY_ITEM_ATTRIBUTE, "");
      model.addElement(item);
      array.add(item);
    }
    //PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.saveToString());
    myList.setModel(model);

    myList.setVisibleRowCount(Math.min(myList.getModel().getSize(), CodeInsightSettings.getInstance().LOOKUP_HEIGHT));

    if (!isEmpty){
      selectMostPreferableItem();
      if (myIndex >= 0){
        ListScrollingUtil.selectItem(myList, myIndex);
      }
      else{
        if (oldSelected == null || !ListScrollingUtil.selectItem(myList, oldSelected)){
          ListScrollingUtil.selectItem(myList, 0);
        }
      }
    }

    LookupItem[] items = array.toArray(new LookupItem[array.size()]);
    int maxWidth = myCellRenderer.getMaximumWidth(items);
    myList.setFixedCellWidth(maxWidth);
  }

  /**
   * @return point in layered pane coordinate system.
   */
  Point calculatePosition(){
    Dimension dim = getComponent().getPreferredSize();
    int lookupStart = myLookupStartMarker.getStartOffset();
    LogicalPosition pos = myEditor.offsetToLogicalPosition(lookupStart);
    Point location = myEditor.logicalPositionToXY(pos);
    location.y += myEditor.getLineHeight();
    JComponent editorComponent = myEditor.getComponent();
    JComponent internalComponent = myEditor.getContentComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
    Point layeredPanePoint=SwingUtilities.convertPoint(internalComponent,location, layeredPane);
    layeredPanePoint.x = layeredPanePoint.x - myCellRenderer.ICON_WIDTH - 5 /*?*/;
    if (dim.width > layeredPane.getWidth()){
      dim.width = layeredPane.getWidth();
    }
    int wshift = layeredPane.getWidth() - (layeredPanePoint.x + dim.width);
    if (wshift < 0){
      layeredPanePoint.x += wshift;
    }
    if (myPositionedAbove == null){
      int shiftLow = layeredPane.getHeight() - (layeredPanePoint.y + dim.height);
      int shiftHigh = layeredPanePoint.y - dim.height;
      myPositionedAbove = shiftLow < 0 && shiftLow < shiftHigh ? Boolean.TRUE : Boolean.FALSE;
    }
    if (myPositionedAbove.booleanValue()){
      layeredPanePoint.y -= dim.height + myEditor.getLineHeight();
    }
    return layeredPanePoint;
  }

  public void finishLookup(final char completionChar){
    final LookupItem item = (LookupItem)myList.getSelectedValue();
    if (item == null){
      fireItemSelected(null, completionChar);
      hide();
      return;
    }

    if(item.getObject() instanceof DeferredUserLookupValue) {
      if(!((DeferredUserLookupValue)item.getObject()).handleUserSelection(item,myProject)) {
        fireItemSelected(null, completionChar);
        hide();
        return;
      }
    }

    final String s = item.getLookupString();
    final int prefixLength = myPrefix.length();
    if (item.getAttribute(EMPTY_ITEM_ATTRIBUTE) != null){
      fireItemSelected(null, completionChar);
      hide();
      return;
    }

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run(){
          myCanceled = false;
          hide();

          int lookupStart = myLookupStartMarker.getStartOffset();
          //SD - start
          //this patch fixes the problem, that template is finished after showing lookup
          LogicalPosition lookupPosition = myEditor.offsetToLogicalPosition(lookupStart);
          myEditor.getCaretModel().moveToLogicalPosition(lookupPosition);
          //SD - end

          if (myEditor.getSelectionModel().hasSelection()){
            myEditor.getDocument().deleteString(myEditor.getSelectionModel().getSelectionStart(), myEditor.getSelectionModel().getSelectionEnd());
          }
          if (s.startsWith(myPrefix)){
            myEditor.getDocument().insertString(lookupStart + prefixLength, s.substring(prefixLength));
          }
          else{
            if (prefixLength > 0){
              FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.camelHumps");
              myEditor.getDocument().deleteString(lookupStart, lookupStart + prefixLength);
            }
            myEditor.getDocument().insertString(lookupStart, s);
          }
          int offset = lookupStart + s.length();
          myEditor.getCaretModel().moveToOffset(offset);
          myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          myEditor.getSelectionModel().removeSelection();
          fireItemSelected(item, completionChar);
        }
      }
    );
  }

  public void show(){
    int offset = myEditor.getSelectionModel().hasSelection()
                 ? myEditor.getSelectionModel().getSelectionStart()
                 : myEditor.getCaretModel().getOffset();
    int lookupStart = offset - myPrefix.length();
    myLookupStartMarker = myEditor.getDocument().createRangeMarker(lookupStart, lookupStart);
    myLookupStartMarker.setGreedyToLeft(true);
    //myList.setSelectedIndex(0);
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    Point p = calculatePosition();
    HintManager hintManager = HintManager.getInstance();
    hintManager.showEditorHint(this, myEditor, p, HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING, 0, false);
  }

  private void selectMostPreferableItem(){
    //if (!isVisible()) return;

    myIndex = LookupItemUtil.doSelectMostPreferableItem(myItemPreferencePolicy, myPrefix, ((DefaultListModel)myList.getModel()).toArray());
    myList.setSelectedIndex(myIndex);
  }

  public LookupItem getCurrentItem(){
    LookupItem item = (LookupItem)myList.getSelectedValue();
    if (item != null && item.getAttribute(EMPTY_ITEM_ATTRIBUTE) != null){
      return null;
    }
    return item;
  }

  public void setCurrentItem(LookupItem item){
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
    JLayeredPane layeredPane=myList.getRootPane().getLayeredPane();
    Point layeredPanePoint=SwingUtilities.convertPoint(myList,listBounds.x,listBounds.y,layeredPane);
    itmBounds.x = layeredPanePoint.x;
    itmBounds.y = layeredPanePoint.y;
    return itmBounds;
  }

  private void fireItemSelected(final LookupItem item, char completionChar){
    PsiDocumentManager.getInstance(myProject).commitAllDocuments(); //[Mike] todo: remove? Valentin thinks it's a major performance hit.

    if (item != null && myItemPreferencePolicy != null){
      myItemPreferencePolicy.itemSelected(item);
    }

    if (myListeners.size() > 0){
      LookupEvent event = new LookupEvent(this, item, completionChar);
      LookupListener[] listeners = myListeners.toArray(new LookupListener[myListeners.size()]);
      for (LookupListener listener : listeners) {
        listener.itemSelected(event);
      }
    }
  }

  private void fireLookupCanceled(){
    if (myListeners.size() > 0){
      LookupEvent event = new LookupEvent(this, null);
      LookupListener[] listeners = myListeners.toArray(new LookupListener[myListeners.size()]);
      for (LookupListener listener : listeners) {
        listener.lookupCanceled(event);
      }
    }
  }

  private void fireCurrentItemChanged(LookupItem item){
    if (myListeners.size() > 0){
      LookupEvent event = new LookupEvent(this, item);
      LookupListener[] listeners = myListeners.toArray(new LookupListener[myListeners.size()]);
      for (LookupListener listener : listeners) {
        listener.currentItemChanged(event);
      }
    }
  }

  public boolean fillInCommonPrefix(boolean toCompleteUniqueName){
    ListModel listModel = myList.getModel();
    String commonPrefix = null;
    String subprefix = null;
    boolean isStrict = false;
    for(int i = 0; i < listModel.getSize(); i++){
      LookupItem item = (LookupItem)listModel.getElementAt(i);
      if (item.getAttribute(EMPTY_ITEM_ATTRIBUTE) != null) return false;
      String string = item.getLookupString();
      String string1 = string.substring(0, myPrefix.length());
      String string2 = string.substring(myPrefix.length());
      if (commonPrefix == null){
        commonPrefix = string2;
        subprefix = string1;
      }
      else{
        while(commonPrefix.length() > 0){
          if (string2.startsWith(commonPrefix)){
            if (string2.length() > commonPrefix.length()){
              isStrict = true;
            }
            if (!string1.equals(subprefix)){
              subprefix = null;
            }
            break;
          }
          commonPrefix = commonPrefix.substring(0, commonPrefix.length() - 1);
        }
        if (commonPrefix.length() == 0) return false;
      }
    }

    if (!isStrict && !toCompleteUniqueName) return false;

    final String _subprefix = subprefix;
    final String _commonPrefix = commonPrefix;
    CommandProcessor.getInstance().executeCommand(
        myProject, new Runnable() {
        public void run(){
          if (_subprefix != null){ // correct case
            int lookupStart = myLookupStartMarker.getStartOffset();
            myEditor.getDocument().replaceString(lookupStart, lookupStart + _subprefix.length(), _subprefix);
          }
          myPrefix += _commonPrefix;
          EditorModificationUtil.insertStringAtCaret(myEditor, _commonPrefix);
        }
      },
        null,
        null
    );
    myList.repaint(); // to refresh prefix highlighting
    return true;
  }

  public boolean isPositionedAboveCaret(){
    return myPositionedAbove.booleanValue();
  }

  public void hide(){
    if (myDisposed) return;
    if (IdeEventQueue.getInstance().getPopupManager().closeActivePopup()) {
      return;
    }
    myDisposed = true;

    myEditor.getCaretModel().removeCaretListener(myEditorCaretListener);
    myEditor.removeEditorMouseListener(myEditorMouseListener);
    myEditor.getDocument().removeDocumentListener(myDocumentListener);
    myEditor.putUserData(LOOKUP_IN_EDITOR_KEY, null);

    super.hide();

    if (myCanceled){
      fireLookupCanceled();
    }
  }

  public <T> T getUserData(Key<T> key){
    return (T)myUserMap.get(key);
  }

  public <T> void putUserData(Key<T> key, T value){
    if (value != null){
      myUserMap.put(key, value);
    }
    else{
      myUserMap.remove(key);
    }
  }

  static boolean isNarrowDownMode(){
    return CodeInsightSettings.getInstance().NARROW_DOWN_LOOKUP_LIST;
  }

  public CharFilter getCharFilter() {
    return myCharFilter;
  }
}
