// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
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
  public abstract @NotNull LanguageLevel getLanguageLevel();

  @RequiresWriteLock
  public abstract void setLanguageLevel(@NotNull LanguageLevel languageLevel);

  /**
   * Auto-detect language level from project JDK maximum possible level.
   * @return null if the property is not set yet (e.g. after migration).
   */
  public abstract @Nullable Boolean getDefault();

  @RequiresWriteLock
  public abstract void setDefault(@Nullable Boolean value);

  public boolean isDefault() {
    Boolean currentValue = getDefault();
    return currentValue != null && currentValue;
  }

  public void languageLevelsChanged() {
  }

  public interface LanguageLevelChangeListener {
    void onLanguageLevelsChanged();
  }
}
