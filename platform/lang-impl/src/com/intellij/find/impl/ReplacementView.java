package com.intellij.find.impl;

import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ReplacementView extends JPanel {

  private String myReplacement;
  private LiveOccurrence myOccurrence;

  public interface Delegate {
    void performReplacement(LiveOccurrence occurrence, String replacement);
    void performReplaceAll();
  }

  private Delegate delegate;

  public Delegate getDelegate() {
    return delegate;
  }

  public void setDelegate(Delegate delegate) {
    this.delegate = delegate;
  }

  public ReplacementView(final String replacement, final LiveOccurrence occurrence) {
    myReplacement = replacement;
    myOccurrence = occurrence;
    JLabel jLabel = new JLabel(replacement);
    jLabel.setForeground(Color.WHITE);
    add(jLabel);
    JButton replace = new JButton("Replace");
    replace.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (delegate != null) {
          delegate.performReplacement(occurrence, replacement);
        }
      }
    });
    replace.setMnemonic('R');
    add(replace);
    JButton replaceAllButton = new JButton("Replace all");
    replaceAllButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (delegate != null) {
          delegate.performReplaceAll();
        }
      }
    });
    add(replaceAllButton);
    setBackground(IdeTooltipManager.GRAPHITE_COLOR);
    if (SystemInfo.isMac) {
      Font f = getFont();
      setFont(f.deriveFont(f.getStyle(), f.getSize() - 4));
    }
  }

}
