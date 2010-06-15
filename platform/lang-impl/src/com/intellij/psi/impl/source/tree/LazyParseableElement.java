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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class LazyParseableElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.LazyParseableElement");
  private static final RecursiveTreeElementWalkingVisitor CREATE_PSI = new RecursiveTreeElementWalkingVisitor(false) {

    @Override
    public void visitLeaf(LeafElement leaf) {
    }

    @Override
    public void visitComposite(CompositeElement composite) {
      composite.createPsiNoLock();
      super.visitComposite(composite);
    }

  };

  private static class ChameleonLock {
    private ChameleonLock() {}

    @Override
    public String toString() {
      return "chameleon parsing lock";
    }
  }

  private final Object lock = new ChameleonLock();

  private CharSequence myText;

  public LazyParseableElement(@NotNull IElementType type, CharSequence text) {
    super(type);
    synchronized (lock) {
      myText = text == null ? null : text.toString();
    }
  }

  @NotNull
  @Override
  public String getText() {
    synchronized (lock) {
      if (myText != null) {
        return myText.toString();
      }
    }
    return super.getText();
  }

  public CharSequence getChars() {
    synchronized (lock) {
      if (myText != null) {
        return myText;
      }
    }
    return getText();
  }

  @Override
  public int getTextLength() {
    synchronized (lock) {
      if (myText != null) {
        return myText.length();
      }
    }
    return super.getTextLength();
  }

  @Override
  public int getNotCachedLength() {
    synchronized (lock) {
      if (myText != null) {
        return myText.length();
      }
    }
    return super.getNotCachedLength();
  }

  @Override
  public int getCachedLength() {
    synchronized (lock) {
      if (myText != null) {
        return myText.length();
      }
    }
    return super.getCachedLength();
  }

  @Override
  public int hc() {
    synchronized (lock) {
      if (myText != null) {
        return LeafElement.leafHC(myText);
      }
      else {
        return super.hc();
      }
    }
  }

  @Override
  protected int textMatches(CharSequence buffer, int start) {
    synchronized (lock) {
      if (myText != null) {
        return LeafElement.leafTextMatches(myText, buffer, start);
      }
    }
    return super.textMatches(buffer, start);
  }

  public boolean isParsed() {
    synchronized (lock) {
      return myText == null;
    }
  }


  @Override
  public void setFirstChildNode(TreeElement child) {
    if (myText != null) {
      LOG.error("Mutating collapsed chameleon");
    }
    super.setFirstChildNode(child);
  }

  @Override
  public void setLastChildNode(TreeElement child) {
    if (myText != null) {
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
      LOG.error("Chameleons must not be parsed till they're in file tree");
    }

    ILazyParseableElementType type = (ILazyParseableElementType)getElementType();
    ASTNode parsedNode = type.parseContents(this);
    myText = null;

    if (parsedNode != null) {
      rawAddChildren((TreeElement)parsedNode);

      //ensure PSI is created all at once, to reduce contention of PsiLock in CompositeElement.getPsi()
        ((TreeElement)parsedNode).acceptTree(CREATE_PSI);
        parsedNode = parsedNode.getTreeNext();
    }
  }

  @Override
  public void rawAddChildren(@NotNull TreeElement first) {
    if (myText != null) {
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
    synchronized (lock) {
      if (myText == null) return -1;
      
      if (buffer != null) {
        CharArrayUtil.getChars(myText, buffer, start);
      }
      return start + myText.length();
    }
  }
}
