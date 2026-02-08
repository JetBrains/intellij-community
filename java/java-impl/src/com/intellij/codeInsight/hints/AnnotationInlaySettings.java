// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
@State(name = "AnnotationInlaySettings", storages = @Storage("editor.xml"), category = SettingsCategory.CODE)
public final class AnnotationInlaySettings implements PersistentStateComponent<AnnotationInlaySettings> {

  public boolean shortenNotNull = true;

  public static @NotNull AnnotationInlaySettings getInstance() {
    return ApplicationManager.getApplication().getService(AnnotationInlaySettings.class);
  }
  
  @Override
  public @NotNull AnnotationInlaySettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull AnnotationInlaySettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
