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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.util.ArrayUtil;

/**
 * @author peter
*/
public class OpenedInEditorWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    final VirtualFile virtualFile = PsiUtilBase.getVirtualFile(element);
    return virtualFile != null && ArrayUtil.find(FileEditorManager.getInstance(location.getProject()).getOpenFiles(), virtualFile) != -1;
  }
}
