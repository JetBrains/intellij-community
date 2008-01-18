package com.intellij.ide.bookmarks;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class BookmarksDialog extends DialogWrapper{
  private MyModel myModel;
  protected Table myTable;
  private JButton myGotoButton = new JButton(IdeBundle.message("button.go.to"));
  private JButton myRemoveButton = new JButton(IdeBundle.message("button.remove"));
  private JButton myRemoveAllButton = new JButton(IdeBundle.message("button.remove.all"));
  private JButton myMoveUpButton = new JButton(IdeBundle.message("button.move.up"));
  private JButton myMoveDownButton = new JButton(IdeBundle.message("button.move.down"));
  private JButton myCloseButton = new JButton(CommonBundle.getCloseButtonText());
  private JButton myHelpButton = new JButton(CommonBundle.getHelpButtonText());  
  protected BookmarkManager myBookmarkManager;

  protected class MyModel extends DefaultTableModel implements ItemRemovable {
    public MyModel() {
      super(new Object[0][], new Object[] {
        IdeBundle.message("column.bookmark"),
        IdeBundle.message("column.description")
      });
    }

    public boolean isCellEditable(int row, int column) {
      return column == 1; // description
    }

    public void setValueAt(Object aValue, int row, int column) {
      if (column == 1) {
        getBookmarkWrapper(row).getBookmark().setDescription((String)aValue);
      }
      super.setValueAt(aValue, row, column);
      myTable.repaint();
    }

    public Object getValueAt(int row, int column) {
      switch (column) {
        case 0:
          return getBookmarkWrapper(row).myDisplayText;
        case 1:
          return getBookmarkWrapper(row).getBookmark().getDescription();
        default:
          return super.getValueAt(row, column);
      }
    }

    public BookmarkWrapper getBookmarkWrapper(int row) {
      return (BookmarkWrapper)super.getValueAt(row, 0);
    }
  }

  protected BookmarksDialog(BookmarkManager bookmarkManager) {
    super(bookmarkManager.getProject(), true);
    myBookmarkManager = bookmarkManager;
    myModel = new MyModel();
    myTable = new Table(myModel);
  }

  protected Border createContentPaneBorder(){
    return null;
  }

  protected JComponent createSouthPanel(){
    return null;
  }

  protected JComponent createCenterPanel(){
    myTable.setColumnSelectionAllowed(false);
    myTable.setPreferredScrollableViewportSize(new Dimension(500, 200));
    JScrollPane tableScrollPane = ScrollPaneFactory.createScrollPane(myTable);

    myTable.getColumnModel().getColumn(0).setPreferredWidth(400);
    myTable.getColumnModel().getColumn(1).setPreferredWidth(100);

    myTable.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        handle(e);
      }

      public void keyPressed(KeyEvent e) {
        handle(e);
      }

      private void handle(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0) {
          myCloseButton.doClick();
        }
        else if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0) {
          myGotoButton.doClick();
        }
        /* // Commented out because this implementation clashes with doHelpAction() inherited from DialogWrapper.
           // See doHelpAction() in this class.
        else if (e.getKeyCode() == KeyEvent.VK_F1 && e.getModifiers() == 0) {
          myHelpButton.doClick();
        } */
      }
    });

    JPanel panel=new JPanel(new GridBagLayout());
    GridBagConstraints constr;

    constr = new GridBagConstraints();
    constr.weightx = 1;
    constr.weighty = 1;
    constr.insets = new Insets(5, 5, 5, 0);
    constr.fill = GridBagConstraints.BOTH;
    constr.anchor = GridBagConstraints.WEST;
    panel.add(tableScrollPane, constr);
    myTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.insets = new Insets(5, 0, 0, 0);
    constr.anchor = GridBagConstraints.NORTH;
    panel.add(createRightButtonPane(), constr);

    addListeners();

    DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
      public Component getTableCellRendererComponent(
        JTable table,
        Object value,
        boolean isSelected,
        boolean hasFocus,
        int row,
        int column
      ) {
        Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        BookmarkWrapper bookmarkWrapper = myModel.getBookmarkWrapper(row);
        setIcon(bookmarkWrapper.getBookmark().getIcon());
        return component;
      }
    };
    myTable.getColumnModel().getColumn(0).setCellRenderer(renderer);

    return panel;
  }

  // buttons
  protected JPanel createRightButtonPane() {
    JPanel pane = new JPanel(new GridBagLayout());

    GridBagConstraints constr = new GridBagConstraints();
    constr.insets = new Insets(0, 5, 5, 5);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.weightx = 1.0;
    pane.add(myGotoButton, constr);
    constr.gridy = 2;
    pane.add(myMoveUpButton, constr);
    constr.gridy = 3;
    pane.add(myMoveDownButton, constr);
    constr.gridy = 4;
    pane.add(myRemoveButton, constr);
    constr.gridy = 5;
    pane.add(myRemoveAllButton, constr);
    constr.gridy = 6;
    pane.add(myCloseButton, constr);
    constr.gridy = 7;
    pane.add(myHelpButton, constr);

    return pane;
  }

  private static class BookmarkWrapper {
    private Bookmark myBookmark;
    private String myDisplayText;

    BookmarkWrapper(Bookmark bookmark) {
      myBookmark = bookmark;
      myDisplayText = bookmark.toString();
    }

    public String toString() {
      return myDisplayText;
    }

    public Bookmark getBookmark() {
      return myBookmark;
    }
  }

  public <T extends Bookmark> void fillList(List<T> bookmarks, Bookmark selectedBookmark) {
    final List<Bookmark> list = new ArrayList<Bookmark>();
    list.add(selectedBookmark);
    fillList(bookmarks, list);
  }

  protected <T extends Bookmark> void fillList(List<T> bookmarks, Collection<Bookmark> selectedBookmarks) {
    while (myModel.getRowCount() > 0){
      myModel.removeRow(0);
    }
    for(int i = 0; i < bookmarks.size(); i++){
      Bookmark bookmark = bookmarks.get(i);
      myModel.addRow(new Object[] {new BookmarkWrapper(bookmark), null});
      if ((i == 0 && selectedBookmarks.size()==0 ) || selectedBookmarks.contains(bookmark) ) {
        myTable.getSelectionModel().addSelectionInterval(i, i);
      }
    }
    final int minIndex = myTable.getSelectionModel().getMinSelectionIndex();
    final int maxIndex = myTable.getSelectionModel().getMaxSelectionIndex();
    if (minIndex >= 0) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myTable.scrollRectToVisible(myTable.getCellRect(minIndex, 0, true).union(myTable.getCellRect(maxIndex,0,true)));
        }
      });
    }
    enableButtons();
  }

  protected void addListeners() {
    myTable.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            myGotoButton.doClick();
          }
        }
      }
    );

    myTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          enableButtons();
        }
      }
    );

    myGotoButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
          if (getSelectedBookmark() == null) return;
          gotoSelectedBookmark(true);
        }
      }
    );

    myRemoveButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
          removeSelectedBookmarks();
          myTable.requestFocus();
        }
      }
    );

    myRemoveAllButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
          while (myModel.getRowCount() > 0) {
            Bookmark bookmark = myModel.getBookmarkWrapper(0).getBookmark();
            myBookmarkManager.removeBookmark(bookmark);
            myModel.removeRow(0);
          }
          myTable.clearSelection();
          enableButtons();
          myTable.requestFocus();
        }
      }
    );

    myMoveUpButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
          final List<Bookmark> selectedBookmarks = getSelectedBookmarks();
          List<Bookmark> allBookmarks = null;
          for ( Bookmark bookmark : selectedBookmarks ) {
            allBookmarks = myBookmarkManager.moveBookmarkUp(bookmark);
          }
          fillList(allBookmarks, selectedBookmarks);
          myTable.requestFocus();
        }
      }
    );

    myMoveDownButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final List<Bookmark> selectedBookmarks = getSelectedBookmarks();
          final Bookmark[] bookmarksArray = selectedBookmarks.toArray(new Bookmark[0]);
          List<Bookmark> allBookmarks = null;
          for (int i = bookmarksArray.length - 1; i >= 0; i--) {
            allBookmarks = myBookmarkManager.moveBookmarkDown(bookmarksArray[i]);
          }
          fillList(allBookmarks, selectedBookmarks);
          myTable.requestFocus();
        }
      }
    );

    myCloseButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          close(CANCEL_EXIT_CODE);
        }
      }
    );

    myHelpButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          HelpManager.getInstance().invokeHelp("find.bookmarks");
        }
      }
    );

    ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet();
    new AnAction() {
      public void actionPerformed(AnActionEvent e){
        myGotoButton.doClick();
      }
    }.registerCustomShortcutSet(shortcutSet, getRootPane());

    getRootPane().registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          removeSelectedBookmarks();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );

    getRootPane().setDefaultButton(myGotoButton);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("find.bookmarks");
  }

  private void removeSelectedBookmarks() {
    for ( Bookmark selectedBookmark : getSelectedBookmarks() ){
      myBookmarkManager.removeBookmark(selectedBookmark);
    }
    TableUtil.removeSelectedItems(myTable);
    enableButtons();
  }

  protected void enableButtons() {
    int minSelectedIndex = myTable.getSelectionModel().getMinSelectionIndex();
    int maxSelectedIndex = myTable.getSelectionModel().getMaxSelectionIndex();
    myRemoveButton.setEnabled(minSelectedIndex != -1);
    myRemoveAllButton.setEnabled(myModel.getRowCount() > 0);
    myGotoButton.setEnabled(minSelectedIndex != -1 && minSelectedIndex == maxSelectedIndex);
    myMoveUpButton.setEnabled(minSelectedIndex > 0);
    myMoveDownButton.setEnabled(maxSelectedIndex != -1 && maxSelectedIndex < myModel.getRowCount() - 1);
  }

  abstract protected void gotoSelectedBookmark(boolean closeWindow);

  @Nullable
  protected Bookmark getSelectedBookmark() {
    List<Bookmark> selected = getSelectedBookmarks();
    return selected.size() == 1 ? selected.get(0) : null;
  }

  protected List<Bookmark> getSelectedBookmarks() {
    List<Bookmark> bookmarks = new ArrayList<Bookmark>();
    final ListSelectionModel model = myTable.getSelectionModel();
    int minIndex = model.getMinSelectionIndex();
    int maxIndex = model.getMaxSelectionIndex();
    if (minIndex>=0) {
      for ( int i = minIndex; i <= maxIndex; i++ ) {
        if ( model.isSelectedIndex(i)) {
          bookmarks.add ( myModel.getBookmarkWrapper(i).getBookmark() );
        }
      }
    }
    return bookmarks;
  }

  public void dispose() {
    stopCellEditing();
    super.dispose();
  }

  private void stopCellEditing() {
    if (myTable.isEditing()) {
      TableCellEditor editor = myTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }
}