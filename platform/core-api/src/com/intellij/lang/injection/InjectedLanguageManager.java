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

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class InjectedLanguageManager {

  /** @see com.intellij.lang.injection.MultiHostInjector#MULTIHOST_INJECTOR_EP_NAME */
  @Deprecated
  public static final ExtensionPointName<MultiHostInjector> MULTIHOST_INJECTOR_EP_NAME = MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME;

  protected static final NotNullLazyKey<InjectedLanguageManager, Project> INSTANCE_CACHE = ServiceManager.createLazyKey(InjectedLanguageManager.class);

  public static InjectedLanguageManager getInstance(Project project) {
    return INSTANCE_CACHE.getValue(project);
  }

  @Nullable
  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement element);

  @NotNull
  public abstract TextRange injectedToHost(@NotNull PsiElement injectedContext, @NotNull TextRange injectedTextRange);
  public abstract int injectedToHost(@NotNull PsiElement injectedContext, int injectedOffset);

  /**
   * Test-only method.
   * @see com.intellij.lang.injection.MultiHostInjector#MULTIHOST_INJECTOR_EP_NAME
   */
  @Deprecated
  public abstract void registerMultiHostInjector(@NotNull MultiHostInjector injector);

  /**
   * Test-only method.
   * @see com.intellij.lang.injection.MultiHostInjector#MULTIHOST_INJECTOR_EP_NAME
   */
  @Deprecated
  public abstract boolean unregisterMultiHostInjector(@NotNull MultiHostInjector injector);

  public abstract String getUnescapedText(@NotNull PsiElement injectedNode);

  @NotNull
  public abstract List<TextRange> intersectWithAllEditableFragments(@NotNull PsiFile injectedPsi, @NotNull TextRange rangeToEdit);

  public abstract boolean isInjectedFragment(@NotNull PsiFile file);

  @Nullable
  public abstract PsiElement findInjectedElementAt(@NotNull PsiFile hostFile, int hostDocumentOffset);

  @Nullable
  public abstract List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull PsiElement host);

  public abstract void dropFileCaches(@NotNull PsiFile file);

  public abstract PsiFile getTopLevelFile(@NotNull PsiElement element);

  @NotNull
  public abstract List<DocumentWindow> getCachedInjectedDocuments(@NotNull PsiFile hostPsiFile);

  public abstract void startRunInjectors(@NotNull Document hostDocument, boolean synchronously);

  public abstract void enumerate(@NotNull PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor);
  public abstract void enumerateEx(@NotNull PsiElement host, @NotNull PsiFile containingFile, boolean probeUp, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor);
}
