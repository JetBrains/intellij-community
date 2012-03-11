/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries.ui.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.libraries.ui.RootDetector;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class RootDetectionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.libraryEditor.RootDetectionUtil");

  private RootDetectionUtil() {
  }

  @NotNull
  public static List<OrderRoot> detectRoots(@NotNull final Collection<VirtualFile> rootCandidates,
                                            @Nullable Component parentComponent,
                                            @Nullable Project project,
                                            @NotNull final List<? extends RootDetector> detectors,
                                            boolean allowUserToSelectRootTypeIfNothingIsDetected) {
    LOG.assertTrue(!detectors.isEmpty());
    final List<OrderRoot> result = new ArrayList<OrderRoot>();
    final List<SuggestedChildRootInfo> suggestedRoots = new ArrayList<SuggestedChildRootInfo>();
    new Task.Modal(project, "Scanning for Roots", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          for (RootDetector detector : detectors) {
            for (VirtualFile rootCandidate : rootCandidates) {
              final Collection<VirtualFile> roots = detector.detectRoots(rootCandidate, indicator);
              final VirtualFile first = ContainerUtil.getFirstItem(roots);
              if (first != null && roots.size() == 1 && first.equals(rootCandidate)) {
                result.add(new OrderRoot(first, detector.getRootType(), detector.isJarDirectory()));
              }
              else {
                for (VirtualFile root : roots) {
                  suggestedRoots.add(new SuggestedChildRootInfo(detector, rootCandidate, root));
                }
              }
            }
          }
        }
        catch (ProcessCanceledException ignored) {
        }
      }
    }.queue();

    if (!suggestedRoots.isEmpty()) {
      final DetectedRootsChooserDialog dialog = parentComponent != null
                                                ? new DetectedRootsChooserDialog(parentComponent, suggestedRoots)
                                                : new DetectedRootsChooserDialog(project, suggestedRoots);
      dialog.show();
      if (!dialog.isOK()) {
        return Collections.emptyList();
      }
      for (SuggestedChildRootInfo rootInfo : dialog.getChosenRoots()) {
        result
          .add(new OrderRoot(rootInfo.getSuggestedRoot(), rootInfo.getDetector().getRootType(), rootInfo.getDetector().isJarDirectory()));
      }
    }

    if (result.isEmpty() && allowUserToSelectRootTypeIfNothingIsDetected) {
      List<RootDetector> sortedDetectors = new ArrayList<RootDetector>(detectors);
      Collections.sort(sortedDetectors, new Comparator<RootDetector>() {
        @Override
        public int compare(final RootDetector o1, final RootDetector o2) {
          return o1.getPresentableRootTypeName().compareToIgnoreCase(o2.getPresentableRootTypeName());
        }
      });

      List<String> names = new ArrayList<String>();
      for (RootDetector detector : sortedDetectors) {
        names.add(detector.getPresentableRootTypeName());
      }
      final int i = Messages.showChooseDialog("Choose category for selected files:", "Attach Files",
                                              ArrayUtil.toStringArray(names), names.get(0), null);
      if (i != -1) {
        final RootDetector detector = sortedDetectors.get(i);
        for (VirtualFile candidate : rootCandidates) {
          result.add(new OrderRoot(candidate, detector.getRootType(), detector.isJarDirectory()));
        }
      }
    }
    return result;
  }
}
