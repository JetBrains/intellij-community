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

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LeafElement extends TreeElement {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.source.tree.LeafElement");

  private static final int TEXT_MATCHES_THRESHOLD = 5;

  private final CharSequence myText;

  protected LeafElement(IElementType type, CharSequence text) {
    super(type);
    myText = text;
  }

  @Override
  public LeafElement clone() {
    LeafElement clone = (LeafElement)super.clone();
    clone.clearCaches();
    return clone;
  }

  @Override
  public int getTextLength() {
    return myText.length();
  }

  @Override
  public CharSequence getChars() {
    return myText;
  }

  @Override
  public String getText() {
    return myText.toString();
  }

  public char charAt(int position) {
    return myText.charAt(position);
  }

  public int copyTo(@Nullable char[] buffer, int start) {
    final int length = myText.length();
    if (buffer != null) {
      CharArrayUtil.getChars(myText, buffer, start, length);
    }
    return start + length;
  }

  @Override
  @NotNull
  public char[] textToCharArray() {
    final char[] buffer = new char[myText.length()];
    CharArrayUtil.getChars(myText, buffer, 0);
    return buffer;
  }

  @Override
  public boolean textContains(char c) {
    final CharSequence text = myText;
    final int len = myText.length();

    if (len > TEXT_MATCHES_THRESHOLD) {
      char[] chars = CharArrayUtil.fromSequenceWithoutCopying(myText);
      if (chars != null) {
        for (char aChar : chars) {
          if (aChar == c) return true;
        }
        return false;
      }
    }

    for (int i = 0; i < len; ++i) {
      if (c == text.charAt(i)) return true;
    }

    return false;
  }

  @Override
  protected int textMatches(@NotNull CharSequence buffer, int start) {
    assert start >= 0 : start;
    final CharSequence text = myText;
    return leafTextMatches(text, buffer, start);
  }

  public static int leafTextMatches(@NotNull CharSequence text, @NotNull CharSequence buffer, int start) {
    assert start >= 0 : start;
    final int length = text.length();
    if(buffer.length() - start < length) {
      return start == 0 ? Integer.MIN_VALUE : -start;
    }
    for(int i = 0; i < length; i++){
      int k = i + start;
      if(text.charAt(i) != buffer.charAt(k)) {
        return k == 0 ? Integer.MIN_VALUE : -k;
      }
    }
    return start + length;
  }

  public LeafElement rawReplaceWithText(String newText) {
    LeafElement newLeaf = ASTFactory.leaf(getElementType(), newText);
    copyUserDataTo(newLeaf);
    rawReplaceWithList(newLeaf);
    newLeaf.clearCaches();
    return newLeaf;
  }
  
  public LeafElement replaceWithText(String newText) {
    LeafElement newLeaf = ChangeUtil.copyLeafWithText(this, newText);
    getTreeParent().replaceChild(this, newLeaf);
    return newLeaf;
  }

  @Override
  public LeafElement findLeafElementAt(int offset) {
    return this;
  }

  @Override
  @SuppressWarnings({"MethodOverloadsMethodOfSuperclass"})
  public boolean textMatches(@NotNull final CharSequence buf, int start, int end) {
    final CharSequence text = getChars();
    final int len = text.length();

    if (end - start != len) return false;
    if (buf == text) return true;

    if (len > TEXT_MATCHES_THRESHOLD && text instanceof String && buf instanceof String) {
      return ((String)text).regionMatches(0,(String)buf,start,len);
    }

    for (int i = 0; i < len; i++) {
      if (text.charAt(i) != buf.charAt(start + i)) return false;
    }

    return true;
  }

  @Override
  public void acceptTree(TreeElementVisitor visitor) {
    visitor.visitLeaf(this);
  }

  @Override
  public ASTNode findChildByType(IElementType type) {
    return null;
  }

  @Override
  public ASTNode findChildByType(IElementType type, @Nullable ASTNode anchor) {
    return null;
  }

  @Override
  @Nullable
  public ASTNode findChildByType(@NotNull TokenSet typesSet) {
    return null;
  }

  @Override
  @Nullable
  public ASTNode findChildByType(@NotNull TokenSet typesSet, @Nullable ASTNode anchor) {
    return null;
  }

  @Override
  public int hc() {
    return leafHC(getChars());
  }

  public static int leafHC(CharSequence text) {
    final int len = text.length();
    int hc = 0;

    for (int i = 0; i < len; i++) {
      hc += text.charAt(i);
    }

    return hc;
  }

  @Override
  public TreeElement getFirstChildNode() {
    return null;
  }

  @Override
  public TreeElement getLastChildNode() {
    return null;
  }

  @Override
  public int getNotCachedLength() {
    return myText.length();
  }

  @Override
  public int getCachedLength() {
    return getNotCachedLength();
  }

  @Override
  public ASTNode[] getChildren(TokenSet filter) {
    return EMPTY_ARRAY;
  }

  @Override
  public void addChild(@NotNull ASTNode child, ASTNode anchorBefore) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  @Override
  public void addLeaf(@NotNull final IElementType leafType, final CharSequence leafText, final ASTNode anchorBefore) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  @Override
  public void addChild(@NotNull ASTNode child) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  @Override
  public void removeChild(@NotNull ASTNode child) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  @Override
  public void replaceChild(@NotNull ASTNode oldChild, @NotNull ASTNode newChild) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  @Override
  public void replaceAllChildrenToChildrenOf(ASTNode anotherParent) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  @Override
  public void removeRange(@NotNull ASTNode first, ASTNode firstWhichStayInTree) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  @Override
  public void addChildren(ASTNode firstChild, ASTNode lastChild, ASTNode anchorBefore) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  @Override
  public PsiElement getPsi() {
    return null;
  }

  @Override
  @Nullable
  public <T extends PsiElement> T getPsi(Class<T> clazz) {
    return getPsi(clazz, getPsi(), LOG);
  }

  @Nullable
  static <T extends PsiElement> T getPsi(Class<T> clazz, PsiElement element, Logger log) {
    log.assertTrue(clazz.isInstance(element), "unexpected psi class. expected: " + clazz
                                             + " got: " + (element == null ? null : element.getClass()));
    return (T)element;
  }

}
