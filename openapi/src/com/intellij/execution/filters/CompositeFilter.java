/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.filters;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CompositeFilter implements Filter {
  private final List myFilters = new ArrayList();

  public Result applyFilter(final String line, final int entireLength) {
    final Iterator itr = myFilters.iterator();
    while (itr.hasNext()) {
      final Result info = (((Filter)itr.next()).applyFilter(line, entireLength));
      if (info != null) {
        return info;
      }
    }

    return null;
  }

  public void addFilter(final Filter filter) {
    myFilters.add(filter);
  }
}