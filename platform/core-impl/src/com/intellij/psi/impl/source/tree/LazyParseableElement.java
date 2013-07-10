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

/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class LazyParseableElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.LazyParseableElement");

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
  private CharSequence myText; /** guarded by {@link #lock} */

  public LazyParseableElement(@NotNull IElementType type, CharSequence text) {
    super(type);
    synchronized (lock) {
      myText = text == null ? null : text.toString();
      if (text != null) {
        setCachedLength(text.length());
      }
    }
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    synchronized (lock) {
      if (myText != null) {
        setCachedLength(myText.length());
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
    return super.getText();
  }

  @Override
  @NotNull
  public CharSequence getChars() {
    CharSequence text = myText();
    if (text != null) {
      return text;
    }
    return super.getText();
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
    return myText() == null;
  }

  private CharSequence myText() {
    synchronized (lock) {
      return myText;
    }
  }

  @Override
  final void setFirstChildNode(TreeElement child) {
    if (myText() != null) {
      LOG.error("Mutating collapsed chameleon");
    }
    super.setFirstChildNode(child);
  }

  @Override
  final void setLastChildNode(TreeElement child) {
    if (myText() != null) {
      LOG.error("Mutating collapsed chameleon");
    }
    super.setLastChildNode(child);
  }

  private void ensureParsed() {
    if (!ourParsingAllowed) {
      LOG.error("Parsing not allowed!!!");
    }
    CharSequence text = myText();
    if (text == null) return;

    if (TreeUtil.getFileElement(this) == null) {
      LOG.error("Chameleons must not be parsed till they're in file tree: " + this);
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();

    ILazyParseableElementType type = (ILazyParseableElementType)getElementType();
    ASTNode parsedNode = type.parseContents(this);

    if (parsedNode == null && text.length() > 0) {
      CharSequence diagText = ApplicationManager.getApplication().isInternal() ? text : "";
      LOG.error("No parse for a non-empty string: " + diagText + "; type=" + LogUtil.objectAndClass(type));
    }

    synchronized (lock) {
      if (myText == null) return;
      if (rawFirstChild() != null) {
        LOG.error("Reentrant parsing?");
      }

      myText = null;

      if (parsedNode == null) return;
      super.rawAddChildrenWithoutNotifications((TreeElement)parsedNode);
    }

    // create PSI all at once, to reduce contention of PsiLock in CompositeElement.getPsi()
    // create PSI outside the 'lock' since this method grabs PSI_LOCK and deadlock is possible when someone else locks in the other order.
    createAllChildrenPsiIfNecessary();
  }

  @Override
  public void rawAddChildrenWithoutNotifications(@NotNull TreeElement first) {
    if (myText() != null) {
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
