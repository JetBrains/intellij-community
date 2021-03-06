// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentMap;

import static com.intellij.psi.impl.PsiManagerImpl.ANY_PSI_CHANGE_TOPIC;

/**
 * @author Eugene Zhuravlev
 */
public final class TreeViewUtil {
  private static final int SUBPACKAGE_LIMIT = 2;
  private static final Key<ConcurrentMap<PsiPackage,Boolean>> SHOULD_ABBREV_PACK_KEY = Key.create("PACK_ABBREV_CACHE");

  private static boolean shouldAbbreviateName(PsiPackage aPackage) {
    final Project project = aPackage.getProject();
    ConcurrentMap<PsiPackage, Boolean> map = project.getUserData(SHOULD_ABBREV_PACK_KEY);
    if (map == null) {
      final ConcurrentMap<PsiPackage, Boolean> newMap = ContainerUtil.createConcurrentWeakMap();
      map = ((UserDataHolderEx)project).putUserDataIfAbsent(SHOULD_ABBREV_PACK_KEY, newMap);
      if (map == newMap) {
        project.getMessageBus().connect().subscribe(ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
          @Override
          public void beforePsiChanged(boolean isPhysical) {
            if (isPhysical) newMap.clear();
          }
        });
      }
    }

    Boolean ret = map.get(aPackage);
    if (ret != null) return ret;
    ret = scanPackages(aPackage, 1);
    map.put(aPackage, ret);
    return ret;
  }

  private static boolean scanPackages(@NotNull PsiPackage p, int packageNameOccurrencesFound) {
    final PsiPackage[] subPackages = p.getSubPackages();
    packageNameOccurrencesFound += subPackages.length;
    if (packageNameOccurrencesFound > SUBPACKAGE_LIMIT) {
      return true;
    }
    for (PsiPackage subPackage : subPackages) {
      if (scanPackages(subPackage, packageNameOccurrencesFound)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static String calcAbbreviatedPackageFQName(@NotNull PsiPackage aPackage) {
    final StringBuilder name = new StringBuilder(aPackage.getName());
    for (PsiPackage parentPackage = aPackage.getParentPackage(); parentPackage != null; parentPackage = parentPackage.getParentPackage()) {
      final String packageName = parentPackage.getName();
      if (packageName == null || packageName.isEmpty()) {
        break; // reached default package
      }
      name.insert(0, ".");
      if (packageName.length() > 2 && shouldAbbreviateName(parentPackage)) {
        name.insert(0, packageName.charAt(0));
      }
      else {
        name.insert(0, packageName);
      }
    }
    return name.toString();
  }

  /**
   * a directory is considered "empty" if it has at least one child and all its children are only directories
   *
   * @param strictlyEmpty if true, the package is considered empty if it has only 1 child and this child  is a directory
   *                      otherwise the package is considered as empty if all direct children that it has are directories
   * @param filter        if returns false for some element, that elements is not counted
   */
  public static boolean isEmptyMiddlePackage(@NotNull PsiDirectory dir,
                                             boolean strictlyEmpty,
                                             @Nullable PsiFileSystemItemFilter filter) {
    final VirtualFile[] files = dir.getVirtualFile().getChildren();
    if (files.length == 0) {
      return false;
    }
    PsiManager manager = dir.getManager();
    int subpackagesCount = 0;
    int directoriesCount = 0;
    for (VirtualFile file : files) {
      if (FileTypeManager.getInstance().isFileIgnored(file)) continue;
      if (!file.isDirectory()) {
        if (filter == null) return false;
        PsiFile childFile = manager.findFile(file);
        if (childFile != null && filter.shouldShow(childFile)) return false;
      }
      PsiDirectory childDir = manager.findDirectory(file);
      if (childDir != null && (filter == null || filter.shouldShow(childDir))) {
        directoriesCount++;
        if (strictlyEmpty && directoriesCount > 1) return false;
        if (JavaDirectoryService.getInstance().getPackage(childDir) != null) {
          subpackagesCount++;
        }
      }
    }
    if (strictlyEmpty) {
      return directoriesCount == subpackagesCount && directoriesCount == 1;
    }
    return directoriesCount == subpackagesCount && directoriesCount > 0;
  }
}
