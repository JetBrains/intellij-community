package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.editor.actions.ContentChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class CommitMessage extends JPanel{

  private final JTextArea myCommentArea = new JTextArea();
  private final JButton myHistory = new JButton("History...");

  public CommitMessage(final VcsConfiguration configuration, final Project project) {
    super(new BorderLayout());
    myHistory.setMnemonic('H');
    final JScrollPane scrollPane = new JScrollPane(myCommentArea);
    scrollPane.setPreferredSize(myCommentArea.getPreferredSize());
    add(scrollPane, BorderLayout.CENTER);
    add(new JLabel("Comment:"), BorderLayout.NORTH);
    final ArrayList<String> recentMessages = configuration.getRecentMessages();
    Collections.reverse(recentMessages);

    if (!recentMessages.isEmpty()) {
      final JPanel buttonPanel = new JPanel(new BorderLayout());
      buttonPanel.add(myHistory, BorderLayout.EAST);
      add(buttonPanel, BorderLayout.SOUTH);

      myHistory.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final ContentChooser<String> contentChooser = new ContentChooser<String>(project, "Choose Message", false){
            protected void removeContentAt(final String content) {
              configuration.removeMessage(content);
            }

            protected String getStringRepresentationFor(final String content) {
              return content;
            }

            protected List<String> getContents() {
              return recentMessages;
            }
          };
          contentChooser.show();
          if (contentChooser.isOK()) {
            final int selectedIndex = contentChooser.getSelectedIndex();
            if (selectedIndex >= 0) {
              setText(contentChooser.getAllContents().get(selectedIndex));
            }
          }
        }
      });
    }
  }

  public JComponent getTextField() {
    return myCommentArea;
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
