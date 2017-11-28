/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author peter
 */
class DirectoryPathMatcher {
  @NotNull private final GotoFileModel myModel;
  @Nullable private final List<Pair<VirtualFile, String>> myFiles;
  @NotNull private final String myPattern;

  private DirectoryPathMatcher(@NotNull GotoFileModel model, @Nullable List<Pair<VirtualFile, String>> files, @NotNull String pattern) {
    myModel = model;
    myFiles = files;
    myPattern = pattern;
  }

  @Nullable
  static DirectoryPathMatcher root(@NotNull GotoFileModel model, @NotNull String pattern) {
    DirectoryPathMatcher matcher = new DirectoryPathMatcher(model, null, "");
    for (int i = 0; i < pattern.length(); i++) {
      matcher = matcher.appendChar(pattern.charAt(i));
      if (matcher == null) return null;
    }
    return matcher;
  }

  @Nullable
  DirectoryPathMatcher appendChar(char c) {
    String nextPattern = myPattern + c;
    if (c == '*' || c == '/' || c == ' ') return new DirectoryPathMatcher(myModel, myFiles, nextPattern);

    List<Pair<VirtualFile, String>> files = myFiles != null ? myFiles : getProjectRoots(myModel);

    List<Pair<VirtualFile, String>> nextRoots = new ArrayList<>();
    MinusculeMatcher matcher = GotoFileItemProvider.getQualifiedNameMatcher(nextPattern);
    for (Pair<VirtualFile, String> pair : files) {
      if (containsChar(pair.second, c) && matcher.matches(pair.second)) {
        nextRoots.add(pair);
      } else {
        processSubdirectoriesContaining(pair.first, c, sub -> {
          String fullName = myModel.getFullName(sub);
          if (fullName == null)  return false;
          fullName = FileUtil.toSystemIndependentName(fullName);
          if (matcher.matches(fullName)) {
            nextRoots.add(Pair.create(sub, fullName));
            return true;
          }
          return false;
        });
      }
    }

    return nextRoots.isEmpty() ? null : new DirectoryPathMatcher(myModel, nextRoots, nextPattern);
  }
  
  @NotNull
  GlobalSearchScope narrowDown(@NotNull GlobalSearchScope fileSearchScope) {
    if (myFiles == null) return fileSearchScope;

    VirtualFile[] array = ContainerUtil.map2Array(myFiles, VirtualFile.class, p -> p.first);
    return GlobalSearchScopesCore.directoriesScope(myModel.getProject(), true, array).intersectWith(fileSearchScope);

  }

  private void processSubdirectoriesContaining(VirtualFile root, char c, Processor<VirtualFile> consumer) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(myModel.getProject());
    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Object>() {

      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory() || !scope.contains(file)) return false;

        String name = file.getName();
        if (containsChar(name, c) && consumer.process(file)) {
          return false;
        }
        return true;
      }

      @Nullable
      @Override
      public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
        return file instanceof NewVirtualFile ? ((NewVirtualFile)file).getCachedChildren() : null;
      }
    });
  }

  private static boolean containsChar(String name, char c) {
    return StringUtil.indexOfIgnoreCase(name, c, 0) >= 0;
  }

  @NotNull
  private static List<Pair<VirtualFile, String>> getProjectRoots(GotoFileModel model) {
    Set<VirtualFile> roots = new HashSet<>();
    for (Module module : ModuleManager.getInstance(model.getProject()).getModules()) {
      Collections.addAll(roots, ModuleRootManager.getInstance(module).getContentRoots());
      for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (entry instanceof LibraryOrSdkOrderEntry) {
          Collections.addAll(roots, entry.getFiles(OrderRootType.CLASSES));
          Collections.addAll(roots, entry.getFiles(OrderRootType.SOURCES));
        }
      }
    }
    return roots.stream()
      .map(root -> {
        VirtualFile top = model.getTopLevelRoot(root);
        return top != null ? top : root;
      })
      .distinct()
      .map(r -> Pair.create(r, StringUtil.notNullize(model.getFullName(r))))
      .collect(Collectors.toList());
  }

}
