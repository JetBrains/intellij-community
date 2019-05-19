// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.libraries.ui.RootDetector;
import com.intellij.openapi.roots.libraries.ui.impl.LibraryRootsDetectorImpl;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This utility class contains utility methods for selecting paths.
 *
 * @author Constantine.Plotnikov
 */
public class LibrarySourceRootDetectorUtil {
  public static final ExtensionPointName<RootDetector> JAVA_SOURCE_ROOT_DETECTOR = ExtensionPointName.create("com.intellij.library.javaSourceRootDetector");

  protected LibrarySourceRootDetectorUtil() {
  }

  /**
   * This method takes a candidates for the project root, then scans the candidates and
   * if multiple candidates or non root source directories are found within some
   * directories, it shows a dialog that allows selecting or deselecting them.
   * @param rootCandidates a candidates for roots
   * @return a array of source folders or empty array if non was selected or dialog was canceled.
   */
  public static VirtualFile[] scanAndSelectDetectedJavaSourceRoots(Component parentComponent, final VirtualFile[] rootCandidates) {
    final List<OrderRoot> orderRoots = RootDetectionUtil.detectRoots(Arrays.asList(rootCandidates), parentComponent, null,
                                                                     new LibraryRootsDetectorImpl(JAVA_SOURCE_ROOT_DETECTOR.getExtensionList()),
                                                                     new OrderRootType[] {OrderRootType.SOURCES});
    final List<VirtualFile> result = new ArrayList<>();
    for (OrderRoot root : orderRoots) {
      result.add(root.getFile());
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }
}
