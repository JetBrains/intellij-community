/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class InjectedLanguageManager implements ProjectComponent {
  public static final ExtensionPointName<MultiHostInjector> MULTIHOST_INJECTOR_EP_NAME = ExtensionPointName.create("com.intellij.multiHostInjector");

  public static InjectedLanguageManager getInstance(Project project) {
    return project.getComponent(InjectedLanguageManager.class);
  }

  @Nullable
  public abstract PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement element);

  public abstract TextRange injectedToHost(@NotNull PsiElement element, @NotNull TextRange textRange);

  public abstract void registerMultiHostInjector(@NotNull MultiHostInjector injector);
  public abstract boolean unregisterMultiHostInjector(@NotNull MultiHostInjector injector);

  public abstract String getUnescapedText(@NotNull PsiElement injectedNode);
}
