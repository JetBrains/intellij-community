/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jun 8, 2007
 * Time: 8:41:25 PM
 */
package com.intellij.lang.injection;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class InjectedLanguageManager implements ProjectComponent {

  /** @see com.intellij.lang.injection.MultiHostInjector#MULTIHOST_INJECTOR_EP_NAME */
  @Deprecated
  public static final ExtensionPointName<MultiHostInjector> MULTIHOST_INJECTOR_EP_NAME = MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME;

  private final static NotNullLazyKey<InjectedLanguageManager, Project> INSTANCE_CACHE = ServiceManager.createLazyKey(InjectedLanguageManager.class);

  public static InjectedLanguageManager getInstance(Project project) {
    return INSTANCE_CACHE.getValue(project);
  }

  @Nullable
  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement element);

  @NotNull
  public abstract TextRange injectedToHost(@NotNull PsiElement injectedContext, @NotNull TextRange injectedTextRange);
  public abstract int injectedToHost(@NotNull PsiElement injectedContext, int injectedOffset);

  /** @see com.intellij.lang.injection.MultiHostInjector#MULTIHOST_INJECTOR_EP_NAME */
  @Deprecated
  public abstract void registerMultiHostInjector(@NotNull MultiHostInjector injector);
  /** @see com.intellij.lang.injection.MultiHostInjector#MULTIHOST_INJECTOR_EP_NAME */
  @Deprecated
  public abstract boolean unregisterMultiHostInjector(@NotNull MultiHostInjector injector);

  public abstract String getUnescapedText(@NotNull PsiElement injectedNode);

  @NotNull
  public abstract List<TextRange> intersectWithAllEditableFragments(@NotNull PsiFile injectedPsi, @NotNull TextRange rangeToEdit);

  public abstract boolean isInjectedFragment(PsiFile file);

  @Nullable
  public abstract PsiElement findInjectedElementAt(@NotNull PsiFile hostFile, int hostDocumentOffset);
}
