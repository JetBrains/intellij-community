/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.NotNullFactory;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class RelatedItemLineMarkerInfo<T extends PsiElement> extends MergeableLineMarkerInfo<T> {
  private final NotNullLazyValue<? extends Collection<? extends GotoRelatedItem>> myTargets;

  /**
   * @deprecated Use {@link #RelatedItemLineMarkerInfo(PsiElement, TextRange, Icon, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment, NotNullFactory)} instead
   */
  @Deprecated
  public RelatedItemLineMarkerInfo(@NotNull T element, @NotNull TextRange range, Icon icon, int updatePass,
                                   @Nullable Function<? super T, String> tooltipProvider,
                                   @Nullable GutterIconNavigationHandler<T> navHandler,
                                   @NotNull GutterIconRenderer.Alignment alignment,
                                   @NotNull NotNullLazyValue<? extends Collection<? extends GotoRelatedItem>> targets) {
    super(element, range, icon, tooltipProvider, navHandler, alignment);
    myTargets = targets;
  }

  /**
   * @deprecated Use {@link #RelatedItemLineMarkerInfo(PsiElement, TextRange, Icon, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment, NotNullFactory)} instead
   */
  @Deprecated(forRemoval = true)
  public RelatedItemLineMarkerInfo(@NotNull T element, @NotNull TextRange range, Icon icon, int updatePass,
                                   @Nullable Function<? super T, String> tooltipProvider,
                                   @Nullable GutterIconNavigationHandler<T> navHandler,
                                   @NotNull GutterIconRenderer.Alignment alignment,
                                   @NotNull final Collection<? extends GotoRelatedItem> targets) {
    this(element, range, icon, tooltipProvider, navHandler, alignment, ()->targets);
  }

  public RelatedItemLineMarkerInfo(@NotNull T element, @NotNull TextRange range, Icon icon,
                                   @Nullable Function<? super T, String> tooltipProvider,
                                   @Nullable GutterIconNavigationHandler<T> navHandler,
                                   @NotNull GutterIconRenderer.Alignment alignment,
                                   @NotNull NotNullFactory<? extends Collection<? extends GotoRelatedItem>> targets) {
    super(element, range, icon, tooltipProvider, navHandler, alignment);
    myTargets = NotNullLazyValue.createValue(targets);
  }

  public RelatedItemLineMarkerInfo(@NotNull T element, @NotNull TextRange range, Icon icon,
                                   @Nullable Function<? super T, String> tooltipProvider,
                                   @Nullable Function<PsiElement, @Nls(capitalization = Nls.Capitalization.Title) String> presentationProvider,
                                   @Nullable GutterIconNavigationHandler<T> navHandler,
                                   @NotNull GutterIconRenderer.Alignment alignment,
                                   @NotNull NotNullFactory<? extends Collection<? extends GotoRelatedItem>> targets,
                                   @NotNull Supplier<@NotNull @Nls String> accessibleNameProvider) {
    super(element, range, icon, tooltipProvider, presentationProvider, navHandler, alignment, accessibleNameProvider);
    myTargets = NotNullLazyValue.createValue(targets);
  }

  @NotNull
  public Collection<? extends GotoRelatedItem> createGotoRelatedItems() {
    return myTargets.getValue();
  }

  @Override
  public GutterIconRenderer createGutterRenderer() {
    if (myIcon == null) return null;
    return new RelatedItemLineMarkerGutterIconRenderer<>(this);
  }

  @Override
  public boolean canMergeWith(@NotNull MergeableLineMarkerInfo<?> info) {
    return myIcon == info.myIcon;
  }

  @Override
  public Icon getCommonIcon(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
    return myIcon;
  }

  private static class RelatedItemLineMarkerGutterIconRenderer<T extends PsiElement> extends LineMarkerGutterIconRenderer<T> {
    RelatedItemLineMarkerGutterIconRenderer(final RelatedItemLineMarkerInfo<T> markerInfo) {
      super(markerInfo);
    }

    @Override
    protected boolean looksTheSameAs(@NotNull LineMarkerGutterIconRenderer<?> renderer) {
      if (!(renderer instanceof RelatedItemLineMarkerGutterIconRenderer) || !super.looksTheSameAs(renderer)) {
        return false;
      }

      final RelatedItemLineMarkerInfo<?> markerInfo = (RelatedItemLineMarkerInfo<?>)getLineMarkerInfo();
      final RelatedItemLineMarkerInfo<?> otherInfo = (RelatedItemLineMarkerInfo<?>)renderer.getLineMarkerInfo();
      return markerInfo.myTargets.equals(otherInfo.myTargets);
    }
  }
}
