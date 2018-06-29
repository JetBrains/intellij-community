// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SearchEverywhereContributorFilterImpl<T> implements SearchEverywhereContributorFilter<T> {

  private final Map<T, Boolean> myElementsMap = new LinkedHashMap<>();
  private final Function<? super T, String> myTextExtractor;
  private final Function<? super T, ? extends Icon> myIconExtractor;

  public SearchEverywhereContributorFilterImpl(@NotNull List<T> elements,
                                               @NotNull Function<? super T, String> textExtractor,
                                               @NotNull Function<? super T, ? extends Icon> iconExtractor) {
    myTextExtractor = textExtractor;
    myIconExtractor = iconExtractor;
    elements.forEach(e -> myElementsMap.put(e, true));
  }

  @Override
  public List<T> getAllElements() {
    return new ArrayList<>(myElementsMap.keySet());
  }

  @Override
  public List<T> getSelectedElements() {
    return myElementsMap.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
  }

  @Override
  public boolean isSelected(T element) {
    return myElementsMap.get(element);
  }

  @Override
  public void setSelected(T element, boolean selected) {
    if (myElementsMap.containsKey(element)) {
      myElementsMap.put(element, selected);
    }
  }

  @Override
  public String getElementText(T element) {
    return myTextExtractor.apply(element);
  }

  @Override
  public Icon getElementIcon(T element) {
    return myIconExtractor.apply(element);
  }
}
