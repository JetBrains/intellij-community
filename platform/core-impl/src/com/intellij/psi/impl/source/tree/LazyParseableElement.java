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

/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.StaticGetter;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.reference.SoftReference;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class LazyParseableElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.LazyParseableElement");
  private static final StaticGetter<CharSequence> NO_TEXT = new StaticGetter<CharSequence>(null);

  private static class ChameleonLock {
    private ChameleonLock() {}

    @NonNls
    @Override
    public String toString() {
      return "chameleon parsing lock";
    }
  }

  // Lock which protects expanding chameleon for this node.
  // Under no circumstances should you grab the PSI_LOCK while holding this lock.
  private final ChameleonLock lock = new ChameleonLock();
  /**
   * Cached or non-parsed text of this element. Must be non-null if {@link #myParsed} is false.
   * Guarded by {@link #lock}
   * */
  @NotNull private Getter<CharSequence> myText;
  private boolean myParsed;

  public LazyParseableElement(@NotNull IElementType type, @Nullable CharSequence text) {
    super(type);
    synchronized (lock) {
      myParsed = text == null;
      if (text == null) {
        myText = NO_TEXT;
      }
      else {
        myText = new StaticGetter<CharSequence>(ImmutableCharSequence.asImmutable(text));
        setCachedLength(text.length());
      }
    }
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    synchronized (lock) {
      if (myParsed) {
        myText = NO_TEXT;
      }
      else {
        setCachedLength(myText.get().length());
      }
    }
  }

  @NotNull
  @Override
  public String getText() {
    CharSequence text = myText();
    if (text != null) {
      return text.toString();
    }
    String s = super.getText();
    synchronized (lock) {
      myText = new SoftReference<CharSequence>(s);
    }
    return s;
  }

  @Override
  @NotNull
  public CharSequence getChars() {
    CharSequence text = myText();
    return text != null ? text : getText();
  }

  @Override
  public int getTextLength() {
    CharSequence text = myText();
    if (text != null) {
      return text.length();
    }
    return super.getTextLength();
  }

  @Override
  public int getNotCachedLength() {
    CharSequence text = myText();
    if (text != null) {
      return text.length();
    }
    return super.getNotCachedLength();
  }

  @Override
  public int hc() {
    CharSequence text = myText();
    return text == null ? super.hc() : LeafElement.leafHC(text);
  }

  @Override
  protected int textMatches(@NotNull CharSequence buffer, int start) {
    CharSequence text = myText();
    if (text != null) {
      return LeafElement.leafTextMatches(text, buffer, start);
    }
    return super.textMatches(buffer, start);
  }

  public boolean isParsed() {
    synchronized (lock) {
      return myParsed;
    }
  }

  private CharSequence myText() {
    synchronized (lock) {
      return myText.get();
    }
  }

  @Override
  final void setFirstChildNode(TreeElement child) {
    if (!isParsed()) {
      LOG.error("Mutating collapsed chameleon");
    }
    super.setFirstChildNode(child);
  }

  @Override
  final void setLastChildNode(TreeElement child) {
    if (!isParsed()) {
      LOG.error("Mutating collapsed chameleon");
    }
    super.setLastChildNode(child);
  }

  private void ensureParsed() {
    if (!ourParsingAllowed) {
      LOG.error("Parsing not allowed!!!");
    }
    CharSequence text;
    synchronized (lock) {
      if (myParsed) return;
      text = myText.get();
      assert text != null;
    }

    if (TreeUtil.getFileElement(this) == null) {
      LOG.error("Chameleons must not be parsed till they're in file tree: " + this);
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();

    DebugUtil.startPsiModification("lazy-parsing");
    try {
      ILazyParseableElementType type = (ILazyParseableElementType)getElementType();
      ASTNode parsedNode = type.parseContents(this);

      if (parsedNode == null && text.length() > 0) {
        CharSequence diagText = ApplicationManager.getApplication().isInternal() ? text : "";
        LOG.error("No parse for a non-empty string: " + diagText + "; type=" + LogUtil.objectAndClass(type));
      }

      synchronized (lock) {
        if (myParsed) return;
        if (rawFirstChild() != null) {
          LOG.error("Reentrant parsing?");
        }

        myParsed = true;

        if (parsedNode != null) {
          super.rawAddChildrenWithoutNotifications((TreeElement)parsedNode);
        }

        AstPath.cacheNodePaths(this);

        assertTextLengthIntact(text.length());
        myText = new SoftReference<CharSequence>(text);
      }
    }
    finally {
      DebugUtil.finishPsiModification();
    }
  }

  private void assertTextLengthIntact(int expected) {
    int length = 0;
    for (ASTNode node : getChildren(null)) {
      length += node.getTextLength();
    }
    assert length == expected : "Text mismatch in " + getElementType();
  }

  @Override
  public void rawAddChildrenWithoutNotifications(@NotNull TreeElement first) {
    if (!isParsed()) {
      LOG.error("Mutating collapsed chameleon");
    }
    super.rawAddChildrenWithoutNotifications(first);
  }

  @Override
  public TreeElement getFirstChildNode() {
    ensureParsed();
    return super.getFirstChildNode();
  }

  @Override
  public TreeElement getLastChildNode() {
    ensureParsed();
    return super.getLastChildNode();
  }

  public int copyTo(@Nullable char[] buffer, int start) {
    CharSequence text = myText();
    if (text == null) return -1;

    if (buffer != null) {
      CharArrayUtil.getChars(text, buffer, start);
    }
    return start + text.length();
  }

  private static boolean ourParsingAllowed = true;

  @TestOnly
  public static void setParsingAllowed(boolean allowed) {
    ourParsingAllowed = allowed;
  }
  
}
