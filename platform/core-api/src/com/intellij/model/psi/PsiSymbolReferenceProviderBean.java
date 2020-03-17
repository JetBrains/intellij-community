// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.Language;
import com.intellij.model.Symbol;
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiSymbolReferenceProviderBean extends CustomLoadingExtensionPointBean<PsiSymbolReferenceProvider> {

  /**
   * {@link Language#getID() id} of the language for which references are provided.<br/>
   * The references will be provided for the specified language and its {@linkplain Language#getBaseLanguage() base languages}.
   */
  @Attribute
  @RequiredElement
  public String hostLanguage;

  /**
   * Fully qualified name of the class of the PsiElement for which references are provided.<br/>
   * The references will be provided for the specified class and its superclasses.
   */
  @Attribute
  @RequiredElement
  public String hostElementClass;

  /**
   * Fully qualified name of the common supertype of all symbols that this provider's references could resolve to.
   */
  @Attribute
  @RequiredElement
  public String targetClass;

  @Attribute
  @RequiredElement
  public String implementationClass;

  @Nullable
  @Override
  protected String getImplementationClassName() {
    return implementationClass;
  }

  @NotNull
  public Language getHostLanguage() {
    Language language = Language.findLanguageByID(hostLanguage);
    if (language == null) {
      throw new PluginException("Cannot find language '" + hostLanguage + "'", getPluginDescriptor().getPluginId());
    }
    return language;
  }

  @NotNull
  public Class<? extends PsiExternalReferenceHost> getHostElementClass() {
    return loadClass(hostElementClass);
  }

  @NotNull
  public Class<? extends Symbol> getResolveTargetClass() {
    return loadClass(targetClass);
  }

  @SuppressWarnings("unchecked")
  private <T> Class<T> loadClass(@NotNull String fqn) {
    PluginDescriptor pluginDescriptor = getPluginDescriptor();
    try {
      return (Class<T>)Class.forName(fqn, true, pluginDescriptor.getPluginClassLoader());
    }
    catch (ClassNotFoundException e) {
      throw new PluginException(e, pluginDescriptor.getPluginId());
    }
  }
}
