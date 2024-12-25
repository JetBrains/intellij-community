// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.MetaLanguage;
import com.intellij.lang.jvm.source.JvmDeclarationSearcher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionsArea;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class JvmMetaLanguage extends MetaLanguage {
  private JvmMetaLanguage() {
    super("JVM");
  }

  @Override
  public @NotNull Collection<Language> getMatchingLanguages() {
    ExtensionPoint<@NotNull LanguageExtensionPoint<JvmDeclarationSearcher>> point = getPoint();
    List<Language> result = new ArrayList<>();
    for (Language language : Language.getRegisteredLanguages()) {
      if (language instanceof JvmLanguage || (point != null && matchesRegisteredLanguage(language, point))) {
        result.add(language);
      }
    }
    return result;
  }

  @Override
  public boolean matchesLanguage(@NotNull Language language) {
    if (language instanceof JvmLanguage) {
      return true;
    }

    ExtensionPoint<@NotNull LanguageExtensionPoint<JvmDeclarationSearcher>> point = getPoint();
    return point != null && matchesRegisteredLanguage(language, point);
  }

  private static boolean matchesRegisteredLanguage(@NotNull Language language,
                                                   @NotNull ExtensionPoint<@NotNull LanguageExtensionPoint<JvmDeclarationSearcher>> point) {
    return point.getByKey(language.getID(), JvmMetaLanguage.class, LanguageExtensionPoint::getKey) != null;
  }

  private static @Nullable ExtensionPoint<@NotNull LanguageExtensionPoint<JvmDeclarationSearcher>> getPoint() {
    ExtensionsArea area = ApplicationManager.getApplication().getExtensionArea();
    return area.getExtensionPointIfRegistered(JvmDeclarationSearcher.EP.getName());
  }
}
