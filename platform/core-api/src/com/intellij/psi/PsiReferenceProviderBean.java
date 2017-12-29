/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.ElementPatternBean;
import com.intellij.patterns.StandardPatterns;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Registers a {@link PsiReferenceProvider} in plugin.xml
 */
public class PsiReferenceProviderBean extends AbstractExtensionPointBean implements KeyedLazyInstance<PsiReferenceProviderBean> {

  public static final ExtensionPointName<PsiReferenceProviderBean> EP_NAME =
    new ExtensionPointName<>("com.intellij.psi.referenceProvider");

  @Attribute("language")
  public String language = Language.ANY.getID();

  @Attribute("providerClass")
  public String className;

  @Tag("description")
  public String description;

  @Property(surroundWithTag = false)
  @XCollection
  public ElementPatternBean[] patterns;

  public String getDescription() {
    return description;
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.PsiReferenceProviderBean");

  public PsiReferenceProvider instantiate() {
    try {
      return (PsiReferenceProvider)instantiate(className, ApplicationManager.getApplication().getPicoContainer());
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
    return null;
  }

  private static final NullableFunction<ElementPatternBean,ElementPattern<? extends PsiElement>> PATTERN_NULLABLE_FUNCTION =
    elementPatternBean -> elementPatternBean.compilePattern();

  @Nullable
  public ElementPattern<PsiElement> createElementPattern() {
    if (patterns.length > 1) {
      List<ElementPattern<? extends PsiElement>> list = ContainerUtil.mapNotNull(patterns, PATTERN_NULLABLE_FUNCTION);
      //noinspection unchecked
      return StandardPatterns.or(list.toArray(new ElementPattern[list.size()]));
    }
    else if (patterns.length == 1) {
      return patterns[0].compilePattern();
    }
    else {
      LOG.error("At least one pattern should be specified");
      return null;
    }
  }

  @NotNull
  @Override
  public String getKey() {
    return language;
  }

  @NotNull
  @Override
  public PsiReferenceProviderBean getInstance() {
    return this;
  }
}
