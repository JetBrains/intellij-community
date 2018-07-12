// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.MetaLanguage;
import com.intellij.lang.jvm.source.JvmDeclarationSearcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @since 2018.2
 */
public class JvmMetaLanguage extends MetaLanguage {

  private static final Logger LOG = Logger.getInstance(JvmMetaLanguage.class);

  private final Set<String> supportedLanguages;

  protected JvmMetaLanguage() {
    super("JVM");
    ExtensionsArea rootArea = Extensions.getRootArea();
    if (rootArea.hasExtensionPoint(JvmDeclarationSearcher.EP.getName())) {
      ExtensionPoint<LanguageExtensionPoint<JvmDeclarationSearcher>> jvmDeclarationSearcherEP =
        rootArea.getExtensionPoint(JvmDeclarationSearcher.EP.getName());

      LanguageExtensionPoint<JvmDeclarationSearcher>[] jvmDeclarationSearcherImplementations = jvmDeclarationSearcherEP.getExtensions();
      supportedLanguages = Collections.unmodifiableSet(
        Arrays.stream(jvmDeclarationSearcherImplementations).map(ep -> ep.getKey()).collect(Collectors.toSet())
      );
    }
    else {
      supportedLanguages = Collections.emptySet();
      LOG.warn("'JvmMetaLanguage' requested but no implementations of 'JvmDeclarationSearcher' EP were found in the 'rootArea'");
    }
  }

  @Override
  public boolean matchesLanguage(@NotNull Language language) {
    return language instanceof JvmLanguage || supportedLanguages.contains(language.getID());
  }
}
