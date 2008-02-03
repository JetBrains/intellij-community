package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileTextField;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class FileTextFieldImpl implements FileLookup, Disposable, FileTextField {

  private JTextField myPathTextField;

  private CompletionResult myCurrentCompletion;
  private JBPopup myCurrentPopup;
  private JList myList;

  private MergingUpdateQueue myUiUpdater;

  private boolean myPathIsUpdating;
  private Finder myFinder;
  private LookupFilter myFilter;
  private String myCompletionBase;

  private int myCurrentCompletionsPos = 1;
  private String myFileSpitRegExp;
  public static final String KEY = "fileTextField";

  private boolean myAutopopup = true;
  private FileTextFieldImpl.CancelAction myCancelAction;
  private Set<Action> myDisabledTextActions;

  public FileTextFieldImpl(Finder finder, LookupFilter filter) {
    this(new JTextField(), finder, filter, null);
  }

  public FileTextFieldImpl(JTextField field, Finder finder, LookupFilter filter, MergingUpdateQueue uiUpdater) {
    myPathTextField = field;


    final InputMap listMap = (InputMap)UIManager.getDefaults().get("List.focusInputMap");
    final KeyStroke[] listKeys = listMap.keys();
    myDisabledTextActions = new HashSet<Action>();
    for (KeyStroke eachListStroke : listKeys) {
      final String listActionID = (String)listMap.get(eachListStroke);
      if ("selectNextRow".equals(listActionID) || "selectPreviousRow".equals(listActionID)) {
        final String textActionID = (String)field.getInputMap().get(eachListStroke);
        if (textActionID != null) {
          final Action textAction = field.getActionMap().get(textActionID);
          if (textAction != null) {
            myDisabledTextActions.add(textAction);
          }
        }
      }
    }


    FileTextFieldImpl assigned = (FileTextFieldImpl)myPathTextField.getClientProperty(KEY);
    if (assigned != null) {
      assigned.myFinder = finder;
      assigned.myFilter = filter;
      return;
    }

    myPathTextField.putClientProperty(KEY, this);
    final boolean headless = ApplicationManager.getApplication().isUnitTestMode();

    if (uiUpdater == null) {
      myUiUpdater = new MergingUpdateQueue("FileTextField.UiUpdater", 200, false, myPathTextField);
      if (!headless) {
        new UiNotifyConnector(myPathTextField, myUiUpdater);
        Disposer.register(this, myUiUpdater);
      }
    }
    else {
      myUiUpdater = uiUpdater;
    }

    myFinder = finder;
    myFilter = filter;

    myFileSpitRegExp = myFinder.getSeparator().replaceAll("\\\\", "\\\\\\\\");

    myPathTextField.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(final DocumentEvent e) {
        processTextChanged();
      }

      public void removeUpdate(final DocumentEvent e) {
        processTextChanged();
      }

      public void changedUpdate(final DocumentEvent e) {
        processTextChanged();
      }
    });

    myPathTextField.addKeyListener(new KeyAdapter() {
      public void keyPressed(final KeyEvent e) {
        processListSelection(e);
      }
    });

    myPathTextField.addFocusListener(new FocusAdapter() {
      public void focusLost(final FocusEvent e) {
        closePopup();
      }
    });

    myCancelAction = new CancelAction();
  }

  public void dispose() {
  }

  private void processTextChanged() {
    if (myAutopopup && !isPathUpdating()) {
      suggestCompletion(false, false);
    }

    onTextChanged(getTextFieldText());
  }

  protected void onTextChanged(final String newValue) {
  }

  private void suggestCompletion(final boolean selectReplacedText, boolean isExplicitCall) {
    if (isExplicitCall) {
      myAutopopup = true;
    }

    if (!getField().isFocusOwner()) return;

    final CompletionResult result = new CompletionResult();
    if (myList != null && myCurrentCompletion != null) {
      int index = myList.getSelectedIndex();
      if (index >= 0 && index < myList.getModel().getSize()) {
        result.myPreselected = (LookupFile)myList.getSelectedValue();
      }
    }

    myUiUpdater.queue(new Update("textField.suggestCompletion") {
      public void run() {
        result.myCompletionBase = getCompletionBase();
        if (result.myCompletionBase == null) return;
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            processCompletion(result);
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                if (!result.myCompletionBase.equals(getCompletionBase())) return;

                int pos = selectCompletionRemoveText(result, selectReplacedText);

                showCompletionPopup(result, pos);
              }
            });
          }
        });
      }
    });

  }

  private int selectCompletionRemoveText(final CompletionResult result, boolean selectReplacedText) {
    int pos = myPathTextField.getCaretPosition();

    if (result.myToComplete.size() > 0 && selectReplacedText) {
      myPathTextField.setCaretPosition(myPathTextField.getText().length());
      myPathTextField.moveCaretPosition(pos);
    }

    return pos;
  }

  public static class CompletionResult {
    public List<LookupFile> myToComplete;
    public List<LookupFile> mySiblings;
    public List<LookupFile> myKidsAfterSeparator;
    public String myCompletionBase;
    public LookupFile myClosestParent;
    public LookupFile myPreselected;
  }

  private void showCompletionPopup(final CompletionResult result, int position) {
    if (myList == null) {
      myList = new JList();
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      myList.setCellRenderer(new GroupedItemsListRenderer(new ListItemDescriptor() {
        public String getTextFor(final Object value) {
          final LookupFile file = (LookupFile)value;
          return (myCurrentCompletion != null && myCurrentCompletion.myKidsAfterSeparator.contains(file) ? myFinder.getSeparator() : "") +
                 file.getName();
        }

        public String getTooltipFor(final Object value) {
          return null;
        }

        public Icon getIconFor(final Object value) {
          return null;
        }

        public boolean hasSeparatorAboveOf(final Object value) {
          return getCaptionAboveOf(value) != null;
        }

        public String getCaptionAboveOf(final Object value) {
          if (myCurrentCompletion == null) return null;
          final LookupFile file = (LookupFile)value;
          if (myCurrentCompletion.myKidsAfterSeparator.indexOf(file) == 0 && myCurrentCompletion.mySiblings.size() > 0) {
            final LookupFile parent = file.getParent();
            return parent == null ? "" : parent.getName();
          }
          else {
            return null;
          }
        }
      }));
    }


    if (myCurrentPopup != null) {
      closePopup();
    }

    myCurrentCompletion = result;
    myCurrentCompletionsPos = position;

    if (myCurrentCompletion.myToComplete.size() == 0) return;

    myList.setModel(new AbstractListModel() {
      public int getSize() {
        return myCurrentCompletion.myToComplete.size();
      }

      public Object getElementAt(final int index) {
        return myCurrentCompletion.myToComplete.get(index);
      }
    });
    myList.getSelectionModel().clearSelection();
    final PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(myList);
    builder.addListener(new JBPopupListener() {
      public void beforeShown(final Project project, final JBPopup popup) {
        myPathTextField
          .registerKeyboardAction(myCancelAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        for (Action each : myDisabledTextActions) {
          each.setEnabled(false);
        }
      }

      public void onClosed(final JBPopup popup) {
        myPathTextField.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        for (Action each : myDisabledTextActions) {
          each.setEnabled(true);
        }
      }
    });

    myCurrentPopup =
      builder.setRequestFocus(false).setAutoSelectIfEmpty(false).setResizable(false).setCancelCalllback(new Computable<Boolean>() {
        public Boolean compute() {
          final int caret = myPathTextField.getCaretPosition();
          myPathTextField.setSelectionStart(caret);
          myPathTextField.setSelectionEnd(caret);
          myPathTextField.setFocusTraversalKeysEnabled(true);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              getField().requestFocus();
            }
          });
          return Boolean.TRUE;
        }
      }).setItemChoosenCallback(new Runnable() {
        public void run() {
          processChosenFromCompletion(true);
        }
      }).setCancelKeyEnabled(false).setAlpha(0.75f).setFocusOwners(new Component[]{myPathTextField}).createPopup();


    if (result.myPreselected != null) {
      myList.setSelectedValue(result.myPreselected, false);
    }

    myPathTextField.setFocusTraversalKeysEnabled(false);

    myCurrentPopup.showInScreenCoordinates(getField(), getLocationForCaret());
  }

  private Point getLocationForCaret() {
    Point point;

    try {
      final Rectangle rec = myPathTextField.modelToView(myPathTextField.getCaretPosition());
      point = new Point((int)rec.getMaxX(), (int)rec.getMaxY());
    }
    catch (BadLocationException e) {
      return myPathTextField.getCaret().getMagicCaretPosition();
    }

    SwingUtilities.convertPointToScreen(point, myPathTextField);

    return point;
  }

  public void processCompletion(final CompletionResult result) {
    result.myToComplete = new ArrayList<LookupFile>();
    result.mySiblings = new ArrayList<LookupFile>();
    result.myKidsAfterSeparator = new ArrayList<LookupFile>();
    String typed = result.myCompletionBase;

    final LookupFile current = getClosestParent(typed);
    result.myClosestParent = current;

    if (current == null) return;
    if (typed == null || typed.length() == 0) return;

    final String typedText = myFinder.normalize(typed);
    final boolean currentParentMatch = SystemInfo.isFileSystemCaseSensitive
                                       ? typedText.equals(current.getAbsolutePath())
                                       : typedText.equalsIgnoreCase(current.getAbsolutePath());

    final boolean closedPath = typed.endsWith(myFinder.getSeparator()) && typedText.length() > myFinder.getSeparator().length();
    final String currentParentText = current.getAbsolutePath();

    if (!typedText.toUpperCase().startsWith(currentParentText.toUpperCase())) return;

    String prefix = typedText.substring(currentParentText.length());
    if (prefix.startsWith(myFinder.getSeparator())) {
      prefix = prefix.substring(myFinder.getSeparator().length());
    }
    else if (typed.endsWith(myFinder.getSeparator())) {
      prefix = "";
    }

    final String effectivePrefix = prefix.toUpperCase();

    final LookupFile currentGrandparent = current.getParent();
    final String[] grandparentPrefix = new String[1];
    if (currentGrandparent != null && currentParentMatch && !closedPath) {
      final String currentGrandparentText = currentGrandparent.getAbsolutePath();
      if (typedText.startsWith(currentGrandparentText + myFinder.getSeparator())) {
        grandparentPrefix[0] =
          currentParentText.substring(currentGrandparentText.length() + myFinder.getSeparator().length()).toUpperCase();
      }
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        result.myToComplete.addAll(current.getChildren(new LookupFilter() {
          public boolean isAccepted(final LookupFile file) {
            return myFilter.isAccepted(file) && file.getName().toUpperCase().startsWith(effectivePrefix);
          }
        }));

        if (currentParentMatch && !closedPath) {
          result.myKidsAfterSeparator.addAll(result.myToComplete);
        }

        if (grandparentPrefix[0] != null) {
          final List<LookupFile> siblings = currentGrandparent.getChildren(new LookupFilter() {
            public boolean isAccepted(final LookupFile file) {
              return !file.equals(current) && myFilter.isAccepted(file) && file.getName().toUpperCase().startsWith(grandparentPrefix[0]);
            }
          });
          result.myToComplete.addAll(0, siblings);
          result.mySiblings.addAll(siblings);
        }

        int currentDiff = Integer.MIN_VALUE;
        LookupFile toPreselect = result.myPreselected;

        if (toPreselect == null || !result.myToComplete.contains(toPreselect)) {
          if (effectivePrefix.length() > 0) {
            for (LookupFile each : result.myToComplete) {
              String eachName = each.getName().toUpperCase();
              if (!eachName.startsWith(effectivePrefix)) continue;
              int diff = effectivePrefix.compareTo(eachName);
              currentDiff = Math.max(diff, currentDiff);
              if (currentDiff == diff) {
                toPreselect = each;
              }
            }
          }
          else {
            toPreselect = null;
          }

          if (toPreselect == null) {
            if (result.myToComplete.size() == 1) {
              toPreselect = result.myToComplete.get(0);
            }
            else if (effectivePrefix.length() == 0) {
              if (result.mySiblings.size() > 0) {
                toPreselect = result.mySiblings.get(0);
              }
              if (!result.myToComplete.contains(toPreselect) && result.myToComplete.size() > 0) {
                toPreselect = result.myToComplete.get(0);
              }
            }
          }
        }

        if (currentParentMatch && result.mySiblings.size() > 0) {
          toPreselect = null;
        }

        result.myPreselected = toPreselect;
      }
    });
  }

  private
  @Nullable
  LookupFile getClosestParent(final String typed) {
    if (typed == null) return null;
    LookupFile lastFound = myFinder.find(typed);
    if (lastFound == null) return null;
    if (lastFound.exists()) return lastFound;

    final String[] splits = myFinder.normalize(typed).split(myFileSpitRegExp);
    StringBuffer fullPath = new StringBuffer();
    for (int i = 0; i < splits.length; i++) {
      String each = splits[i];
      fullPath.append(each);
      if (i < splits.length - 1) {
        fullPath.append(myFinder.getSeparator());
      }
      final LookupFile file = myFinder.find(fullPath.toString());
      if (file == null || !file.exists()) return lastFound;
      lastFound = file;
    }

    return lastFound;
  }

  public
  @Nullable
  LookupFile getFile() {
    String text = getTextFieldText();
    if (text == null) return null;
    return myFinder.find(text);
  }

  private void processChosenFromCompletion(boolean closePath) {
    final LookupFile file = getSelectedFileFromCompletionPopup();
    if (file == null) return;

    String text = file.getAbsolutePath();
    if (closePath) {
      if (file.isDirectory() && !text.endsWith(myFinder.getSeparator())) {
        text += myFinder.getSeparator();
      }
    }
    myPathTextField.setText(text);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void processListSelection(final KeyEvent e) {
    if (togglePopup(e)) return;

    if (!isPopupShowing()) return;

    final Object action = getAction(e, myList);

    if ("selectNextRow".equals(action)) {
      if (ensureSelectionExists()) {
        ListScrollingUtil.moveDown(myList, e.getModifiersEx());
      }
    }
    else if ("selectPreviousRow".equals(action)) {
      ListScrollingUtil.moveUp(myList, e.getModifiersEx());
    }
    else if ("scrollDown".equals(action)) {
      ListScrollingUtil.movePageDown(myList);
    }
    else if ("scrollUp".equals(action)) {
      ListScrollingUtil.movePageUp(myList);
    }
    else if (getSelectedFileFromCompletionPopup() != null && e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_TAB) {
      myCurrentPopup.cancel();
      e.consume();
      processChosenFromCompletion(true);
    }
  }

  private
  @Nullable
  LookupFile getSelectedFileFromCompletionPopup() {
    if (myList == null) return null;
    return (LookupFile)myList.getSelectedValue();
  }

  private boolean ensureSelectionExists() {
    if (myList.getSelectedIndex() < 0 || myList.getSelectedIndex() >= myList.getModel().getSize()) {
      if (myList.getModel().getSize() >= 0) {
        myList.setSelectedIndex(0);
        return false;
      }
    }

    return true;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private boolean togglePopup(KeyEvent e) {
    final KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
    final Object action = ((InputMap)UIManager.get("ComboBox.ancestorInputMap")).get(stroke);
    if ("selectNext".equals(action)) {
      if (!isPopupShowing()) {
        suggestCompletion(true, true);
        return true;
      }
      else {
        return false;
      }
    }
    else if ("togglePopup".equals(action)) {
      if (isPopupShowing()) {
        closePopup();
      }
      else {
        suggestCompletion(true, true);
      }
      return true;
    }
    else {
      final Keymap active = KeymapManager.getInstance().getActiveKeymap();
      final String[] ids = active.getActionIds(stroke);
      if (ids.length > 0 && "CodeCompletion".equals(ids[0])) {
        suggestCompletion(true, true);
      }
    }

    return false;
  }

  private static Object getAction(final KeyEvent e, final JComponent comp) {
    final KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
    return comp.getInputMap().get(stroke);
  }


  public JTextField getField() {
    return myPathTextField;
  }

  private boolean isPopupShowing() {
    return myCurrentPopup != null && myList != null && myList.isShowing();
  }

  private void closePopup() {
    if (myCurrentPopup != null) {
      myCurrentPopup.cancel();
      myCurrentPopup = null;
    }
    myCurrentCompletion = null;
  }

  @Nullable
  public String getTextFieldText() {
    final String text = myPathTextField.getText();
    if (text == null) return null;
    return text;
  }

  public final void setText(final String text, boolean now, @Nullable final Runnable onDone) {
    final Update update = new Update("pathFromTree") {
      public void run() {
        myPathIsUpdating = true;
        getField().setText(text);
        myPathIsUpdating = false;
        if (onDone != null) {
          onDone.run();
        }
      }
    };
    if (now) {
      update.run();
    }
    else {
      myUiUpdater.queue(update);
    }
  }

  public boolean isPathUpdating() {
    return myPathIsUpdating;
  }

  public
  @Nullable
  String getCompletionBase() {
    String text = getTextFieldText();
    if (text == null) return null;
    int pos = myPathTextField.getCaretPosition();
    return pos < text.length() ? text.substring(0, pos) : text;
  }

  public static class Vfs extends FileTextFieldImpl {


    public Vfs(final LookupFilter filter) {
      super(new LocalFsFinder(), filter);
    }

    public Vfs(FileChooserDescriptor filter, boolean showHidden, JTextField field) {
      super(field, new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(filter, showHidden), null);
    }

    public Vfs(FileChooserDescriptor filter, boolean showHidden, final MergingUpdateQueue uiUpdater) {
      super(new JTextField(), new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(filter, showHidden), uiUpdater);
    }

    public Vfs(final LookupFilter filter, final MergingUpdateQueue uiUpdater) {
      super(new JTextField(), new LocalFsFinder(), filter, uiUpdater);
    }

    public VirtualFile getSelectedFile() {
      LookupFile lookupFile = getFile();
      return lookupFile != null ? ((LocalFsFinder.VfsFile)lookupFile).getFile() : null;
    }
  }

  private class CancelAction implements ActionListener {
    public void actionPerformed(final ActionEvent e) {
      if (myCurrentPopup != null) {
        myAutopopup = false;
        myCurrentPopup.cancel();
      }
    }
  }
}
