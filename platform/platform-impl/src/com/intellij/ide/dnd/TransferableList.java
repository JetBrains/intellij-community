// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

abstract public class TransferableList<T> implements Transferable {
  private static final DataFlavor LIST_DATA_FLAVOR = new DataFlavor(List.class, "Transferable List");
  private static final DataFlavor ALL_HTML_DATA_FLAVOR = DataFlavor.allHtmlFlavor;
  private static final DataFlavor PART_HTML_DATA_FLAVOR = DataFlavor.fragmentHtmlFlavor;

  private final List<T> myList;

  @SafeVarargs
  public TransferableList(T @NotNull ... array) {
    this(Arrays.asList(array));
  }

  public TransferableList(List<? extends T> list) {
    myList = Collections.unmodifiableList(list);
  }

  @Override
  public DataFlavor @NotNull [] getTransferDataFlavors() {
    return new DataFlavor[]{
      ALL_HTML_DATA_FLAVOR,//DataFlavor.allHtmlFlavor,
      PART_HTML_DATA_FLAVOR,//DataFlavor.fragmentHtmlFlavor,
      DataFlavor.stringFlavor,
      LIST_DATA_FLAVOR};
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return ArrayUtil.contains(flavor, getTransferDataFlavors());
  }

  @Override
  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    if (flavor.equals(ALL_HTML_DATA_FLAVOR)) {
      return getHTML(myList, true);
    }
    if (flavor.equals(PART_HTML_DATA_FLAVOR)) {
      return getHTML(myList, false);
    }
    if (flavor.equals(DataFlavor.stringFlavor)) {
      return getText(myList);
    }
    if (flavor.equals(LIST_DATA_FLAVOR)) {
      return myList;
    }
    throw new UnsupportedFlavorException(flavor);
  }

  protected String getHTML(List<? extends T> list, boolean all) {
    StringBuilder sb = new StringBuilder();
    if (all) sb.append("<html><body>");
    sb.append("<ul>\n");
    for (T object : list) {
      sb.append("  <li>").append(toHTML(object)).append("</li>\n");
    }
    sb.append("</ul>");
    if (all) sb.append("</body></html>");
    return sb.toString();
  }

  protected String getText(List<? extends T> list) {
    if (list.size() == 1) {
      return toString(list.get(0));
    }
    StringBuilder sb = new StringBuilder();
    for (T object : list) {
      sb.append(toString(object)).append('\n');
    }
    return sb.toString();
  }

  protected String toHTML(T object) {
    return toString(object);
  }

  abstract protected String toString(T object);
}
