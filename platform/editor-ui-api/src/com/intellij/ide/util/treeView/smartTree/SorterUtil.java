// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView.smartTree;

import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public final class SorterUtil {
  private SorterUtil() {
  }

  public static @NotNull String getStringPresentation(Object object) {
    String result = null;
    if (object instanceof SortableTreeElement) {
      result = ((SortableTreeElement)object).getAlphaSortKey();
    }
    else if (object instanceof TreeElement) {
      result = ((TreeElement)object).getPresentation().getPresentableText();
    }
    else if (object instanceof Group) {
      result = ((Group)object).getPresentation().getPresentableText();
    }

    return result == null ? "" : result;
  }
}
