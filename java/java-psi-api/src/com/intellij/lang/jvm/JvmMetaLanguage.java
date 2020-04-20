// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.MetaLanguage;
import com.intellij.lang.jvm.source.JvmDeclarationSearcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointChangeListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class JvmMetaLanguage extends MetaLanguage {

  private static final Logger LOG = Logger.getInstance(JvmMetaLanguage.class);

  private Set<String> supportedLanguages;

  protected JvmMetaLanguage() {
    super("JVM");
    ExtensionPoint<KeyedLazyInstance<JvmDeclarationSearcher>> point = JvmDeclarationSearcher.EP.getPoint();
    if (point != null) {
      point.addExtensionPointListener(new ExtensionPointChangeListener() {
        @Override
        public void extensionListChanged() {
          supportedLanguages = getSupportedLanguages();
        }
      }, false, null);
      supportedLanguages = getSupportedLanguages();
    }
    else {
      supportedLanguages = Collections.emptySet();
      LOG.warn("'JvmMetaLanguage' requested but no implementations of 'JvmDeclarationSearcher' EP were found in the 'rootArea'");
    }
  }

  private static Set<String> getSupportedLanguages() {
    ExtensionPoint<LanguageExtensionPoint<JvmDeclarationSearcher>> jvmDeclarationSearcherEP =
      Extensions.getRootArea().getExtensionPoint(JvmDeclarationSearcher.EP.getName());

    LanguageExtensionPoint<JvmDeclarationSearcher>[] jvmDeclarationSearcherImplementations = jvmDeclarationSearcherEP.getExtensions();
    return Collections.unmodifiableSet(
      Arrays.stream(jvmDeclarationSearcherImplementations).map(ep -> ep.getKey()).collect(Collectors.toSet())
    );
  }

  @Override
  public boolean matchesLanguage(@NotNull Language language) {
    return language instanceof JvmLanguage || supportedLanguages.contains(language.getID());
  }
}
