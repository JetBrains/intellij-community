/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class PsiReferenceContributorEP extends CustomLoadingExtensionPointBean implements KeyedLazyInstance<PsiReferenceContributor> {

  @Attribute("language")
  public String language = Language.ANY.getID();

  @Attribute("implementation")
  public String implementationClass;

  private final NotNullLazyValue<PsiReferenceContributor> myHandler = new NotNullLazyValue<PsiReferenceContributor>() {
    @Override
    @NotNull
    protected PsiReferenceContributor compute() {
      try {
        return (PsiReferenceContributor)instantiateExtension(implementationClass, ApplicationManager.getApplication().getPicoContainer());
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  };

  @NotNull
  @Override
  public PsiReferenceContributor getInstance() {
    return myHandler.getValue();
  }

  @Override
  public String getKey() {
    return language;
  }
}
