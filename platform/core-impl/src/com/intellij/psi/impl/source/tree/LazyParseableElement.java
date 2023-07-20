// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementTypeBase;
import com.intellij.reference.SoftReference;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.intellij.util.ObjectUtils.objectInfo;

public class LazyParseableElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance(LazyParseableElement.class);
  private static final Supplier<CharSequence> NO_TEXT = () -> null;

  // Lock which protects expanding chameleon for this node.
  // Under no circumstances should you grab the PSI_LOCK while holding this lock.
  private final ReentrantLock myLock = new ReentrantLock();
  /**
   * Cached or non-parsed text of this element. Must be non-null if {@link #myParsed} is false.
   * Coordinated writes to (myParsed, myText) are guarded by {@link #myLock}
   * */
  private volatile @NotNull Supplier<CharSequence> myText;
  private volatile boolean myParsed;

  public LazyParseableElement(@NotNull IElementType type, @Nullable CharSequence text) {
    super(type);

    waitForLock(myLock);
    try {
      if (text == null) {
        myParsed = true;
        myText = NO_TEXT;
      }
      else {
        CharSequence sequence = ImmutableCharSequence.asImmutable(text);
        myText = () -> sequence;
        setCachedLength(text.length());
      }
    }
    finally {
      myLock.unlock();
    }
  }

  @Override
  public void clearCaches() {
    super.clearCaches();

    waitForLock(myLock);
    try {
      if (myParsed) {
        myText = NO_TEXT;
      }
      else {
        setCachedLength(myText.get().length());
      }
    }
    finally {
      myLock.unlock();
    }
  }

  @Override
  public @NotNull String getText() {
    CharSequence text = myText();
    if (text != null) {
      return text.toString();
    }
    String s = super.getText();
    myText = new SoftReference<>(s);
    return s;
  }

  @Override
  public @NotNull CharSequence getChars() {
    CharSequence text = myText();
    if (text == null) {
      // use super.getText() instead of super.getChars() to avoid extra myText() call
      text = super.getText();
      myText = new SoftReference<>(text);
    }
    return text;
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
  public int hc() {
    CharSequence text = myText();
    return text == null ? super.hc() : LeafElement.leafHC(text);
  }

  @Override
  public boolean textContains(char c) {
    CharSequence text = myText();
    if (text != null) {
      return StringUtil.indexOf(text, c) != -1;
    }
    return super.textContains(c);
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
    return myParsed;
  }

  private CharSequence myText() {
    return myText.get();
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
    if (myParsed) return;

    CharSequence text;
    waitForLock(myLock);
    try {
      if (myParsed) return;

      text = myText.get();
      assert text != null;

      FileElement fileElement = TreeUtil.getFileElement(this);
      if (fileElement == null) {
        LOG.error("Chameleons must not be parsed till they're in file tree: " + this);
      }
      else {
        fileElement.assertReadAccessAllowed();
      }

      if (rawFirstChild() != null) {
        LOG.error("Reentrant parsing?");
      }

      DebugUtil.performPsiModification("lazy-parsing", () -> {
        TreeElement parsedNode = (TreeElement)((ILazyParseableElementTypeBase)getElementType()).parseContents(this);
        assertTextLengthIntact(text, parsedNode);

        if (parsedNode != null) {
          setChildren(parsedNode);
        }

        myParsed = true;
        myText = new SoftReference<>(text);
      });
    } finally {
      myLock.unlock();
    }
  }

  private void assertTextLengthIntact(CharSequence text, TreeElement child) {
    int length = 0;
    while (child != null) {
      length += child.getTextLength();
      child = child.getTreeNext();
    }
    if (length != text.length()) {
      LOG.error("Text mismatch in " + objectInfo(getElementType()),
                PluginException.createByClass("Text mismatch", null, getElementType().getClass()),
                new Attachment("code.txt", text.toString()));
    }
  }

  private void setChildren(@NotNull TreeElement parsedNode) {
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      try {
        TreeElement last = rawSetParents(parsedNode, this);
        super.setFirstChildNode(parsedNode);
        super.setLastChildNode(last);
      }
      catch (Throwable e) {
        LOG.error("Chameleon expansion may not be interrupted by exceptions", e);
      }
    });
  }

  @Override
  public void rawAddChildrenWithoutNotifications(@NotNull TreeElement first) {
    if (!isParsed()) {
      LOG.error("Mutating collapsed chameleon " + this.getClass());
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

  public int copyTo(char @Nullable [] buffer, int start) {
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

  private static void waitForLock(@NotNull ReentrantLock ml) {
    while (true) {
      try {
        if (ml.tryLock(10, TimeUnit.MILLISECONDS)) {
          return;
        }
      }
      catch (InterruptedException ignore) {
      }

      ProgressManager.checkCanceled();
    }
  }
}
