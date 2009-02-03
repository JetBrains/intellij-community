/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 17-Mar-2006
 */
public class DefaultSearchableConfigurable implements Configurable {
  private final SearchableConfigurable myDelegate;
  private JComponent myComponent;

  public DefaultSearchableConfigurable(final SearchableConfigurable delegate) {
    myDelegate = delegate;
  }

  @NonNls
  public String getId() {
    return myDelegate.getId();
  }

  public void clearSearch() {
  }

  public void enableSearch(String option) {
    Runnable runnable = myDelegate.enableSearch(option);
    if (runnable != null){
      runnable.run();
    }
  }

  public String getDisplayName() {
    return myDelegate.getDisplayName();
  }

  public Icon getIcon() {
    return myDelegate.getIcon();
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return myDelegate.getHelpTopic();
  }

  public JComponent createComponent() {
    myComponent = myDelegate.createComponent();
    return myComponent;
  }

  public boolean isModified() {
    return myDelegate.isModified();
  }

  public void apply() throws ConfigurationException {
    myDelegate.apply();
  }

  public void reset() {
    myDelegate.reset();
  }

  public void disposeUIResources() {
    myComponent = null;
    myDelegate.disposeUIResources();
  }

  public Configurable getDelegate() {
    return myDelegate;
  }

}
