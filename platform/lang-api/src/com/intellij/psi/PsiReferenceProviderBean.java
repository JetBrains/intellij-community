/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.ElementPatternBean;
import com.intellij.patterns.StandardPatterns;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Registers a {@link PsiReferenceProvider} in plugin.xml
 */
public class PsiReferenceProviderBean extends AbstractExtensionPointBean {

  public static final ExtensionPointName<PsiReferenceProviderBean> EP_NAME =
    new ExtensionPointName<PsiReferenceProviderBean>("com.intellij.psi.referenceProvider");
  @Attribute("providerClass")
  public String className;

  @Tag("description")
  public String description;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
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

  private static final NullableFunction<ElementPatternBean,ElementPattern<? extends PsiElement>> PATTERN_NULLABLE_FUNCTION = new NullableFunction<ElementPatternBean, ElementPattern<? extends PsiElement>>() {
    @Override
    public ElementPattern<? extends PsiElement> fun(ElementPatternBean elementPatternBean) {
      return elementPatternBean.compilePattern();
    }
  };

  @Nullable
  public ElementPattern<PsiElement> createElementPattern() {
    if (patterns.length > 1) {
      return StandardPatterns.or(ContainerUtil.mapNotNull(patterns,
                                                          PATTERN_NULLABLE_FUNCTION).toArray(new ElementPattern[0]));
    }
    else if (patterns.length == 1) {
      return patterns[0].compilePattern();
    }
    else {
      LOG.error("At least one pattern should be specified");
      return null;
    }
  }
}
