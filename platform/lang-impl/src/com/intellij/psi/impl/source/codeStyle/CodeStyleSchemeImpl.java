// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.configurationStore.SchemeDataHolder;
import com.intellij.configurationStore.SerializableScheme;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ExternalizableSchemeAdapter;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleSchemeImpl extends ExternalizableSchemeAdapter implements CodeStyleScheme, SerializableScheme {
  private static final Logger LOG = Logger.getInstance(CodeStyleSchemeImpl.class);

  private volatile SchemeDataHolder<? super CodeStyleSchemeImpl> myDataHolder;
  private final boolean myIsDefault;
  private volatile @NotNull CodeStyleSettings myCodeStyleSettings;
  private long myLastModificationCount;
  private final Object lock = new Object();

  CodeStyleSchemeImpl(@NotNull String name, @NotNull SchemeDataHolder<? super CodeStyleSchemeImpl> dataHolder) {
    setName(name);
    myDataHolder = dataHolder;
    myIsDefault = DEFAULT_SCHEME_NAME.equals(name);
    myCodeStyleSettings = init(null);
  }

  public CodeStyleSchemeImpl(@NotNull String name, boolean isDefault, @Nullable CodeStyleScheme parentScheme) {
    setName(name);
    myIsDefault = isDefault;
    myCodeStyleSettings = init(parentScheme);
  }

  private static @NotNull CodeStyleSettings init(@Nullable CodeStyleScheme parentScheme) {
    final CodeStyleSettings settings;
    if (parentScheme == null) {
      settings = CodeStyleSettingsManager.getInstance().createSettings();
      LOG.debug("Initialized using empty settings");
    }
    else {
      CodeStyleSettings parentSettings = parentScheme.getCodeStyleSettings();
      settings = CodeStyleSettingsManager.getInstance().cloneSettings(parentSettings);
      while (parentSettings.getParentSettings() != null) {
        parentSettings = parentSettings.getParentSettings();
      }
      settings.setParentSettings(parentSettings);
      LOG.debug("Initialized using parent scheme '" + parentScheme.getName() + "'");
    }

    return settings;
  }

  private static void readFromDataHolder(@NotNull CodeStyleSettings settings,
                                         @NotNull SchemeDataHolder<? super CodeStyleSchemeImpl> dataHolder) {
    Element root = dataHolder.read();
    try {
      settings.readExternal(root);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  @Override
  public @NotNull CodeStyleSettings getCodeStyleSettings() {
    SchemeDataHolder<? super CodeStyleSchemeImpl> dataHolder = myDataHolder;
    if (dataHolder == null) {
      return myCodeStyleSettings;
    }

    synchronized (lock) {
      dataHolder = myDataHolder;
      if (dataHolder == null) {
        return myCodeStyleSettings;
      }
      logDebug("Reinit settings from dataHolder");

      CodeStyleSettings settings = init(null);
      readFromDataHolder(settings, dataHolder);
      // nullize only after element is successfully read, otherwise our state will be undefined - both myDataHolder and myCodeStyleSettings are null
      myDataHolder = null;
      dataHolder.updateDigest(this);
      myCodeStyleSettings = settings;
      return settings;
    }
  }

  public void setCodeStyleSettings(@NotNull CodeStyleSettings codeStyleSettings) {
    myCodeStyleSettings = codeStyleSettings;
    synchronized (lock) {
      myDataHolder = null;
    }
    logDebug("Replaced code style settings");
  }

  @Override
  public boolean isDefault() {
    return myIsDefault;
  }

  @Override
  public @Nullable SchemeState getSchemeState() {
    synchronized (lock) {
      if (myDataHolder == null) {
        final long currModificationCount = myCodeStyleSettings.getModificationTracker().getModificationCount();
        if (myLastModificationCount != currModificationCount) {
          myLastModificationCount = currModificationCount;
          logDebug("Possibly changed");
          return SchemeState.POSSIBLY_CHANGED;
        }
      }
      return SchemeState.UNCHANGED;
    }
  }

  @Override
  public @NotNull Element writeScheme() {
    SchemeDataHolder<? super CodeStyleSchemeImpl> dataHolder;
    synchronized (lock) {
      dataHolder = myDataHolder;
    }

    if (dataHolder == null) {
      Element newElement = new Element(CODE_STYLE_TAG_NAME);
      newElement.setAttribute(CODE_STYLE_NAME_ATTR, getName());
      myCodeStyleSettings.writeExternal(newElement);
      logDebug("Saved from CodeStyleSettings");
      return newElement;
    }
    else {
      logDebug("Saved from dataHolder");
      return dataHolder.read();
    }
  }

  @Override
  public @NotNull @Nls String getDisplayName() {
    if (DEFAULT_SCHEME_NAME.equals(getName())) {
      return ApplicationBundle.message("code.style.scheme.default");
    }
    return super.getDisplayName();
  }

  private void logDebug(@NotNull String message) {
    LOG.debug("Scheme '"+ getName() + "': "+ message);
  }
}
