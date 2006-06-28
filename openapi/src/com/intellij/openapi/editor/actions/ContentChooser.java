/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.CommonBundle;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public abstract class ContentChooser<Data> extends DialogWrapper {

  private static final Icon textIcon = IconLoader.getIcon("/fileTypes/text.png");

  private JList myList;
  private List<Data> myAllContents;

  private Editor myViewer;
  private final boolean myUseIdeaEditor;

  private Splitter mySplitter;
  private Project myProject;

  public ContentChooser(Project project, String title, boolean useIdeaEditor) {
    super(project, true);
    myProject = project;
    myUseIdeaEditor = useIdeaEditor;

    setOKButtonText(CommonBundle.getOkButtonText());
    setTitle(title);

    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  protected JComponent createCenterPanel() {
    myList = new JList();
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    rebuildListContent();

    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.isConsumed() || e.getClickCount() != 2 || e.isPopupTrigger()) return;
        close(OK_EXIT_CODE);
      }
    });

    myList.setCellRenderer(new MyListCellRenderer());

    if (myAllContents.size() > 0) {
      myList.setSelectedIndex(0);
    }

    myList.addKeyListener(new KeyAdapter() {
      public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
          int selectedIndex = getSelectedIndex();
          int size = myAllContents.size();
          removeContentAt(myAllContents.get(selectedIndex));
          rebuildListContent();
          if (size == 1) {
            close(CANCEL_EXIT_CODE);
            return;
          }
          myList.setSelectedIndex(Math.min(selectedIndex, myAllContents.size() - 1));
        }
        else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          close(OK_EXIT_CODE);
        }
        else {
          final char aChar = e.getKeyChar();
          if (aChar >= '0' && aChar <= '9') {
            int idx = aChar == '0' ? 9 : aChar - '1';
            if (idx < myAllContents.size()) {
              myList.setSelectedIndex(idx);
            }
          }
        }
      }
    });

    mySplitter = new Splitter(true);
    mySplitter.setFirstComponent(new JScrollPane(myList));
    mySplitter.setSecondComponent(new JPanel());
    updateViewerForSelection();

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateViewerForSelection();
      }
    });

    mySplitter.setPreferredSize(new Dimension(500, 500));

    return mySplitter;
  }

  protected abstract void removeContentAt(final Data Content);

  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.editor.actions.MultiplePasteAction.Chooser";
  }

  private void updateViewerForSelection() {
    if (myAllContents.size() == 0) return;
    String fullString = getStringRepresentationFor(myAllContents.get(getSelectedIndex()));
    fullString = StringUtil.convertLineSeparators(fullString);

    if (myViewer != null) {
      EditorFactory.getInstance().releaseEditor(myViewer);
    }

    if (myUseIdeaEditor) {
      Document doc = EditorFactory.getInstance().createDocument(fullString);
      myViewer = EditorFactory.getInstance().createViewer(doc, myProject);
      myViewer.getComponent().setPreferredSize(new Dimension(300, 500));
      myViewer.getSettings().setFoldingOutlineShown(false);
      myViewer.getSettings().setLineNumbersShown(false);
      myViewer.getSettings().setLineMarkerAreaShown(false);
      mySplitter.setSecondComponent(myViewer.getComponent());
    } else {
      final JTextArea textArea = new JTextArea(fullString);
      textArea.setRows(3);
      textArea.setWrapStyleWord(true);
      textArea.setLineWrap(true);
      textArea.setSelectionStart(0);
      textArea.setSelectionEnd(textArea.getText().length());
      textArea.setEditable(false);
      mySplitter.setSecondComponent(new JScrollPane(textArea));
    }
    mySplitter.revalidate();
  }

  public void dispose() {
    super.dispose();
    if (myViewer != null) {
      EditorFactory.getInstance().releaseEditor(myViewer);
      myViewer = null;
    }
  }

  private void rebuildListContent() {
    List<Data> allContents = new ArrayList<Data>(getContents());
    ArrayList<String> shortened = new ArrayList<String>();
    for (Data content : allContents) {
      String fullString = getStringRepresentationFor(content);
      if (fullString != null) {
        fullString = StringUtil.convertLineSeparators(fullString, "\n");
        int newLineIdx = fullString.indexOf('\n');
        if (newLineIdx == -1) {
          shortened.add(fullString.trim());
        }
        else {
          int lastLooked = 0;
          do  {
            int nextLineIdx = fullString.indexOf("\n", lastLooked);
            if (nextLineIdx > lastLooked) {
              shortened.add(fullString.substring(lastLooked, nextLineIdx).trim() + " ...");
              break;
            }
            else if (nextLineIdx == -1) {
              shortened.add(" ...");
              break;
            }
            lastLooked = nextLineIdx + 1;
          } while (true);
        }
      }
    }

    myAllContents = allContents;
    myList.setListData(shortened.toArray(new String[shortened.size()]));
  }

  protected abstract String getStringRepresentationFor(final Data content);

  protected abstract List<Data> getContents();

  public int getSelectedIndex() {
    if (myList.getSelectedIndex() == -1) return 0;
    return myList.getSelectedIndex();
  }

  public List<Data> getAllContents() {
    return myAllContents;
  }

  private static class MyListCellRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      setIcon(textIcon);
      if (index <= 9) {
        append(String.valueOf((index + 1) % 10) + "  ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      append((String) value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
