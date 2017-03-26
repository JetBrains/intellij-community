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
package com.intellij.psi.presentation.java;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.PsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaModulePresentationProvider implements ItemPresentationProvider<PsiJavaModule> {
  private static final Pattern JAR_NAME = Pattern.compile(".+/([^/]+\\.jar)!/.*");

  @Override
  public ItemPresentation getPresentation(@NotNull final PsiJavaModule item) {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return item.getName();
      }

      @Nullable
      @Override
      public String getLocationString() {
        VirtualFile file = PsiImplUtil.getModuleVirtualFile(item);
        FileIndexFacade index = FileIndexFacade.getInstance(item.getProject());
        if (index.isInLibraryClasses(file)) {
          Matcher matcher = JAR_NAME.matcher(file.getPath());
          if (matcher.find()) {
            return matcher.group(1);
          }
        }
        else if (index.isInSource(file)) {
          Module module = index.getModuleForFile(file);
          if (module != null) {
            return '[' + module.getName() + ']';
          }
        }
        return null;
      }

      @Nullable
      @Override
      public Icon getIcon(boolean unused) {
        return AllIcons.Nodes.JavaModule;
      }
    };
  }
}