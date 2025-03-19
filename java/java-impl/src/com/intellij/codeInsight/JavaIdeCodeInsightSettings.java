// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(name = "JavaIdeCodeInsightSettings", storages = @Storage("codeInsightSettings.xml"), category = SettingsCategory.CODE, perClient = true)
public class JavaIdeCodeInsightSettings implements PersistentStateComponent<JavaIdeCodeInsightSettings> {

  @XCollection(propertyElementName = "included-static-names", elementName = "name", valueAttributeName = "")
  public List<String> includedAutoStaticNames = new ArrayList<>();

  public static JavaIdeCodeInsightSettings getInstance() {
    return ApplicationManager.getApplication().getService(JavaIdeCodeInsightSettings.class);
  }

  @Override
  public @Nullable JavaIdeCodeInsightSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JavaIdeCodeInsightSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
