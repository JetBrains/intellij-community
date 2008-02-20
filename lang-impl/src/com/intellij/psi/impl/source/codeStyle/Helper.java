package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;

public class Helper {
  private final CodeStyleSettings mySettings;
  private final FileType myFileType;

  protected Helper(FileType fileType, Project project) {
    mySettings = CodeStyleSettingsManager.getSettings(project);
    myFileType = fileType;
  }

  //----------------------------------------------------------------------------------------------------

  public static final int INDENT_FACTOR = 10000; // "indent" is indent_level * INDENT_FACTOR + spaces

  public int getIndent(ASTNode element) {
    return getIndent(element, false);
  }

  public int getIndent(final ASTNode element, boolean includeNonSpace) {
    return getIndentInner(element, includeNonSpace, 0);
  }
  
  protected static final int TOO_BIG_WALK_THRESHOULD = 450;

  protected int getIndentInner(final ASTNode element, boolean includeNonSpace, int recursionLevel) {
    if (recursionLevel > TOO_BIG_WALK_THRESHOULD) return 0;

    if (element.getTreePrev() != null) {
      ASTNode prev = element.getTreePrev();
      ASTNode lastCompositePrev;
      while (prev instanceof CompositeElement && !TreeUtil.isStrongWhitespaceHolder(prev.getElementType())) {
        lastCompositePrev = prev;
        prev = prev.getLastChildNode();
        if (prev == null) { // element.prev is "empty composite"
          return getIndentInner(lastCompositePrev, includeNonSpace, recursionLevel + 1);
        }
      }

      String text = prev.getText();
      int index = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));

      if (index >= 0) {
        return getIndent(text.substring(index + 1), includeNonSpace);
      }

      if (includeNonSpace) {
        return getIndentInner(prev, includeNonSpace, recursionLevel + 1) + getIndent(text, includeNonSpace);
      }


      ASTNode parent = prev.getTreeParent();
      ASTNode child = prev;
      while (parent != null) {
        if (child.getTreePrev() != null) break;
        child = parent;
        parent = parent.getTreeParent();
      }

      if (parent == null) {
        return getIndent(text, includeNonSpace);
      }
      else {
        return getIndentInner(prev, includeNonSpace, recursionLevel + 1);
      }
    }
    else {
      if (element.getTreeParent() == null) {
        return 0;
      }
      return getIndentInner(element.getTreeParent(), includeNonSpace, recursionLevel + 1);
    }
  }

  public String fillIndent(int indent) {
    int indentLevel = (indent + INDENT_FACTOR / 2) / INDENT_FACTOR;
    int spaceCount = indent - indentLevel * INDENT_FACTOR;
    int indentLevelSize = indentLevel * mySettings.getIndentSize(getFileType());
    int totalSize = indentLevelSize + spaceCount;

    StringBuffer buffer = new StringBuffer();
    if (mySettings.useTabCharacter(getFileType())) {
      if (mySettings.isSmartTabs(getFileType())) {
        int tabCount = indentLevelSize / mySettings.getTabSize(getFileType());
        int leftSpaces = indentLevelSize - tabCount * mySettings.getTabSize(getFileType());
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
          if (size >= mySettings.getTabSize(getFileType())) {
            buffer.append('\t');
            size -= mySettings.getTabSize(getFileType());
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

  public int getIndent(String text, boolean includeNonSpace) {
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

    int tabSize = mySettings.getTabSize(getFileType());
    int indentLevel = tabCount * tabSize / mySettings.getIndentSize(getFileType());
    return indentLevel * INDENT_FACTOR + spaceCount;
  }

  public FileType getFileType() {
    return myFileType;
  }

}
