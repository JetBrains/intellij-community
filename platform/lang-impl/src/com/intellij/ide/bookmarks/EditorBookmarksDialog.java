package com.intellij.ide.bookmarks;

import com.intellij.ide.IdeBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class EditorBookmarksDialog extends BookmarksDialog {
  private final JButton myViewSourceButton;

  private EditorBookmarksDialog(BookmarkManager bookmarkManager) {
    super(bookmarkManager);
    myViewSourceButton = new JButton(IdeBundle.message("button.view.source"));
    init();
  }

  protected void addListeners(){
    super.addListeners();
    myViewSourceButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (getSelectedBookmark() == null) return;
        gotoSelectedBookmark(false);
        myTable.requestFocus();
      }
    });
  }

  protected void enableButtons() {
    super.enableButtons();
    myViewSourceButton.setEnabled(getSelectedBookmark() != null);
  }

  protected void gotoSelectedBookmark(boolean closeWindow) {
    Bookmark bookmark = getSelectedBookmark();
    if (bookmark == null) return;

    bookmark.navigate();

    if (closeWindow){
      close(CANCEL_EXIT_CODE);
    }
  }

  protected JPanel createRightButtonPane() {
    JPanel panel = super.createRightButtonPane();

    GridBagConstraints constr = new GridBagConstraints();
    constr.insets = new Insets(0, 5, 5, 5);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.weightx = 1.0;
    constr.gridy = 1;
    panel.add(myViewSourceButton, constr);

    return panel;
  }

  public static void execute(BookmarkManager manager, Bookmark currentBookmark) {
    BookmarksDialog dialog = new EditorBookmarksDialog(manager);
    dialog.setTitle(IdeBundle.message("title.editor.bookmarks"));
    dialog.fillList(manager.getValidBookmarks(), currentBookmark);
    dialog.show();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.ide.bookmarks.EditorBookmarksDialog";
  }
}


