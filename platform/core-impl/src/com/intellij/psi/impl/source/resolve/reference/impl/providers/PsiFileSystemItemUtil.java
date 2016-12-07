/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PsiFileSystemItemUtil {
  @Nullable
  static PsiFileSystemItem getCommonAncestor(PsiFileSystemItem file1, PsiFileSystemItem file2) {
    if (file1 == file2) return file1;

    int depth1 = getDepth(file1);
    int depth2 = getDepth(file2);

    PsiFileSystemItem parent1 = file1;
    PsiFileSystemItem parent2 = file2;
    while(depth1 > depth2 && parent1 != null) {
      parent1 = parent1.getParent();
      depth1--;
    }
    while(depth2 > depth1 && parent2 != null) {
      parent2 = parent2.getParent();
      depth2--;
    }
    while(parent1 != null && parent2 != null && !parent1.equals(parent2)) {
      parent1 = parent1.getParent();
      parent2 = parent2.getParent();
    }
    return parent1;
  }

  private static int getDepth(PsiFileSystemItem file) {
    int depth = 0;
    while (file != null) {
      depth++;
      file = file.getParent();
    }
    return depth;
  }

  @NotNull
  public static String getNotNullRelativePath(PsiFileSystemItem src, PsiFileSystemItem dst) throws IncorrectOperationException {
    final String s = getRelativePath(src, dst);
    if (s == null) throw new IncorrectOperationException("Cannot find path between files; src = " + src.getVirtualFile().getPresentableUrl() + "; dst = " + dst.getVirtualFile().getPresentableUrl());
    return s;
  }

  @Nullable
  public static String getRelativePath(PsiFileSystemItem src, PsiFileSystemItem dst) {
    final PsiFileSystemItem commonAncestor = getCommonAncestor(src, dst);

    if (commonAncestor != null) {
      StringBuilder buffer = new StringBuilder();
      if (!src.equals(commonAncestor)) {
        while (!commonAncestor.equals(src.getParent())) {
          buffer.append("..").append('/');
          src = src.getParent();
          assert src != null;
        }
      }
      buffer.append(getRelativePathFromAncestor(dst, commonAncestor));
      return buffer.toString();
    }

    return null;
  }

  @Nullable
  public static String getRelativePathFromAncestor(PsiFileSystemItem file, PsiFileSystemItem ancestor) {
    int length = 0;
    PsiFileSystemItem parent = file;

    while (true) {
      if (parent == null) return null;
      if (parent.equals(ancestor)) break;
      if (length > 0) {
        length++;
      }
      String name = parent.getName();
      if (name == null) {
        throw new AssertionError("Null name for " + parent + " of " + parent.getClass());
      }
      length += name.length();
      parent = parent.getParent();
    }

    char[] chars = new char[length];
    int index = chars.length;
    parent = file;

    while (true) {
      if (parent.equals(ancestor)) break;
      if (index < length) {
        chars[--index] = '/';
      }
      String name = parent.getName();
      for (int i = name.length() - 1; i >= 0; i--) {
        chars[--index] = name.charAt(i);
      }
      parent = parent.getParent();
    }
    return StringFactory.createShared(chars);
  }
}
