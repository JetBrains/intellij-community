// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.richcopy.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "EditorRichCopySettings", storages = @Storage("editor.rich.copy.xml"), category = SettingsCategory.UI)
public final class RichCopySettings implements PersistentStateComponent<RichCopySettings> {

  public static final @NotNull String ACTIVE_GLOBAL_SCHEME_MARKER = "__ACTIVE_GLOBAL_SCHEME__";

  private boolean myEnabled = true;
  private String  mySchemeName = ACTIVE_GLOBAL_SCHEME_MARKER;

  public static @NotNull RichCopySettings getInstance() {
    return ApplicationManager.getApplication().getService(RichCopySettings.class);
  }

  public @NotNull EditorColorsScheme getColorsScheme(@NotNull EditorColorsScheme editorColorsScheme) {
    EditorColorsScheme result = null;
    if (mySchemeName != null && !ACTIVE_GLOBAL_SCHEME_MARKER.equals(mySchemeName)) {
      result = EditorColorsManager.getInstance().getScheme(mySchemeName);
    }
    return result == null ? editorColorsScheme : result;
  }

  @Override
  public @Nullable RichCopySettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull RichCopySettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public @NotNull String getSchemeName() {
    return mySchemeName == null ? ACTIVE_GLOBAL_SCHEME_MARKER : mySchemeName;
  }

  public void setSchemeName(@Nullable String schemeName) {
    mySchemeName = schemeName;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }
}
