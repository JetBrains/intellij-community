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
package com.intellij.navigation;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Provides items for "Navigate -> Related Symbol" action.
 * <p>
 * If related items are represented as icons on the gutter use {@link com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider}
 * to provide both line markers and 'goto related' targets
 *
 * @author Dmitry Avdeev
 */
public abstract class GotoRelatedProvider {

  public static final ExtensionPointName<GotoRelatedProvider> EP_NAME = ExtensionPointName.create("com.intellij.gotoRelatedProvider");

  @NotNull
  public List<? extends GotoRelatedItem> getItems(@NotNull PsiElement psiElement) {
    return Collections.emptyList();
  }

  @NotNull
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    return Collections.emptyList();
  }
}
