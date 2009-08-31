/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.application.ApplicationBundle;

import javax.swing.*;
import java.awt.*;

public class JavaErrorOptionsProvider implements ErrorOptionsProvider {
  private JCheckBox mySuppressWay;

  public JComponent createComponent() {
    mySuppressWay = new JCheckBox(ApplicationBundle.message("checkbox.suppress.with.suppresswarnings"));
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(mySuppressWay, BorderLayout.EAST);
    return panel;
  }

  public void reset() {
    mySuppressWay.setSelected(DaemonCodeAnalyzerSettings.getInstance().SUPPRESS_WARNINGS);
  }

  public void disposeUIResources() {
    mySuppressWay = null;
  }

  public void apply() {
    DaemonCodeAnalyzerSettings.getInstance().SUPPRESS_WARNINGS = mySuppressWay.isSelected();
  }

  public boolean isModified() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    return mySuppressWay.isSelected() != settings.SUPPRESS_WARNINGS;
  }

}