package com.intellij.find.impl.livePreview;

import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ReplacementView extends JPanel {

  private String myReplacement;
  private LiveOccurrence myOccurrence;
  private JButton myStatusButton;

  public interface Delegate {
    void performReplacement(LiveOccurrence occurrence, String replacement);
    void performReplaceAll();
    boolean isExcluded(LiveOccurrence occurrence);
    void exclude(LiveOccurrence occurrence);
  }

  private Delegate myDelegate;

  public Delegate getDelegate() {
    return myDelegate;
  }

  public void setDelegate(Delegate delegate) {
    this.myDelegate = delegate;
    updateStatusLabel();
  }

  @Override
  protected void paintComponent(Graphics graphics) {

  }

  public ReplacementView(final String replacement, final LiveOccurrence occurrence) {
    myReplacement = replacement;
    myOccurrence = occurrence;
    setLayout(new GridBagLayout());

    GridBagConstraints c = new GridBagConstraints();

    JLabel jLabel = new JLabel(replacement);
    jLabel.setForeground(Color.WHITE);
    JPanel labelPane = new JPanel(new FlowLayout()) {
      @Override
      protected void paintComponent(Graphics graphics) {}
    };
    labelPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    labelPane.add(jLabel);

    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = c.gridy = 0;
    c.gridwidth = 2;
    c.gridheight = 1;
    add(labelPane, c);
    JButton replace = new JButton("Replace");

    replace.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (myDelegate != null) {
          myDelegate.performReplacement(occurrence, replacement);
        }
      }
    });
    replace.setPreferredSize(new Dimension(80, 20));
    replace.setMnemonic('R');
    JPanel buttonsPane = new JPanel(){
      @Override
      protected void paintComponent(Graphics graphics) {      }
    };
    JButton replaceAllButton = new JButton("Replace all");

    replaceAllButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (myDelegate != null) {
          myDelegate.performReplaceAll();
        }
      }
    });
    replaceAllButton.setPreferredSize(new Dimension(100, 20));
    replaceAllButton.setMnemonic('a');

    myStatusButton = new JButton("Exclude");
    myStatusButton.setPreferredSize(new Dimension(80, 20));
    myStatusButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        myDelegate.exclude(myOccurrence);
        updateStatusLabel();
      }
    });
    myStatusButton.setMnemonic('l');
    buttonsPane.add(myStatusButton);

    setBackground(IdeTooltipManager.GRAPHITE_COLOR);
    if (SystemInfo.isMac) {
      Font f = getFont();
      setFont(f.deriveFont(f.getStyle(), f.getSize() - 4));
    }

    buttonsPane.add(replace);
    buttonsPane.add(replaceAllButton);

    c.gridx = 0;
    c.gridy = 1;

    c.gridwidth = 3;
    c.gridheight = 1;
    add(buttonsPane, c);
    setAlignmentX(Component.LEFT_ALIGNMENT);
  }

  private void updateStatusLabel() {
    myStatusButton.setText(myDelegate.isExcluded(myOccurrence) ? "Include" : "Exclude");
    myStatusButton.repaint();
  }

}
