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

package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;


@SuppressWarnings({"HardCodedStringLiteral", "UtilityClassWithoutPrivateConstructor"})
public class DebugUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DebugUtil");

  public static /*final*/ boolean CHECK = false;
  public static final boolean CHECK_INSIDE_ATOMIC_ACTION_ENABLED = false;
  public static final Key<Boolean> TRACK_INVALIDATION_KEY = new Key<Boolean>("TRACK_INVALIDATION_KEY");

  public static boolean shouldTrackInvalidation() {
    return false;
  }

  public static String psiTreeToString(@NotNull PsiElement element, boolean skipWhitespaces) {
    return treeToString(SourceTreeToPsiMap.psiElementToTree(element), skipWhitespaces);
  }

  public static String treeToString(@NotNull ASTNode root, boolean skipWhitespaces) {
    StringBuilder buffer = new StringBuilder();
    treeToBuffer(buffer, root, 0, skipWhitespaces, false, false, true);
    return buffer.toString();
  }

  public static String nodeTreeToString(@NotNull ASTNode root, boolean skipWhitespaces) {
    StringBuilder buffer = new StringBuilder();
    treeToBuffer(buffer, root, 0, skipWhitespaces, false, false, false);
    return buffer.toString();
  }

  public static String treeToString(@NotNull ASTNode root, boolean skipWhitespaces, boolean showRanges) {
    StringBuilder buffer = new StringBuilder();
    treeToBuffer(buffer, root, 0, skipWhitespaces, showRanges, false, true);
    return buffer.toString();
  }

  public static String treeToStringWithUserData(TreeElement root, boolean skipWhitespaces) {
    StringBuilder buffer = new StringBuilder();
    treeToBufferWithUserData(buffer, root, 0, skipWhitespaces);
    return buffer.toString();
  }

  public static String treeToStringWithUserData(PsiElement root, boolean skipWhitespaces) {
    StringBuilder buffer = new StringBuilder();
    treeToBufferWithUserData(buffer, root, 0, skipWhitespaces);
    return buffer.toString();
  }

  public static void treeToBuffer(@NotNull StringBuilder buffer,
                                  @NotNull ASTNode root,
                                  int indent,
                                  boolean skipWhiteSpaces,
                                  boolean showRanges,
                                  final boolean showChildrenRanges,
                                  final boolean usePsi) {
    if (skipWhiteSpaces && root.getElementType() == TokenType.WHITE_SPACE) return;

    StringUtil.repeatSymbol(buffer, ' ', indent);
    if (root instanceof CompositeElement) {
      if (usePsi) {
        final PsiElement psiElement = root.getPsi();
        if (psiElement != null) {
          buffer.append(psiElement.toString());
        }
        else {
          buffer.append(root.getElementType().toString());
        }
      }
      else {
        buffer.append(root.toString());
      }
    }
    else {
      String text = root.getText();
      text = StringUtil.replace(text, "\n", "\\n");
      text = StringUtil.replace(text, "\r", "\\r");
      text = StringUtil.replace(text, "\t", "\\t");
      buffer.append(root.toString()).append("('").append(text).append("')");
    }
    if (showRanges) buffer.append(root.getTextRange());
    buffer.append("\n");
    if (root instanceof CompositeElement) {
      ASTNode child = root.getFirstChildNode();

      if (child == null) {
        StringUtil.repeatSymbol(buffer, ' ', indent + 2);
        buffer.append("<empty list>\n");
      }
      else {
        while (child != null) {
          treeToBuffer(buffer, child, indent + 2, skipWhiteSpaces, showChildrenRanges, showChildrenRanges, usePsi);
          child = child.getTreeNext();
        }
      }
    }
  }

  private static void treeToBufferWithUserData(StringBuilder buffer, TreeElement root, int indent, boolean skipWhiteSpaces) {
    if (skipWhiteSpaces && root.getElementType() == TokenType.WHITE_SPACE) return;

    StringUtil.repeatSymbol(buffer, ' ', indent);
    if (root instanceof CompositeElement) {
      buffer.append(SourceTreeToPsiMap.treeElementToPsi(root).toString());
    }
    else {
      String text = root.getText();
      text = StringUtil.replace(text, "\n", "\\n");
      text = StringUtil.replace(text, "\r", "\\r");
      text = StringUtil.replace(text, "\t", "\\t");
      buffer.append(root.toString()).append("('").append(text).append("')");
    }
    buffer.append(root.getUserDataString());
    buffer.append("\n");
    if (root instanceof CompositeElement) {
      PsiElement[] children = SourceTreeToPsiMap.treeElementToPsi(root).getChildren();

      for (PsiElement child : children) {
        treeToBufferWithUserData(buffer, (TreeElement)SourceTreeToPsiMap.psiElementToTree(child), indent + 2, skipWhiteSpaces);
      }

      if (children.length == 0) {
        StringUtil.repeatSymbol(buffer, ' ', indent + 2);
        buffer.append("<empty list>\n");
      }
    }
  }

  private static void treeToBufferWithUserData(StringBuilder buffer, PsiElement root, int indent, boolean skipWhiteSpaces) {
    if (skipWhiteSpaces && root instanceof PsiWhiteSpace) return;

    StringUtil.repeatSymbol(buffer, ' ', indent);
    if (root instanceof CompositeElement) {
      buffer.append(root);
    }
    else {
      String text = root.getText();
      text = StringUtil.replace(text, "\n", "\\n");
      text = StringUtil.replace(text, "\r", "\\r");
      text = StringUtil.replace(text, "\t", "\\t");
      buffer.append(root.toString()).append("('").append(text).append("')");
    }
    buffer.append(((UserDataHolderBase)root).getUserDataString());
    buffer.append("\n");

    PsiElement[] children = root.getChildren();

    for (PsiElement child : children) {
      treeToBufferWithUserData(buffer, child, indent + 2, skipWhiteSpaces);
    }

    if (children.length == 0) {
      StringUtil.repeatSymbol(buffer, ' ', indent + 2);
      buffer.append("<empty list>\n");
    }

  }

  public static void checkTreeStructure(@NotNull ASTNode anyElement) {
    ASTNode root = anyElement;
    while (root.getTreeParent() != null) {
      root = root.getTreeParent();
    }
    if (root instanceof CompositeElement) {
      synchronized (PsiLock.LOCK) {
        checkSubtree((CompositeElement)root);
      }
    }
  }

  private static void checkSubtree(CompositeElement root) {
    if (root.rawFirstChild() == null) {
      if (root.rawLastChild() != null) {
        throw new IncorrectTreeStructureException(root, "firstChild == null, but lastChild != null");
      }
    }
    else {
      for (ASTNode child = root.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        if (child instanceof CompositeElement) {
          checkSubtree((CompositeElement)child);
        }
        if (child.getTreeParent() != root) {
          throw new IncorrectTreeStructureException(child, "child has wrong parent value");
        }
        if (child == root.getFirstChildNode()) {
          if (child.getTreePrev() != null) {
            throw new IncorrectTreeStructureException(root, "firstChild.prev != null");
          }
        }
        else {
          if (child.getTreePrev() == null) {
            throw new IncorrectTreeStructureException(child, "not first child has prev == null");
          }
          if (child.getTreePrev().getTreeNext() != child) {
            throw new IncorrectTreeStructureException(child, "element.prev.next != element");
          }
        }
        if (child.getTreeNext() == null) {
          if (root.getLastChildNode() != child) {
            throw new IncorrectTreeStructureException(child, "not last child has next == null");
          }
        }
      }
    }
  }

  public static void checkParentChildConsistent(@NotNull ASTNode element) {
    ASTNode treeParent = element.getTreeParent();
    if (treeParent == null) return;
    ASTNode[] elements = treeParent.getChildren(null);
    if (ArrayUtil.find(elements, element) == -1) {
      throw new IncorrectTreeStructureException(element, "child cannot be found among parents children");
    }
    //LOG.debug("checked consistence: "+System.identityHashCode(element));
  }

  public static void checkSameCharTabs(@NotNull ASTNode element1, @NotNull ASTNode element2) {
    final CharTable fromCharTab = SharedImplUtil.findCharTableByTree(element1);
    final CharTable toCharTab = SharedImplUtil.findCharTableByTree(element2);
    LOG.assertTrue(fromCharTab == toCharTab);
  }

  public static String psiToString(@NotNull PsiElement element, final boolean skipWhitespaces) {
    return psiToString(element, skipWhitespaces, false);
  }

  public static String psiToString(@NotNull PsiElement root,
                                 boolean skipWhiteSpaces,
                                 boolean showRanges) {
    final StringBuilder result = new StringBuilder();
    if (root.getNode() == null) {
      psiToBuffer(result, root, 0, skipWhiteSpaces, showRanges, showRanges);
    }
    else {
      treeToBuffer(result, root.getNode(), 0, skipWhiteSpaces, showRanges, showRanges, true);
    }
    return result.toString();
  }
  
  public static void psiToBuffer(@NotNull StringBuilder buffer,
                                 @NotNull PsiElement root,
                                 int indent,
                                 boolean skipWhiteSpaces,
                                 boolean showRanges,
                                 final boolean showChildrenRanges) {
    if (skipWhiteSpaces && root instanceof PsiWhiteSpace) return;

    StringUtil.repeatSymbol(buffer, ' ', indent);
    final String rootStr = root.toString();
    buffer.append(rootStr);
    PsiElement child = root.getFirstChild();
    if (child == null) {
      String text = root.getText();
      assert text != null : "text is null for <" + root + ">";
      text = StringUtil.replace(text, "\n", "\\n");
      text = StringUtil.replace(text, "\r", "\\r");
      text = StringUtil.replace(text, "\t", "\\t");
      buffer.append("('");
      buffer.append(text);
      buffer.append("')");
    }

    if (showRanges) buffer.append(root.getTextRange());
    buffer.append("\n");
    while (child != null) {
      psiToBuffer(buffer, child, indent + 2, skipWhiteSpaces, showChildrenRanges, showChildrenRanges);
      child = child.getNextSibling();
    }
  }


  public static class IncorrectTreeStructureException extends RuntimeException {
    private final ASTNode myElement;

    public IncorrectTreeStructureException(ASTNode element, String message) {
      super(message);
      myElement = element;
    }

    public ASTNode getElement() {
      return myElement;
    }
  }

  public static void trackInvalidation(PsiElement element) {
    if (element == null) return;
    element.putUserData(TRACK_INVALIDATION_KEY, Boolean.TRUE);
    final ASTNode node = element.getNode();
    if (node != null) {
      node.putUserData(TRACK_INVALIDATION_KEY, Boolean.TRUE);
    }
    trackInvalidation(element.getParent());
  }
}
