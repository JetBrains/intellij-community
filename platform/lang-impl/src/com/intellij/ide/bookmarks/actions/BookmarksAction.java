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

/*
 * @author max
 */
package com.intellij.ide.bookmarks.actions;

import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.ListUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// TODO: remove duplication with BaseShowRecentFilesAction, there's quite a bit of it

public class BookmarksAction extends AnAction implements DumbAware {
  private static final Color BORDER_COLOR = new Color(0x87, 0x87, 0x87);

  private final Alarm myPreviewUpdateAlarm = new Alarm();

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    e.getPresentation().setEnabled(project != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;


    final DefaultListModel model = buildModel(project);


    final JLabel pathLabel = new JLabel(" ");
    pathLabel.setHorizontalAlignment(SwingConstants.RIGHT);

    final Font font = pathLabel.getFont();
    pathLabel.setFont(font.deriveFont((float)10));

    final JList list = new JBList(model);

    final PreviewPanel previewPanel = new PreviewPanel(project);

    list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      private String getTitle2Text(String fullText) {
        int labelWidth = pathLabel.getWidth();
        if (fullText == null || fullText.length() == 0) return " ";
        while (pathLabel.getFontMetrics(pathLabel.getFont()).stringWidth(fullText) > labelWidth) {
          int sep = fullText.indexOf(File.separatorChar, 4);
          if (sep < 0) return fullText;
          fullText = "..." + fullText.substring(sep);
        }

        return fullText;
      }

      public void valueChanged(final ListSelectionEvent e) {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            updatePathLabel();
          }
        });
      }

      private void updatePreviewPanel(final ItemWrapper wrapper) {
        myPreviewUpdateAlarm.cancelAllRequests();
        myPreviewUpdateAlarm.addRequest(new Runnable() {
          public void run() {
            previewPanel.updateWithItem(wrapper);
          }
        }, 300);
      }

      private void updatePathLabel() {
        final Object[] values = list.getSelectedValues();
        if (values != null && values.length == 1) {
          ItemWrapper wrapper = (ItemWrapper)values[0];
          pathLabel.setText(getTitle2Text(wrapper.footerText()));
          updatePreviewPanel(wrapper);
        }
        else {
          updatePreviewPanel(null);
          pathLabel.setText(" ");
        }
      }
    });

    Runnable runnable = new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
          public void run() {
            Object[] values = list.getSelectedValues();

            if (values.length == 1) {
              ((ItemWrapper)values[0]).execute(project);
            }
            else {
              for (Object value : values) {
                if (value instanceof BookmarkItem) {
                  ((BookmarkItem)value).execute(project);
                }
              }
            }
          }
        });
      }
    };

    if (list.getModel().getSize() == 0) {
      list.clearSelection();
    }

    list.setCellRenderer(new ItemRenderer(project));


    JPanel footerPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(BORDER_COLOR);
        g.drawLine(0, 0, getWidth(), 0);
      }
    };

    footerPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    footerPanel.add(pathLabel);

    DefaultActionGroup actions = new DefaultActionGroup();
    EditBookmarkDescriptionAction editDescriptionAction = new EditBookmarkDescriptionAction(project, list);
    actions.add(editDescriptionAction);
    actions.add(new DeleteBookmarkAction(project, list));
    actions.add(new MoveBookmarkUpAction(project, list));
    actions.add(new MoveBookmarkDownAction(project, list));

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("", actions, true);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    actionToolbar.setMinimumButtonSize(new Dimension(16, 16));
    final JComponent toolBar = actionToolbar.getComponent();
    toolBar.setOpaque(false);

    final JBPopup popup = new PopupChooserBuilder(list).
      setTitle("Bookmarks").
      setMovable(true).
      setAutoselectOnMouseMove(false).
      setSettingButton(toolBar).
      setSouthComponent(footerPanel).
      setEastComponent(previewPanel).
      setItemChoosenCallback(runnable).
      setFilteringEnabled(new Function<Object, String>() {
        public String fun(Object o) {
          return ((ItemWrapper)o).speedSearchText();
        }
      }).createPopup();

    list.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
          int index = list.getSelectedIndex();
          if (index == -1 || index >= list.getModel().getSize()) {
            return;
          }
          Object[] values = list.getSelectedValues();
          for (Object value : values) {
            if (value instanceof BookmarkItem) {
              BookmarkItem item = (BookmarkItem)value;
              model.removeElement(item);
              if (model.getSize() > 0) {
                if (model.getSize() == index) {
                  list.setSelectedIndex(model.getSize() - 1);
                }
                else if (model.getSize() > index) {
                  list.setSelectedIndex(index);
                }
              }
              else {
                list.clearSelection();
              }
              BookmarkManager.getInstance(project).removeBookmark(item.myBookmark);
            }
          }
        }
        else if (e.getModifiersEx() == 0) {
          char mnemonic = e.getKeyChar();
          final Bookmark bookmark = BookmarkManager.getInstance(project).findBookmarkForMnemonic(mnemonic);
          if (bookmark != null) {
            popup.cancel();
            IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
              public void run() {
                bookmark.navigate();
              }
            });
          }
        }
      }
    });

    editDescriptionAction.setPopup(popup);
    popup.showCenteredInCurrentWindow(project);
  }

  private static DefaultListModel buildModel(Project project) {
    final DefaultListModel model = new DefaultListModel();

    for (Bookmark bookmark : BookmarkManager.getInstance(project).getValidBookmarks()) {
      model.addElement(new BookmarkItem(bookmark));
    }

    return model;
  }

  private interface ItemWrapper {
    void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected);
    void updateMnemonicLabel(JLabel label);

    void execute(Project project);

    String speedSearchText();

    @Nullable
    String footerText();

    void updatePreviewPanel(PreviewPanel panel);
  }

  private static class BookmarkItem implements ItemWrapper {
    private final Bookmark myBookmark;

    private BookmarkItem(Bookmark bookmark) {
      myBookmark = bookmark;
    }

    public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
      VirtualFile file = myBookmark.getFile();

      PsiManager psiManager = PsiManager.getInstance(project);

      PsiElement fileOrDir = file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);
      if (fileOrDir != null) {
        renderer.setIcon(fileOrDir.getIcon(Iconable.ICON_FLAG_CLOSED));
      }


      FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
      TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
      renderer.append(file.getName(), SimpleTextAttributes.fromTextAttributes(attributes));
      if (myBookmark.getLine() >= 0) {
        renderer.append(":", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        renderer.append(String.valueOf(myBookmark.getLine() + 1), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }

      if (!selected) {
        FileColorManager colorManager = FileColorManager.getInstance(project);
        if (fileOrDir instanceof PsiFile) {
          Color color = colorManager.getRendererBackground((PsiFile)fileOrDir);
          if (color != null) {
            renderer.setBackground(color);
          }
        }
      }

      String description = myBookmark.getDescription();
      if (description != null) {
        renderer.append(" " + description, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }

    public void updateMnemonicLabel(JLabel label) {
      final char mnemonic = myBookmark.getMnemonic();
      if (mnemonic != 0) {
        label.setText(Character.toString(mnemonic) + '.');
      }
      else {
        label.setText("");
      }
    }

    public String speedSearchText() {
      return myBookmark.getFile().getName() + " " + myBookmark.getDescription();
    }

    public void execute(Project project) {
      myBookmark.navigate();
    }

    public String footerText() {
      return myBookmark.getFile().getPresentableUrl();
    }

    public void updatePreviewPanel(final PreviewPanel panel) {
      VirtualFile file = myBookmark.getFile();
      Document document = FileDocumentManager.getInstance().getDocument(file);
      Project project = panel.myProject;

      if (document != null) {
        if (panel.myEditor == null || panel.myEditor.getDocument() != document) {
          panel.cleanup();
          panel.myEditor = EditorFactory.getInstance().createViewer(document, project);
          EditorHighlighter highlighter = EditorHighlighterFactory.getInstance()
            .createEditorHighlighter(file, EditorColorsManager.getInstance().getGlobalScheme(), project);
          ((EditorEx)panel.myEditor).setHighlighter(highlighter);
          ((EditorEx)panel.myEditor).setFile(file);

          panel.myEditor.getSettings().setAnimatedScrolling(false);
          panel.myEditor.getSettings().setRefrainFromScrolling(false);
          panel.myEditor.getSettings().setLineNumbersShown(true);
          panel.myEditor.getSettings().setFoldingOutlineShown(false);

          panel.add(panel.myEditor.getComponent(), BorderLayout.CENTER);
        }

        panel.myEditor.getCaretModel().moveToLogicalPosition(new LogicalPosition(myBookmark.getLine(), 0));
        panel.validate();
        panel.myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
      }
      else {
        panel.cleanup();

        JLabel label = new JLabel("Navigate to selected " + (file.isDirectory() ? "directory " : "file ") + "in Project View");
        label.setHorizontalAlignment(JLabel.CENTER);
        panel.add(label);
      }
    }
  }

  private static class ItemRenderer extends JPanel implements ListCellRenderer {
    private final Project myProject;
    private final ColoredListCellRenderer myRenderer;

    private ItemRenderer(Project project) {
      super(new BorderLayout());
      myProject = project;

      setBackground(UIUtil.getListBackground());

      final JLabel label = new JLabel();
      label.setFont(Bookmark.MNEMONIC_FONT);

      label.setPreferredSize(new JLabel("W.").getPreferredSize());
      label.setOpaque(false);

      if (BookmarkManager.getInstance(project).hasBookmarksWithMnemonics()) {
        add(label, BorderLayout.WEST);
      }

      myRenderer = new ColoredListCellRenderer() {
        @Override
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value instanceof ItemWrapper) {
            final ItemWrapper wrapper = (ItemWrapper)value;
            wrapper.setupRenderer(this, myProject, selected);
            wrapper.updateMnemonicLabel(label);
          }
        }
      };
      add(myRenderer, BorderLayout.CENTER);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      myRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      return this;
    }
  }

  protected static class BookmarkInContextInfo {
    private final DataContext myDataContext;
    private final Project myProject;
    private Bookmark myBookmarkAtPlace;
    private VirtualFile myFile;
    private int myLine;

    public BookmarkInContextInfo(DataContext dataContext, Project project) {
      myDataContext = dataContext;
      myProject = project;
    }

    public Bookmark getBookmarkAtPlace() {
      return myBookmarkAtPlace;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public int getLine() {
      return myLine;
    }

    public BookmarkInContextInfo invoke() {
      myBookmarkAtPlace = null;
      myFile = null;
      myLine = -1;


      BookmarkManager bookmarkManager = BookmarkManager.getInstance(myProject);
      if (ToolWindowManager.getInstance(myProject).isEditorComponentActive()) {
        Editor editor = PlatformDataKeys.EDITOR.getData(myDataContext);
        if (editor != null) {
          Document document = editor.getDocument();
          myLine = editor.getCaretModel().getLogicalPosition().line;
          myFile = FileDocumentManager.getInstance().getFile(document);
          myBookmarkAtPlace = bookmarkManager.findEditorBookmark(document, myLine);
        }
      }

      if (myFile == null) {
        myFile = PlatformDataKeys.VIRTUAL_FILE.getData(myDataContext);
        myLine = -1;

        if (myBookmarkAtPlace == null) {
          myBookmarkAtPlace = bookmarkManager.findFileBookmark(myFile);
        }
      }
      return this;
    }
  }

  private static class PreviewPanel extends JPanel {
    private final Project myProject;
    private Editor myEditor;
    private ItemWrapper myWrapper;

    public PreviewPanel(Project project) {
      super(new BorderLayout());
      myProject = project;
      setPreferredSize(new Dimension(600, 400));
    }

    public void updateWithItem(ItemWrapper wrapper) {
      if (myWrapper != wrapper) {
        myWrapper = wrapper;
        if (wrapper != null) {
          wrapper.updatePreviewPanel(this);
        }
        else {
          cleanup();
          repaint();
        }

        revalidate();
      }
    }

    private void cleanup() {
      removeAll();
      if (myEditor != null) {
        EditorFactory.getInstance().releaseEditor(myEditor);
        myEditor = null;
      }
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      cleanup();
    }
  }

  private static List<Bookmark> getSelectedBookmarks(JList list) {
    List<Bookmark> answer = new ArrayList<Bookmark>();

    for (Object value : list.getSelectedValues()) {
      if (value instanceof BookmarkItem) {
        answer.add(((BookmarkItem)value).myBookmark);
      }
      else {
        return Collections.emptyList();
      }
    }

    return answer;
  }

  private static class EditBookmarkDescriptionAction extends AnAction {
    private final JList myList;
    private final Project myProject;
    private JBPopup myPopup;

    private EditBookmarkDescriptionAction(Project project, JList list) {
      super("Edit Description", "Assign short description for the bookmark to be shown along the file name", IconLoader.getIcon("/actions/properties.png"));
      myProject = project;
      myList = list;
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(SystemInfo.isMac ? "meta ENTER" : "control ENTER")), list);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedBookmarks(myList).size() == 1);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Bookmark b = getSelectedBookmarks(myList).get(0);
      myPopup.setUiVisible(false);

      String description = Messages
        .showInputDialog(myProject, "Enter short bookmark description", "Bookmark Description", Messages.getQuestionIcon(), b.getDescription(),
                         new InputValidator() {
                           public boolean checkInput(String inputString) {
                             return true;
                           }

                           public boolean canClose(String inputString) {
                             return true;
                           }
                         });

      BookmarkManager.getInstance(myProject).setDescription(b, description);

      myPopup.setUiVisible(true);
      myPopup.setSize(myPopup.getContent().getPreferredSize());
    }

    public void setPopup(JBPopup popup) {
      myPopup = popup;
    }
  }

  private static class DeleteBookmarkAction extends AnAction {
    private final Project myProject;
    private final JList myList;

    private DeleteBookmarkAction(Project project, JList list) {
      super("Delete", "Delete current bookmark", IconLoader.getIcon("/general/remove.png"));
      myProject = project;
      myList = list;
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("DELETE")), list);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedBookmarks(myList).size() > 0);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      List<Bookmark> bookmarks = getSelectedBookmarks(myList);
      ListUtil.removeSelectedItems(myList);

      for (Bookmark bookmark : bookmarks) {
        BookmarkManager.getInstance(myProject).removeBookmark(bookmark);
      }
    }
  }

  private static boolean notFiltered(JList list) {
    if (!(list.getModel() instanceof FilteringListModel)) return true;
    final FilteringListModel model = (FilteringListModel)list.getModel();
    return model.getOriginalModel().getSize() == model.getSize();
  }

  private static class MoveBookmarkUpAction extends AnAction {
    private final Project myProject;
    private final JList myList;

    private MoveBookmarkUpAction(Project project, JList list) {
      super("Up", "Move current bookmark up", IconLoader.getIcon("/actions/previousOccurence.png"));
      myProject = project;
      myList = list;
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(SystemInfo.isMac ? "meta UP" : "control UP")), list);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(notFiltered(myList) && getSelectedBookmarks(myList).size() == 1  && myList.getSelectedIndex() > 0);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ListUtil.moveSelectedItemsUp(myList);
      BookmarkManager.getInstance(myProject).moveBookmarkUp(getSelectedBookmarks(myList).get(0));
    }
  }

  private static class MoveBookmarkDownAction extends AnAction {
    private final Project myProject;
    private final JList myList;

    private MoveBookmarkDownAction(Project project, JList list) {
      super("Down", "Move current bookmark down", IconLoader.getIcon("/actions/nextOccurence.png"));
      myProject = project;
      myList = list;
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(SystemInfo.isMac ? "meta DOWN" : "control DOWN")), list);
    }

    @Override
    public void update(AnActionEvent e) {
      int modelSize = myList.getModel().getSize();
      if (modelSize == 0 || !notFiltered(myList)) {
        e.getPresentation().setEnabled(false);
      }
      else {
        int lastIndex = modelSize - 1;
        if (!(myList.getModel().getElementAt(lastIndex) instanceof BookmarkItem)) lastIndex--;
        e.getPresentation().setEnabled(getSelectedBookmarks(myList).size() == 1 && myList.getSelectedIndex() < lastIndex);
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ListUtil.moveSelectedItemsDown(myList);
      BookmarkManager.getInstance(myProject).moveBookmarkDown(getSelectedBookmarks(myList).get(0));
    }
  }
}
