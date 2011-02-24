package com.intellij.find.impl.livePreview;

import javax.swing.*;
import java.awt.*;

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
  }

  @Override
  protected void paintComponent(Graphics graphics) {

  }

  public ReplacementView(final String replacement, final LiveOccurrence occurrence) {
    myReplacement = replacement;
    JLabel jLabel = new JLabel(myReplacement);
    jLabel.setForeground(Color.WHITE);
    add(jLabel);
  }
}
