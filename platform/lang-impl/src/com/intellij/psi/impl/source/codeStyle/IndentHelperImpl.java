/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

public class IndentHelperImpl extends IndentHelper {
  //----------------------------------------------------------------------------------------------------

  public static final int INDENT_FACTOR = 10000; // "indent" is indent_level * INDENT_FACTOR + spaces

  @Override
  public int getIndent(Project project, FileType fileType, ASTNode element) {
    return getIndent(project, fileType, element, false);
  }

  @Override
  public int getIndent(Project project, FileType fileType, final ASTNode element, boolean includeNonSpace) {
    return getIndentInner(project, fileType, element, includeNonSpace, 0);
  }
  
  public static final int TOO_BIG_WALK_THRESHOLD = 450;

  protected int getIndentInner(Project project, FileType fileType, final ASTNode element, boolean includeNonSpace, int recursionLevel) {
    if (recursionLevel > TOO_BIG_WALK_THRESHOLD) return 0;

    if (element.getTreePrev() != null) {
      ASTNode prev = element.getTreePrev();
      ASTNode lastCompositePrev;
      while (prev instanceof CompositeElement && !TreeUtil.isStrongWhitespaceHolder(prev.getElementType())) {
        lastCompositePrev = prev;
        prev = prev.getLastChildNode();
        if (prev == null) { // element.prev is "empty composite"
          return getIndentInner(project, fileType, lastCompositePrev, includeNonSpace, recursionLevel + 1);
        }
      }

      String text = prev.getText();
      int index = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));

      if (index >= 0) {
        return getIndent(project, fileType, text.substring(index + 1), includeNonSpace);
      }

      if (includeNonSpace) {
        return getIndentInner(project, fileType, prev, includeNonSpace, recursionLevel + 1) + getIndent(project, fileType, text, includeNonSpace);
      }


      ASTNode parent = prev.getTreeParent();
      ASTNode child = prev;
      while (parent != null) {
        if (child.getTreePrev() != null) break;
        child = parent;
        parent = parent.getTreeParent();
      }

      if (parent == null) {
        return getIndent(project, fileType, text, includeNonSpace);
      }
      else {
        return getIndentInner(project, fileType, prev, includeNonSpace, recursionLevel + 1);
      }
    }
    else {
      if (element.getTreeParent() == null) {
        return 0;
      }
      return getIndentInner(project, fileType, element.getTreeParent(), includeNonSpace, recursionLevel + 1);
    }
  }

  /**
   * @deprecated Use {@link #fillIndent(CommonCodeStyleSettings.IndentOptions, int)} instead.
   */
  @Deprecated
  public static String fillIndent(Project project,  FileType fileType, int indent) {
    return fillIndent(CodeStyleSettingsManager.getSettings(project).getIndentOptions(fileType), indent);
  }

  public static String fillIndent(@NotNull CommonCodeStyleSettings.IndentOptions indentOptions, int indent) {
    int indentLevel = (indent + INDENT_FACTOR / 2) / INDENT_FACTOR;
    int spaceCount = indent - indentLevel * INDENT_FACTOR;
    int indentLevelSize = indentLevel * indentOptions.INDENT_SIZE;
    int totalSize = indentLevelSize + spaceCount;

    StringBuilder buffer = new StringBuilder();
    if (indentOptions.USE_TAB_CHARACTER) {
      if (indentOptions.SMART_TABS) {
        int tabCount = indentLevelSize / indentOptions.TAB_SIZE;
        int leftSpaces = indentLevelSize - tabCount * indentOptions.TAB_SIZE;
        for (int i = 0; i < tabCount; i++) {
          buffer.append('\t');
        }
        for (int i = 0; i < leftSpaces + spaceCount; i++) {
          buffer.append(' ');
        }
      }
      else {
        int size = totalSize;
        while (size > 0) {
          if (size >= indentOptions.TAB_SIZE) {
            buffer.append('\t');
            size -= indentOptions.TAB_SIZE;
          }
          else {
            buffer.append(' ');
            size--;
          }
        }
      }
    }
    else {
      for (int i = 0; i < totalSize; i++) {
        buffer.append(' ');
      }
    }

    return buffer.toString();
  }

  public static int getIndent(Project project, FileType fileType, String text, boolean includeNonSpace) {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    int i;
    for (i = text.length() - 1; i >= 0; i--) {
      char c = text.charAt(i);
      if (c == '\n' || c == '\r') break;
    }
    i++;

    int spaceCount = 0;
    int tabCount = 0;
    for (int j = i; j < text.length(); j++) {
      char c = text.charAt(j);
      if (c != '\t') {
        if (!includeNonSpace && c != ' ') break;
        spaceCount++;
      }
      else {
        tabCount++;
      }
    }

    if (tabCount == 0) return spaceCount;

    int tabSize = settings.getTabSize(fileType);
    int indentSize = settings.getIndentSize(fileType);
    if (indentSize <= 0) {
      indentSize = 1;
    }
    int indentLevel = tabCount * tabSize / indentSize;
    return indentLevel * INDENT_FACTOR + spaceCount;
  }
}
