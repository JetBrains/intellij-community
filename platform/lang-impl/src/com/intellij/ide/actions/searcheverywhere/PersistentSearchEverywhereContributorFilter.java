// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.util.TypeVisibilityStateHolder;
import com.intellij.ide.util.gotoByName.ChooseByNameFilterConfiguration;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.Icon;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class PersistentSearchEverywhereContributorFilter<T> {

  private final AtomicReference<TypeVisibilityStateHolder<? super T>> myVisibilityStateHolder;
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
    myVisibilityStateHolder = new AtomicReference<>(visibilityStateHolder);
    myTextExtractor = textExtractor;
    myIconExtractor = iconExtractor;
  }

  public List<T> getAllElements() {
    return myElements;
  }

  public @Unmodifiable List<T> getSelectedElements() {
    return ContainerUtil.filter(myElements, type -> myVisibilityStateHolder.get().isVisible(type));
  }

  public boolean isSelected(T element) {
    return myVisibilityStateHolder.get().isVisible(element);
  }

  public void setSelected(T element, boolean selected) {
    myVisibilityStateHolder.get().setVisible(element, selected);
  }

  /**
   * Keeps the selection of this filter in memory from now on, so {@link #setSelected} no longer changes the saved state.
   * The in-memory selection starts as a copy of the saved state.
   */
  @ApiStatus.Internal
  public void keepSelectionInMemory() {
    myVisibilityStateHolder.updateAndGet(
      holder -> holder instanceof InMemoryVisibilityState ? holder : new InMemoryVisibilityState<>(myElements, holder));
  }

  public void setSelectedToAllElements(boolean selected) {
    myElements.forEach(element -> setSelected(element, selected));
  }

  public @Nls String getElementText(T element) {
    return myTextExtractor.apply(element);
  }

  public Icon getElementIcon(T element) {
    return myIconExtractor.apply(element);
  }

  private static final class InMemoryVisibilityState<T> implements TypeVisibilityStateHolder<T> {
    private final Set<T> myHidden = ConcurrentHashMap.newKeySet();

    InMemoryVisibilityState(List<T> elements, TypeVisibilityStateHolder<? super T> savedState) {
      for (T element : elements) {
        if (!savedState.isVisible(element)) myHidden.add(element);
      }
    }

    @Override
    public void setVisible(T type, boolean value) {
      if (value) {
        myHidden.remove(type);
      }
      else {
        myHidden.add(type);
      }
    }

    @Override
    public boolean isVisible(T type) {
      return !myHidden.contains(type);
    }
  }
}
