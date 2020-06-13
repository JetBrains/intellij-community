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

public final class PsiSymbolReferenceProviderBean extends CustomLoadingExtensionPointBean<PsiSymbolReferenceProvider> {
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
   * Fully qualified name of the class of the provided {@code PsiSymbolReference}.<br/>
   * This attribute is ignored if {@link #anyReferenceClass} is set to {@code true}.<br/>
   * <br/>
   * When querying for references of some type the platform will only ask providers
   * which specify this type or its subtype as a reference class. For example, given:
   * <ul>
   * <li>provider 1 specifies that it returns references of type R1;</li>
   * <li>provider 2 specifies that it returns references of type R2;</li>
   * <li>R2 extends R1 (which in turn extends {@link PsiSymbolReference}).</li>
   * </ul>
   * When querying for references of:
   * <ul>
   * <li>{@link PsiSymbolReference} type, the platform will ask all providers for references,
   * since any external reference type is a subtype of {@code PsiSymbolReference};</li>
   * <li>R1 type, the platform will ask both providers for references;</li>
   * <li>R2 type, the platform will only ask the second provider,
   * since only second provider has specified that it's capable of returning references of R2 type.</li>
   * </ul>
   */
  @Attribute
  public String referenceClass = PsiSymbolReference.class.getName();

  /**
   * Set this to {@code true} if provider can return references of any class.
   * Effectively behaves as a bottom type, as if it could be set in {@link #referenceClass}.
   * <p>
   * Usually used in composite providers, which delegate to other providers/extensions/subsystems.
   */
  @Attribute
  public boolean anyReferenceClass = false;

  /**
   * Fully qualified name of the common supertype of all symbols that this provider's references could resolve to.
   */
  @Attribute
  @RequiredElement
  public String targetClass;

  @Attribute
  @RequiredElement
  public String implementationClass;

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }

  public @NotNull Language getHostLanguage() {
    Language language = Language.findLanguageByID(hostLanguage);
    if (language == null) {
      throw new PluginException("Cannot find language '" + hostLanguage + "'", getPluginDescriptor().getPluginId());
    }
    return language;
  }

  public @NotNull Class<? extends PsiExternalReferenceHost> getHostElementClass() {
    return loadClass(hostElementClass);
  }

  public @NotNull Class<? extends PsiSymbolReference> getReferenceClass() {
    return loadClass(referenceClass);
  }

  public @NotNull Class<? extends Symbol> getResolveTargetClass() {
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
