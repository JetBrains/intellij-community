// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.ElementPatternBean;
import com.intellij.patterns.StandardPatterns;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registers a {@link PsiReferenceProvider} for given pattern(s)
 * via extension point {@code com.intellij.psi.referenceProvider}.
 */
public class PsiReferenceProviderBean implements KeyedLazyInstance<PsiReferenceProviderBean>, PluginAware {
  public static final ExtensionPointName<PsiReferenceProviderBean> EP_NAME =
    new ExtensionPointName<>("com.intellij.psi.referenceProvider");

  @Attribute("language")
  public String language = Language.ANY.getID();

  @Attribute("providerClass")
  @RequiredElement
  public String className;

  @Tag("description")
  public String description;

  @Property(surroundWithTag = false)
  @XCollection
  @RequiredElement
  public ElementPatternBean[] patterns;
  private PluginDescriptor pluginDescriptor;

  public String getDescription() {
    return description;
  }

  public PsiReferenceProvider instantiate() {
    try {
      return ApplicationManager.getApplication().instantiateClass(className, pluginDescriptor);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      Logger.getInstance(PsiReferenceProviderBean.class).error(e);
    }
    return null;
  }

  @Override
  @Transient
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }

  public @Nullable ElementPattern<PsiElement> createElementPattern() {
    if (patterns == null || patterns.length == 0) {
      Logger.getInstance(PsiReferenceProviderBean.class)
        .error(new PluginException("At least one pattern should be specified", pluginDescriptor.getPluginId()));
      return null;
    }
    if (patterns.length > 1) {
      List<ElementPattern<PsiElement>> result = new ArrayList<>(patterns.length);
      for (ElementPatternBean t : patterns) {
        ElementPattern<PsiElement> o = t.compilePattern();
        if (o != null) {
          result.add(o);
        }
      }
      result = result.isEmpty() ? Collections.emptyList() : result;
      //noinspection unchecked
      return StandardPatterns.or(result.toArray(new ElementPattern[0]));
    }
    else {
      return patterns[0].compilePattern();
    }
  }

  @Override
  public @NotNull String getKey() {
    return language;
  }

  @Override
  public @NotNull PsiReferenceProviderBean getInstance() {
    return this;
  }
}
