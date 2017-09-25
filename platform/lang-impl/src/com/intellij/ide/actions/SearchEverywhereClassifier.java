/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Philipp Smorygo
 */
public interface SearchEverywhereClassifier {
  class EP_Manager {
    private EP_Manager() {}

    public static boolean isClass(@Nullable Object o) {
      for (SearchEverywhereClassifier classifier : Extensions.getExtensions(SearchEverywhereClassifier.EP_NAME)) {
        if (classifier.isClass(o)) return true;
      }
      return false;
    }

    public static boolean isSymbol(@Nullable Object o) {
      for (SearchEverywhereClassifier classifier : Extensions.getExtensions(SearchEverywhereClassifier.EP_NAME)) {
        if (classifier.isSymbol(o)) return true;
      }
      return false;
    }

    @Nullable
    public static VirtualFile getVirtualFile(@NotNull Object o) {
      for (SearchEverywhereClassifier classifier : Extensions.getExtensions(SearchEverywhereClassifier.EP_NAME)) {
        VirtualFile virtualFile = classifier.getVirtualFile(o);
        if (virtualFile != null) return virtualFile;
      }
      return null;
    }

    @Nullable
    public static Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      for (SearchEverywhereClassifier classifier : Extensions.getExtensions(SearchEverywhereClassifier.EP_NAME)) {
        Component component = classifier.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (component != null) return component;
      }
      return null;
    }

    @Nullable
    public static GlobalSearchScope getProjectScope(@NotNull Project project) {
      for (SearchEverywhereClassifier classifier : Extensions.getExtensions(SearchEverywhereClassifier.EP_NAME)) {
        GlobalSearchScope scope = classifier.getProjectScope(project);
        if (scope != null) return scope;
      }
      return null;
    }
  }

  ExtensionPointName<SearchEverywhereClassifier> EP_NAME = ExtensionPointName.create("com.intellij.searchEverywhereClassifier");

  boolean isClass(@Nullable Object o);

  boolean isSymbol(@Nullable Object o);

  @Nullable
  VirtualFile getVirtualFile(@NotNull Object o);

  @Nullable
  Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus);

  @Nullable
  default GlobalSearchScope getProjectScope(@NotNull Project project) { return null; }
}
