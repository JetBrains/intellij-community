/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

/**
 *  @author dsl
 */
public interface OrderEntry extends Synthetic, Comparable<OrderEntry> {
  OrderEntry[] EMPTY_ARRAY = new OrderEntry[0];

  /**
   * Returns list of root <code>VirtualFile</code>s of given type for this entry.
   * Those files should be traversed in order they are returned in.
   * Note that actual OrderEntry (as seen in UI) may contain also contain invalid roots.
   * If you want to get list of all roots, use <code>getURLs</code> method. <br>
   *
   * Note that list of roots is project dependent.
   *
   * @param type  required root type.
   * @return list of virtual files.
   * @see #getUrls(OrderRootType)
   */
  VirtualFile[] getFiles(OrderRootType type);

  /**
   * Returns list of roots of given type for this entry. To validate returned roots,
   * use <code>{@link com.intellij.openapi.vfs.VirtualFileManager#findFileByUrl(java.lang.String)}</code> <br>
   *
   * Note that list of roots is project-dependent.
   *
   * @return
   */
  String[] getUrls(OrderRootType rootType);

  /**
   * @return name of this OrderEntry to be shown to user.
   */
  String getPresentableName();

  /**
   * Checks whether this order entry is invalid for some reason. Note that entry being valid
   * does not necessarily mean that all its roots are valid.
   * @return true if entry is valid, false otherwise.
   */
  boolean isValid();

  Module getOwnerModule();

  <R> R accept(RootPolicy<R> policy, R initialValue);
}
