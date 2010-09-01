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

  public int getTextLength() {
    return myText.length();
  }

  public CharSequence getChars() {
    return myText;
  }

  public String getText() {
    return myText.toString();
  }

  public char charAt(int position) {
    return myText.charAt(position);
  }

  public int copyTo(char[] buffer, int start) {
    if (buffer != null) {
      CharArrayUtil.getChars(myText, buffer, start);
    }
    return start + myText.length();
  }

  @NotNull
  public char[] textToCharArray() {
    final char[] buffer = new char[myText.length()];
    CharArrayUtil.getChars(myText, buffer, 0);
    return buffer;
  }

  public boolean textContains(char c) {
    CharSequence text = myText;
    for (int i = 0; i < text.length(); i++) {
      if (c == text.charAt(i)) return true;
    }

    return false;
  }

  protected int textMatches(CharSequence buffer, int start) {
    final CharSequence text = myText;
    return leafTextMatches(text, buffer, start);
  }

  public static int leafTextMatches(CharSequence text, CharSequence buffer, int start) {
    final int length = text.length();
    if(buffer.length() - start < length) return -1;
    for(int i = 0; i < length; i++){
      if(text.charAt(i) != buffer.charAt(i + start)) return -1;
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

  public LeafElement findLeafElementAt(int offset) {
    return this;
  }

  @SuppressWarnings({"MethodOverloadsMethodOfSuperclass"})
  public boolean textMatches(final CharSequence buf, int start, int end) {
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

  public void acceptTree(TreeElementVisitor visitor) {
    visitor.visitLeaf(this);
  }

  public ASTNode findChildByType(IElementType type) {
    return null;
  }

  @Nullable
  public ASTNode findChildByType(@NotNull TokenSet typesSet) {
    return null;
  }

  @Nullable
  public ASTNode findChildByType(@NotNull TokenSet typesSet, @Nullable ASTNode anchor) {
    return null;
  }

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

  public TreeElement getFirstChildNode() {
    return null;
  }

  public TreeElement getLastChildNode() {
    return null;
  }

  @Override
  public int getNotCachedLength() {
    return myText.length();
  }

  public int getCachedLength() {
    return getNotCachedLength();
  }

  public ASTNode[] getChildren(TokenSet filter) {
    return EMPTY_ARRAY;
  }

  public void addChild(@NotNull ASTNode child, ASTNode anchorBefore) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void addLeaf(@NotNull final IElementType leafType, final CharSequence leafText, final ASTNode anchorBefore) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void addChild(@NotNull ASTNode child) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void removeChild(@NotNull ASTNode child) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void replaceChild(@NotNull ASTNode oldChild, @NotNull ASTNode newChild) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void replaceAllChildrenToChildrenOf(ASTNode anotherParent) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void removeRange(@NotNull ASTNode first, ASTNode firstWhichStayInTree) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

  public void addChildren(ASTNode firstChild, ASTNode lastChild, ASTNode anchorBefore) {
    throw new RuntimeException(new IncorrectOperationException("Leaf elements cannot have children."));
  }

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
