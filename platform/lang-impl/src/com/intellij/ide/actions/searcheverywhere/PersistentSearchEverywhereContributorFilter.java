// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.util.TypeVisibilityStateHolder;
import com.intellij.ide.util.gotoByName.ChooseByNameFilterConfiguration;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.function.Function;

public final class PersistentSearchEverywhereContributorFilter<T> {

  private final TypeVisibilityStateHolder<? super T> myVisibilityStateHolder;
  private final List<T> myElements;
  private final Function<? super T, @Nls String> myTextExtractor;
  private final Function<? super T, ? extends Icon> myIconExtractor;

  public PersistentSearchEverywhereContributorFilter(@NotNull List<T> elements,
                                                     @NotNull ChooseByNameFilterConfiguration<? super T> configuration,
                                                     Function<? super T, @Nls String> textExtractor,
                                                     Function<? super T, ? extends Icon> iconExtractor) {
    this(elements, (TypeVisibilityStateHolder<? super T>) configuration, textExtractor, iconExtractor);
  }

  public PersistentSearchEverywhereContributorFilter(@NotNull List<T> elements,
                                                     @NotNull TypeVisibilityStateHolder<? super T> visibilityStateHolder,
                                                     Function<? super T, @Nls String> textExtractor,
                                                     Function<? super T, ? extends Icon> iconExtractor) {
    myElements = elements;
    myVisibilityStateHolder = visibilityStateHolder;
    myTextExtractor = textExtractor;
    myIconExtractor = iconExtractor;
  }

  public List<T> getAllElements() {
    return myElements;
  }

  public List<T> getSelectedElements() {
    return ContainerUtil.filter(myElements, type -> myVisibilityStateHolder.isVisible(type));
  }

  public boolean isSelected(T element) {
    return myVisibilityStateHolder.isVisible(element);
  }

  public void setSelected(T element, boolean selected) {
    myVisibilityStateHolder.setVisible(element, selected);
  }

  public @Nls String getElementText(T element) {
    return myTextExtractor.apply(element);
  }

  public Icon getElementIcon(T element) {
    return myIconExtractor.apply(element);
  }
}
