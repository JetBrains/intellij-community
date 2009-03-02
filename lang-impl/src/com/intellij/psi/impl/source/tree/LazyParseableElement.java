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

  private final Object lock = new Object();
  private CharSequence myText;

  public LazyParseableElement(@NotNull IElementType type, CharSequence text) {
    super(type);
    myText = text != null ? text.toString() : null;
  }

  @NotNull
  @Override
  public String getText() {
    synchronized (lock) {
      return myText != null ? myText.toString() : super.getText();
    }
  }

  public CharSequence getChars() {
    synchronized (lock) {
      return myText != null ? myText : getText();
    }
  }

  @Override
  public int getTextLength() {
    synchronized (lock) {
      return myText != null ? myText.length() : super.getTextLength();
    }
  }

  @Override
  public int getNotCachedLength() {
    synchronized (lock) {
      return myText != null ? myText.length() : super.getNotCachedLength();
    }
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
      else {
        return super.textMatches(buffer, start);
      }
    }
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
    }
    //subtreeChanged();
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
