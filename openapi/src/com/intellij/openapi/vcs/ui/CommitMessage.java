package com.intellij.openapi.vcs.ui;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Iterator;

public class CommitMessage extends JPanel{

  private final JTextArea myCommentArea = new JTextArea();
  private final JComboBox myRecentMessages = new JComboBox();

  class RecentMessage {
    private final boolean canBeInserted;
    private final String myPresentation;
    private final String myValue;

    public RecentMessage(final boolean canBeInserted, final String presentation, final String value) {
      this.canBeInserted = canBeInserted;
      myPresentation = presentation;
      myValue = value;
    }

    public boolean isCanBeInserted() {
      return canBeInserted;
    }

    public String getPresentation() {
      return myPresentation;
    }

    public String getValue() {
      return myValue;
    }
  }

  public CommitMessage(ArrayList<String> recentMessages) {
    super(new BorderLayout());
    add(new JScrollPane(myCommentArea), BorderLayout.CENTER);
    add(new JLabel("Comment:"), BorderLayout.NORTH);
    if (!recentMessages.isEmpty()) {
      add(myRecentMessages, BorderLayout.SOUTH);

      myRecentMessages.addItem(new RecentMessage(false, "<Recent messages>", null));

      for (Iterator<String> iterator = recentMessages.iterator(); iterator.hasNext();) {
        String s = iterator.next();
        myRecentMessages.addItem(new RecentMessage(true, createPresentation(s), s));
      }

      myRecentMessages.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(
          JList list,
          Object value,
          int index,
          boolean selected,
          boolean hasFocus
          ) {
          if (value instanceof RecentMessage) {
            append(((RecentMessage)value).getPresentation(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      });

      myRecentMessages.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final Object selectedItem = myRecentMessages.getSelectedItem();
          if (selectedItem instanceof RecentMessage && ((RecentMessage)selectedItem).canBeInserted) {
            setText(((RecentMessage)selectedItem).getValue());
          }
        }
      });
    }
  }

  public JComponent getTextField() {
    return myCommentArea;
  }
  
  private String createPresentation(final String s) {
    String converted = s.replaceAll("\n", " ");
    if (converted.length() < 20) {
      return converted;
    } else {
      return converted.substring(0, 20) + "...";
    }
  }

  public void setText(final String initialMessage) {
    myCommentArea.setText(initialMessage);
  }

  public String getComment() {
    return myCommentArea.getText().trim();
  }

  public void init() {
    myCommentArea.setRows(3);
    myCommentArea.setWrapStyleWord(true);
    myCommentArea.setLineWrap(true);
    myCommentArea.setSelectionStart(0);
    myCommentArea.setSelectionEnd(myCommentArea.getText().length());

  }
}
