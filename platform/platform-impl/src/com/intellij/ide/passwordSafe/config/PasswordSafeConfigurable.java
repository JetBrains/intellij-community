package com.intellij.ide.passwordSafe.config;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * A configurable for password safe
 */
public class PasswordSafeConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  /**
   * The settings for the password safe
   */
  final PasswordSafeSettings mySettings;
  /**
   * The password safe service
   */
  private final PasswordSafe myPasswordSafe;
  /**
   * The option panel to use
   */
  PasswordSafeOptionsPanel myPanel;

  /**
   * The constructor
   *
   * @param settings the password safe settings
   */
  public PasswordSafeConfigurable(@NotNull PasswordSafeSettings settings, @NotNull PasswordSafe passwordSafe) {
    mySettings = settings;
    myPasswordSafe = passwordSafe;
  }

  /**
   * {@inheritDoc}
   */
  @Nls
  public String getDisplayName() {
    return "Passwords";
  }

  /**
   * {@inheritDoc}
   */
  public String getHelpTopic() {
    return "reference.ide.settings.password.safe";
  }

  /**
   * {@inheritDoc}
   */
  public JComponent createComponent() {
    myPanel = new PasswordSafeOptionsPanel(myPasswordSafe);
    myPanel.reset(mySettings);
    return myPanel.getRoot();  //To change body of implemented methods use File | Settings | File Templates.
  }

  /**
   * {@inheritDoc}
   */
  public boolean isModified() {
    return myPanel != null && myPanel.isModified(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  public void apply() throws ConfigurationException {
    myPanel.apply(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  public void reset() {
    myPanel.reset(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  public void disposeUIResources() {
    myPanel = null;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public String getId() {
    return "application.passwordSafe";
  }

  /**
   * {@inheritDoc}
   */
  public Runnable enableSearch(String option) {
    return null;
  }
}
