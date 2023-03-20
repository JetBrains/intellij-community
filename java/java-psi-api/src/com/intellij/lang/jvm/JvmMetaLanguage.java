// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.MetaLanguage;
import com.intellij.lang.jvm.source.JvmDeclarationSearcher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
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
    ExtensionPointImpl<@NotNull LanguageExtensionPoint<JvmDeclarationSearcher>> point = getPoint();
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

    ExtensionPointImpl<@NotNull LanguageExtensionPoint<JvmDeclarationSearcher>> point = getPoint();
    return point != null && matchesRegisteredLanguage(language, point);
  }

  private static boolean matchesRegisteredLanguage(@NotNull Language language,
                                                   @NotNull ExtensionPointImpl<@NotNull LanguageExtensionPoint<JvmDeclarationSearcher>> point) {
    return ExtensionProcessingHelper.INSTANCE.getByKey(point, language.getID(), JvmMetaLanguage.class, LanguageExtensionPoint::getKey) != null;
  }

  private @Nullable static ExtensionPointImpl<@NotNull LanguageExtensionPoint<JvmDeclarationSearcher>> getPoint() {
    ExtensionsAreaImpl area = (ExtensionsAreaImpl)ApplicationManager.getApplication().getExtensionArea();

    return area.getExtensionPointIfRegistered(JvmDeclarationSearcher.EP.getName());
  }
}
