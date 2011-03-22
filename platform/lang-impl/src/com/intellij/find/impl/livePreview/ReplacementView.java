package com.intellij.find.impl.livePreview;

import javax.swing.*;
import java.awt.*;

public class ReplacementView extends JPanel {

  private static final String MALFORMED_REPLACEMENT_STRING = "Malformed replacement string";
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
    String textToShow = myReplacement;
    if (myReplacement == null) {
      textToShow = MALFORMED_REPLACEMENT_STRING;
    }
    JLabel jLabel = new JLabel(textToShow);
    jLabel.setForeground(myReplacement != null ? Color.WHITE : Color.RED);
    add(jLabel);
  }
}
