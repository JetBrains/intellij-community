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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.io.File;
import java.util.StringTokenizer;

class LibraryRootsTreeSpeedSearch extends TreeSpeedSearch {
  private LibraryRootsTreeSpeedSearch(final Tree tree) {
    super(tree, (Void)null);
  }

  @Contract("_ -> new")
  static @NotNull LibraryRootsTreeSpeedSearch installOn(final Tree tree) {
    LibraryRootsTreeSpeedSearch search = new LibraryRootsTreeSpeedSearch(tree);
    search.setupListeners();
    return search;
  }

  @Override
  public boolean isMatchingElement(Object element, String pattern) {
    Object userObject = ((DefaultMutableTreeNode)((TreePath)element).getLastPathComponent()).getUserObject();
    if (userObject instanceof ItemElement) {
      String str = getElementText(element);
      if (str == null) {
        return false;
      }
      if (!hasCapitals(pattern)) { // be case-sensitive only if user types capitals
        str = StringUtil.toLowerCase(str);
      }
      if (pattern.contains(File.separator)) {
        return compare(str,pattern);
      }
      final StringTokenizer tokenizer = new StringTokenizer(str, File.separator);
      while (tokenizer.hasMoreTokens()) {
        final String token = tokenizer.nextToken();
        if (compare(token,pattern)) {
          return true;
        }
      }
      return false;
    }
    else {
      return super.isMatchingElement(element, pattern);
    }
  }

  private static boolean hasCapitals(String str) {
    for (int idx = 0; idx < str.length(); idx++) {
      if (Character.isUpperCase(str.charAt(idx))) {
        return true;
      }
    }
    return false;
  }
}
