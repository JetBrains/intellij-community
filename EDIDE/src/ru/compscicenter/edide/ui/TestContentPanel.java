package ru.compscicenter.edide.ui;

import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class TestContentPanel extends JPanel {
  public static final Dimension PREFERRED_SIZE = new Dimension(300, 200);
  private static final Font HEADER_FONT = new Font("Arial", Font.BOLD, 16);
  private final JTextArea myInputArea = new JTextArea();
  private final JTextArea myOutputArea = new JTextArea();
  public TestContentPanel() {
    this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    initContentLabel("input", myInputArea);
    initContentLabel("output", myOutputArea);
  }

  private void initContentLabel(final String headerText, @NotNull final JTextArea contentArea) {
    JLabel headerLabel = new JLabel(headerText);
    headerLabel.setFont(HEADER_FONT);
    this.add(headerLabel);
    this.add(new JSeparator(SwingConstants.HORIZONTAL));
    JScrollPane scroll = new JBScrollPane(contentArea);
    scroll.setPreferredSize(PREFERRED_SIZE);
    this.add(scroll);
  }

  public void addInputContent(final String content) {
    myInputArea.setText(content);
  }

  public  void addOutputContent(final String content) {
    myOutputArea.setText(content);
  }
}
