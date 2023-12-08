// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class LanguageLevelProjectExtension {
  public static final Topic<LanguageLevelChangeListener> LANGUAGE_LEVEL_CHANGED_TOPIC =
    Topic.create("Java language level", LanguageLevelChangeListener.class);

  public static LanguageLevelProjectExtension getInstance(Project project) {
    return project.getService(LanguageLevelProjectExtension.class);
  }

  /**
   * @return configured project language level.
   * May return {@linkplain LanguageLevel#isUnsupported() unsupported} language level.
   */
  @NotNull
  public abstract LanguageLevel getLanguageLevel();

  public abstract void setLanguageLevel(@NotNull LanguageLevel languageLevel);

  private Boolean myDefault;

  /**
   * Auto-detect language level from project JDK maximum possible level.
   * @return null if the property is not set yet (e.g. after migration).
   */
  @Nullable
  public Boolean getDefault() {
    return myDefault;
  }

  public void setDefault(@Nullable Boolean value) {
    myDefault = value;
  }

  public boolean isDefault() {
    return myDefault != null && myDefault;
  }

  public void languageLevelsChanged() {
  }

  public interface LanguageLevelChangeListener {
    void onLanguageLevelsChanged();
  }
}
