/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.JavaUtilForVfs;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.DetectedSourceRootsDialog;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * This utility class contains utility methods for selecting paths.
 *
 * @author Constantine.Plotnikov
 */
public class PathUIUtils {
  /** a private constructor */
  private PathUIUtils() {}

  /**
   * This method takes a candidates for the project root, then scans the candidates and
   * if multiple candidates or non root source directories are found whithin some
   * directories, it shows a dialog that allows selecting or deselecting them.
   * @param parent a parent parent or project
   * @param rootCandidates a candidates for roots
   * @return a array of source folders or empty array if non was selected or dialog was canceled.
   */
  public static VirtualFile[] scandAndSelectDetectedJavaSourceRoots(Object parent, final VirtualFile[] rootCandidates) {
    final Set<VirtualFile> result = new HashSet<VirtualFile>();
    final Map<VirtualFile, List<VirtualFile>> detectedRootsMap = new LinkedHashMap<VirtualFile, List<VirtualFile>>();
    // scan for roots
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        for (final VirtualFile candidate : rootCandidates) {
          List<VirtualFile> detectedRoots = JavaUtilForVfs.suggestRoots(candidate);
          if (!detectedRoots.isEmpty() && (detectedRoots.size() > 1 || detectedRoots.get(0) != candidate)) {
            detectedRootsMap.put(candidate, detectedRoots);
          } else {
            result.add(candidate);
          }
        }
      }
    }, "Scanning for source roots", true, null);
    if(!detectedRootsMap.isEmpty()) {
      DetectedSourceRootsDialog dlg = parent instanceof Component ?
                                      new DetectedSourceRootsDialog((Component)parent, detectedRootsMap) :
                                      new DetectedSourceRootsDialog((Project)parent, detectedRootsMap);
      dlg.show();
      if (dlg.isOK()) {
        result.addAll(dlg.getChosenRoots());
      }
      else {
        // the empty result means that the entire root adding process will be cancelled.
        result.clear();
      }
    }
    return VfsUtil.toVirtualFileArray(result);
  }
}
