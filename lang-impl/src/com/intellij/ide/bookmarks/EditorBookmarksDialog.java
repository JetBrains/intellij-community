package com.intellij.ide.bookmarks;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class EditorBookmarksDialog extends BookmarksDialog {
  private JButton myViewSourceButton;

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
    EditorBookmark bookmark = (EditorBookmark)getSelectedBookmark();
    if (bookmark == null) return;
    final Project project = myBookmarkManager.getProject();
    OpenFileDescriptor editSourceDescriptor = bookmark.getOpenFileDescriptor();
    if (editSourceDescriptor == null) return;
    FileEditorManager.getInstance(project).openTextEditor(editSourceDescriptor, false);

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
    dialog.fillList(manager.getValidEditorBookmarks(), currentBookmark);
    dialog.show();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.ide.bookmarks.EditorBookmarksDialog";
  }
}


