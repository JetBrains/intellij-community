// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

public abstract class LeafElement extends TreeElement implements LighterASTTokenNode {
  private static final Logger LOG = Logger.getInstance(LeafElement.class);
  private static final Key<SoftReference<String>> CACHED_TEXT = Key.create("CACHED_TEXT");

  private static final int TEXT_MATCHES_THRESHOLD = 5;

  private final CharSequence myText;

  protected LeafElement(@NotNull IElementType type, @NotNull CharSequence text) {
    super(type);
    myText = text;
  }

  @Override
  public @NotNull LeafElement clone() {
    LeafElement clone = (LeafElement)super.clone();
    clone.clearCaches();
    return clone;
  }

  @Override
  public int getTextLength() {
    return myText.length();
  }

  @Override
  public @NotNull CharSequence getChars() {
    return myText;
  }

  @Override
  public @NotNull String getText() {
    CharSequence text = myText;
    if (text.length() > 1000 && !(text instanceof String)) { // e.g. a large text file
      String cachedText = dereference(getUserData(CACHED_TEXT));
      if (cachedText == null) {
        cachedText = text.toString();
        putUserData(CACHED_TEXT, new SoftReference<>(cachedText));
      }
      return cachedText;
    }

    return text.toString();
  }

  public char charAt(int position) {
    return myText.charAt(position);
  }

  public int copyTo(char @Nullable [] buffer, int start) {
    int length = myText.length();
    if (buffer != null) {
      CharArrayUtil.getChars(myText, buffer, start, length);
    }
    return start + length;
  }

  @Override
  public char @NotNull [] textToCharArray() {
    char[] buffer = new char[myText.length()];
    CharArrayUtil.getChars(myText, buffer, 0);
    return buffer;
  }

  @Override
  public boolean textContains(char c) {
    CharSequence text = myText;
    int len = text.length();

    if (len > TEXT_MATCHES_THRESHOLD) {
      char[] chars = CharArrayUtil.fromSequenceWithoutCopying(text);
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
    return leafTextMatches(myText, buffer, start);
  }

  static int leafTextMatches(@NotNull CharSequence text, @NotNull CharSequence buffer, int start) {
    assert start >= 0 : start;
    int length = text.length();
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

  public @NotNull LeafElement rawReplaceWithText(@NotNull String newText) {
    LeafElement newLeaf = ASTFactory.leaf(getElementType(), newText);
    copyUserDataTo(newLeaf);
    rawReplaceWithList(newLeaf);
    newLeaf.clearCaches();
    return newLeaf;
  }

  public @NotNull LeafElement replaceWithText(@NotNull String newText) {
    LeafElement newLeaf = ChangeUtil.copyLeafWithText(this, newText);
    getTreeParent().replaceChild(this, newLeaf);
    return newLeaf;
  }

  @Override
  public LeafElement findLeafElementAt(int offset) {
    return this;
  }

  @Override
  public boolean textMatches(@NotNull CharSequence buf, int start, int end) {
    CharSequence text = getChars();
    int len = text.length();

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
  public void acceptTree(@NotNull TreeElementVisitor visitor) {
    visitor.visitLeaf(this);
  }

  @Override
  public ASTNode findChildByType(@NotNull IElementType type) {
    return null;
  }

  @Override
  public ASTNode findChildByType(@NotNull IElementType type, @Nullable ASTNode anchor) {
    return null;
  }

  @Override
  public @Nullable ASTNode findChildByType(@NotNull TokenSet typesSet) {
    return null;
  }

  @Override
  public @Nullable ASTNode findChildByType(@NotNull TokenSet typesSet, @Nullable ASTNode anchor) {
    return null;
  }

  @Override
  public int hc() {
    return leafHC(getChars());
  }

  static int leafHC(CharSequence text) {
    int hc = 0;
    int length = text.length();
    for (int i = 0; i < length; i++) {
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
  public int getCachedLength() {
    return myText.length();
  }

  @Override
  public ASTNode @NotNull [] getChildren(TokenSet filter) {
    return TreeElement.EMPTY_ARRAY;
  }

  @Override
  public void addChild(@NotNull ASTNode child, ASTNode anchorBefore) {
    throw new IncorrectOperationException("Leaf elements cannot have children.");
  }

  @Override
  public void addLeaf(@NotNull IElementType leafType, @NotNull CharSequence leafText, ASTNode anchorBefore) {
    throw new IncorrectOperationException("Leaf elements cannot have children.");
  }

  @Override
  public void addChild(@NotNull ASTNode child) {
    throw new IncorrectOperationException("Leaf elements cannot have children.");
  }

  @Override
  public void removeChild(@NotNull ASTNode child) {
    throw new IncorrectOperationException("Leaf elements cannot have children.");
  }

  @Override
  public void replaceChild(@NotNull ASTNode oldChild, @NotNull ASTNode newChild) {
    throw new IncorrectOperationException("Leaf elements cannot have children.");
  }

  @Override
  public void replaceAllChildrenToChildrenOf(@NotNull ASTNode anotherParent) {
    throw new IncorrectOperationException("Leaf elements cannot have children.");
  }

  @Override
  public void removeRange(@NotNull ASTNode first, @Nullable ASTNode firstWhichStayInTree) {
    throw new IncorrectOperationException("Leaf elements cannot have children.");
  }

  @Override
  public void addChildren(@NotNull ASTNode firstChild, @Nullable ASTNode lastChild, @Nullable ASTNode anchorBefore) {
    throw new IncorrectOperationException("Leaf elements cannot have children.");
  }

  @Override
  public PsiElement getPsi() {
    return null;
  }

  @Override
  public <T extends PsiElement> T getPsi(@NotNull Class<T> clazz) {
    return getPsi(clazz, getPsi(), LOG);
  }

  static <T extends PsiElement> T getPsi(@NotNull Class<T> clazz, PsiElement element, @NotNull Logger log) {
    if (!clazz.isInstance(element)) {
      log.error("unexpected psi class. expected: " + clazz + " got: " + (element == null ? null : element.getClass()));
    }
    //noinspection unchecked
    return (T)element;
  }
}
