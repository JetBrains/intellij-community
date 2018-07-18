// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import javax.swing.*;
import java.util.List;

public interface SearchEverywhereContributorFilter<T> {

  List<T> getAllElements();

  List<T> getSelectedElements();

  boolean isSelected(T element);

  void setSelected(T element, boolean selected);

  String getElementText(T element);

  Icon getElementIcon(T element);
}
