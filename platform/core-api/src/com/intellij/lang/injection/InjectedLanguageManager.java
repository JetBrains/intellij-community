/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.lang.injection;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public abstract class InjectedLanguageManager {

  public static InjectedLanguageManager getInstance(Project project) {
    return INSTANCE_CACHE.getValue(project);
  }

  protected static final NotNullLazyKey<InjectedLanguageManager, Project> INSTANCE_CACHE = ServiceManager.createLazyKey(InjectedLanguageManager.class);

  public static final Key<Boolean> FRANKENSTEIN_INJECTION = Key.create("FRANKENSTEIN_INJECTION");

  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull FileViewProvider provider);

  @Nullable
  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement element);

  @NotNull
  public abstract TextRange injectedToHost(@NotNull PsiElement injectedContext, @NotNull TextRange injectedTextRange);
  public abstract int injectedToHost(@NotNull PsiElement injectedContext, int injectedOffset);

  /**
   * @deprecated use {@link MultiHostInjector#MULTIHOST_INJECTOR_EP_NAME extension point} for production and
   * {@link #registerMultiHostInjector(MultiHostInjector, Disposable)} for tests
   */
  @Deprecated
  public abstract void registerMultiHostInjector(@NotNull MultiHostInjector injector);

  @TestOnly
  public abstract void registerMultiHostInjector(@NotNull MultiHostInjector injector, @NotNull Disposable parentDisposable);

  public abstract String getUnescapedText(@NotNull PsiElement injectedNode);

  @NotNull
  public abstract List<TextRange> intersectWithAllEditableFragments(@NotNull PsiFile injectedPsi, @NotNull TextRange rangeToEdit);

  public abstract boolean isInjectedFragment(@NotNull PsiFile file);

  /**
   * Finds PSI element in injected fragment (if any) at the given offset in the host file.<p/>
   * E.g. if you injected XML {@code "<xxx/>"} into Java string literal {@code "String s = "<xxx/>";"} and the caret is at {@code "xxx"} then
   * this method will return XmlToken(XML_TAG_START) with the text {@code "xxx"}.<br/>
   * Invocation of this method on uncommitted {@code hostFile} can lead to unexpected results, including throwing an exception!
   */
  @Nullable
  public abstract PsiElement findInjectedElementAt(@NotNull PsiFile hostFile, int hostDocumentOffset);

  @Nullable
  public abstract List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull PsiElement host);

  public abstract void dropFileCaches(@NotNull PsiFile file);

  public abstract PsiFile getTopLevelFile(@NotNull PsiElement element);

  @NotNull
  public abstract List<DocumentWindow> getCachedInjectedDocumentsInRange(@NotNull PsiFile hostPsiFile, @NotNull TextRange range);

  public abstract void startRunInjectorsInRange(@NotNull Document hostDocument,
                                                @NotNull TextRange range,
                                                boolean synchronously);

  public abstract void enumerate(@NotNull PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor);
  public abstract void enumerateEx(@NotNull PsiElement host, @NotNull PsiFile containingFile, boolean probeUp, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor);

  /**
   * @return the ranges in this document window that correspond to prefix/suffix injected text fragments and thus can't be edited and are not visible in the editor.
   */
  @NotNull
  public abstract List<TextRange> getNonEditableFragments(@NotNull DocumentWindow window);

  /**
   * This method can be invoked on an uncommitted document, before performing commit and using other methods here 
   * (which don't work for uncommitted document).
   */
  public abstract boolean mightHaveInjectedFragmentAtOffset(@NotNull Document hostDocument, int hostOffset);
  @NotNull
  public abstract DocumentWindow freezeWindow(@NotNull DocumentWindow document);
}
