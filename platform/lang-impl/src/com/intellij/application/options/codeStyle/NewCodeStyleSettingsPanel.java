package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class NewCodeStyleSettingsPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.codeStyle.NewCodeStyleSettingsPanel");

  private final Configurable myTab;

  public NewCodeStyleSettingsPanel(Configurable tab) {
    super(new BorderLayout());
    myTab = tab;
    JComponent component = myTab.createComponent();
    add(component, BorderLayout.CENTER);

  }

  public boolean isModified() {
    return myTab.isModified();
  }

  public void updatePreview() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).getPanel().onSomethingChanged();
    }
  }

  public void apply() {
    try {
      if (myTab.isModified()) {
        myTab.apply();
      }
    }
    catch (ConfigurationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  public String getHelpTopic() {
    return myTab.getHelpTopic();
  }

  public void dispose() {
    myTab.disposeUIResources();
  }

  public void reset() {
    myTab.reset();
    updatePreview();
  }

  public String getDisplayName() {
    return myTab.getDisplayName();
  }

  public void setModel(final CodeStyleSchemesModel model) {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).setModel(model);
    }
  }

  public void onSomethingChanged() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).onSomethingChanged();
    }
  }

  public void resetFromClone() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).resetFromClone();
    }


  }
}
