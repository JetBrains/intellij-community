package com.intellij.ide.passwordSafe.config;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * A configurable for password safe
 */
public class PasswordSafeConfigurable implements SearchableConfigurable {
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
  PasswordSafeOptionsPanel myPanel = null;

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
    return "Password Safe";
  }

  /**
   * {@inheritDoc}
   */
  public Icon getIcon() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public String getHelpTopic() {
    // TODO add help
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public JComponent createComponent() {
    myPanel = new PasswordSafeOptionsPanel(myPasswordSafe);
    myPanel.load(mySettings);
    return myPanel.getRoot();  //To change body of implemented methods use File | Settings | File Templates.
  }

  /**
   * {@inheritDoc}
   */
  public boolean isModified() {
    return myPanel.isModified(mySettings);  //To change body of implemented methods use File | Settings | File Templates.
  }

  /**
   * {@inheritDoc}
   */
  public void apply() throws ConfigurationException {
    myPanel.save(mySettings);
  }

  /**
   * {@inheritDoc}
   */
  public void reset() {
    myPanel.load(mySettings);
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
