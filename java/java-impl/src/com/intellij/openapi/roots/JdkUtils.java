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
package com.intellij.openapi.roots;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JdkUtils {
  @Nullable
  public static Sdk getJdkForElement(PsiElement element) {
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    if (virtualFile == null) return null;
    final List<OrderEntry> entries = ProjectRootManager.getInstance(element.getProject()).getFileIndex().getOrderEntriesForFile(virtualFile);
    Sdk jdk = null;
    for (OrderEntry orderEntry : entries) {
      if (orderEntry instanceof JdkOrderEntry) {
        jdk = ((JdkOrderEntry)orderEntry).getJdk();
        if (jdk != null) break;
      }
    }
    if (jdk == null) return null;
    return jdk;
  }
}
