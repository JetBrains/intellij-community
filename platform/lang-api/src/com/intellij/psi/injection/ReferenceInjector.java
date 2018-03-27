/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.injection;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * This will work only in presence of IntelliLang plugin.
 *
 * @author Dmitry Avdeev
 */
public abstract class ReferenceInjector extends Injectable {

  public static final ExtensionPointName<ReferenceInjector> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.referenceInjector");

  @Override
  public final Language getLanguage() {
    return null;
  }

  /**
   * Generated references should be soft ({@link com.intellij.psi.PsiReference#isSoft()})
   */
  @NotNull
  public abstract PsiReference[] getReferences(@NotNull PsiElement element, @NotNull final ProcessingContext context, @NotNull TextRange range);

  public static ReferenceInjector findById(final String id) {
    return ContainerUtil.find(EXTENSION_POINT_NAME.getExtensions(), injector -> id.equals(injector.getId()));
  }
}
