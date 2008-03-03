/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util.proximity;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.JdkOrderEntry;

import java.util.List;

/**
 * @author peter
*/
public class SdkOrLibraryWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    final VirtualFile file = PsiUtilBase.getVirtualFile(element);
    if (file != null) {
      List<OrderEntry> orderEntries = ProjectRootManager.getInstance(location.getProject()).getFileIndex().getOrderEntriesForFile(file);
      if (!orderEntries.isEmpty() && orderEntries.get(0) instanceof JdkOrderEntry) {
        return true;
      }
    }
    return false;
  }
}
