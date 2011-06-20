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
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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

  private final ChameleonLock lock = new ChameleonLock();
  private CharSequence myText;

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
  public void setFirstChildNode(TreeElement child) {
    if (myText() != null) {
      LOG.error("Mutating collapsed chameleon");
    }
    super.setFirstChildNode(child);
  }

  @Override
  public void setLastChildNode(TreeElement child) {
    if (myText() != null) {
      LOG.error("Mutating collapsed chameleon");
    }
    super.setLastChildNode(child);
  }

  private void ensureParsed() {
    if (myText == null) return;
    if (rawFirstChild() != null) {
      LOG.error("Reentrant parsing?");
    }

    if (TreeUtil.getFileElement(this) == null) {
      LOG.error("Chameleons must not be parsed till they're in file tree: " + this);
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();

    ILazyParseableElementType type = (ILazyParseableElementType)getElementType();
    ASTNode parsedNode = type.parseContents(this);

    if (parsedNode == null && myText.length() > 0) {
      if (ApplicationManagerEx.getApplicationEx().isInternal() && !ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error("No parse for a non-empty string: " + myText + "; type=" + LogUtil.objectAndClass(type));
      } else {
        LOG.error("No parse for a non-empty string: type=" + LogUtil.objectAndClass(type));
      }
    }

    //CharSequence text = myText;
    myText = null;

    if (parsedNode != null) {
      rawAddChildren((TreeElement)parsedNode);

      //if (getNotCachedLength() != text.length()) {
      //  if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      //    LOG.error("Inconsistent reparse: type=" + getElementType() + "; text=" + text + "; treeText=" + getText());
      //  } else {
      //    LOG.error("Inconsistent reparse: type=" + getElementType());
      //  }
      //}

      //ensure PSI is created all at once, to reduce contention of PsiLock in CompositeElement.getPsi()
      if (parsedNode instanceof CompositeElement) {
        ((CompositeElement)parsedNode).createAllChildrenPsi();
      }
      parsedNode.getTreeNext();
    }
  }

  @Override
  public void rawAddChildren(@NotNull TreeElement first) {
    if (myText() != null) {
      LOG.error("Mutating collapsed chameleon");
    }
    super.rawAddChildren(first);
  }

  @Override
  public TreeElement getFirstChildNode() {
    synchronized (lock) {
      ensureParsed();
      return super.getFirstChildNode();
    }
  }

  @Override
  public TreeElement getLastChildNode() {
    synchronized (lock) {
      ensureParsed();
      return super.getLastChildNode();
    }
  }

  public int copyTo(char[] buffer, int start) {
    CharSequence text = myText();
    if (text == null) return -1;

    if (buffer != null) {
      CharArrayUtil.getChars(text, buffer, start);
    }
    return start + text.length();
  }
}
