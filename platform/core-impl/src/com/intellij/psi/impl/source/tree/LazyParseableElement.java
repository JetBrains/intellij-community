// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementTypeBase;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.VarHandleWrapper;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.intellij.util.ObjectUtils.objectInfo;

public class LazyParseableElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance(LazyParseableElement.class);
  private static final Supplier<CharSequence> NO_TEXT = () -> null;

  private static final VarHandleWrapper myTextGetter = VarHandleWrapper.getFactory().create(LazyParseableElement.class, "myText", Object.class);
  private static final VarHandleWrapper myParsedGetter = VarHandleWrapper.getFactory().create(LazyParseableElement.class, "myParsed", Object.class);


  // Lock which protects expanding chameleon for this node.
  // Under no circumstances should you grab the PSI_LOCK while holding this lock.
  private final ReentrantLock myLock = new ReentrantLock();

   // Cached or non-parsed text of this element. Must be non-null if {@link #myParsed} is false.
   // Coordinated writes to (myParsed, myText) are guarded by {@link #myLock}
  /**
   * A versioned reference to {@link Supplier<CharSequence>}.
   * @see doSetMyText
   * @see doGetMyText
   */
  @SuppressWarnings("unused, FieldMayBeFinal")
  private volatile @Nullable Object myText = null;
  /**
   * A versioned reference to {@link Boolean}.
   * @see doSetMyParsed
   * @see doGetMyParsed
   */
  @SuppressWarnings("unused, FieldMayBeFinal")
  private volatile @Nullable Object myParsed = null;


  private void doSetMyText(long version, Supplier<CharSequence> text) {
    if (version == -1) {
      this.myText = text;
    } else {
      setVersionedField(myTextGetter, version, text);
    }
  }

  private Supplier<CharSequence> doGetMyText(long version) {
    if (version == -1) {
      //noinspection unchecked
      return (Supplier<CharSequence>)this.myText;
    } else {
      Object result = getVersionedField(this.myText, version);
      //noinspection unchecked
      return (Supplier<CharSequence>)result;
    }
  }

  private void doSetMyParsed(long version, Boolean isParsed) {
    if (version == -1) {
      this.myParsed = isParsed;
    } else {
      setVersionedField(myParsedGetter, version, isParsed);
    }
  }

  private Boolean doGetMyParsed(long version) {
    if (version == -1) {
      return (Boolean)this.myParsed;
    } else {
      Object result = getVersionedField(this.myParsed, version);
      return (Boolean)result;
    }
  }

  public LazyParseableElement(@NotNull IElementType type, @Nullable CharSequence text) {
    super(type);

    waitForLock(myLock);
    try {
      long version = getVersionForReading();
      if (text == null) {
        doSetMyParsed(version, true);
        doSetMyText(version, NO_TEXT);
      }
      else {
        CharSequence sequence = ImmutableCharSequence.asImmutable(text);
        doSetMyText(version, () -> sequence);
        setCachedLength(text.length());
      }
    }
    finally {
      myLock.unlock();
    }
  }

  @Override
  public @NotNull CompositeElement clone() {
    LazyParseableElement clone = (LazyParseableElement)super.clone();
    // we need to forcibly drop the cloned lists to avoid accidental sharing of lists between this and clone
    clone.clearCaches();
    return clone;
  }

  @Override
  @ApiStatus.Internal
  protected void postClone(CompositeElement origin) {
    long originVersion = origin.getVersionForReading();
    long cloneVersion = getVersionForReading();
    myParsedGetter.setVolatile(this, null);
    myTextGetter.setVolatile(this, null);
    doSetMyText(cloneVersion, ((LazyParseableElement)origin).doGetMyText(originVersion));
    doSetMyParsed(cloneVersion, ((LazyParseableElement)origin).doGetMyParsed(originVersion));
  }


  @Override
  public void clearCaches() {
    super.clearCaches();

    waitForLock(myLock);
    long version = getVersionForWriting();
    try {
      Boolean parsedForCurrentVersion = doGetMyParsed(version);
      if (parsedForCurrentVersion != null) {
        doSetMyText(version, NO_TEXT);
        doSetMyParsed(version, parsedForCurrentVersion);
      }
      else {
        Supplier<CharSequence> currentText = doGetMyText(version);
        doSetMyText(version, currentText);
        // myParsed corresponds to myText being not null
        setCachedLength(Objects.requireNonNull(currentText).get().length());
        doSetMyParsed(version, null);
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
    long version = getVersionForReading();
    doSetMyText(version, new SoftReference<>(s));
    return s;
  }

  @Override
  public @NotNull CharSequence getChars() {
    CharSequence text = myText();
    if (text == null) {
      // use super.getText() instead of super.getChars() to avoid extra myText() call
      text = super.getText();
      long version = getVersionForReading();
      doSetMyText(version, new SoftReference<>(text));
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
    long version = getVersionForReading();
    return doGetMyParsed(version) != null;
  }

  private CharSequence myText() {
    long version = getVersionForReading();
    Supplier<CharSequence> supplier = doGetMyText(version);
    if (supplier == null) return null;
    return supplier.get();
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
    long version = getVersionForReading();
    if (doGetMyParsed(version) != null) return;

    CharSequence text;
    waitForLock(myLock);
    try {
      if (doGetMyParsed(version) != null) return;

      Supplier<CharSequence> textSupplier = doGetMyText(version);

      text = textSupplier.get();
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
        InternalPsiVersioning.runWriteModification(() -> {
          long innerVersion = getVersionForWriting();
          TreeElement parsedNode = InternalPsiVersioning.inVersionedEnvironment(this.isVersioned(), () ->
            (TreeElement)((ILazyParseableElementTypeBase)getElementType()).parseContents(this)
          );

          if (parsedNode != null) {
            assertTextLengthIntact(text, parsedNode);
            setChildren(innerVersion, parsedNode);
          }

          doSetMyParsed(innerVersion, true);
          doSetMyText(innerVersion, new SoftReference<>(text));
          return null;
        });
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

  private void setChildren(long innerVersion, @NotNull TreeElement parsedNode) {
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      try {
        TreeElement last = rawSetParents(innerVersion, parsedNode, this);
        super.setFirstChildNode(innerVersion, parsedNode);
        super.setLastChildNode(innerVersion, last);
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
  @ApiStatus.Internal
  protected TreeElement getFirstChildNodeVersioned(long version) {
    ensureParsed();
    return super.getFirstChildNodeVersioned(version);
  }

  @Override
  public TreeElement getLastChildNode() {
    ensureParsed();
    return super.getLastChildNode();
  }

  @ApiStatus.Internal
  @Override
  public TreeElement getLastChildNodeVersioned(long version) {
    ensureParsed();
    return super.getLastChildNodeVersioned(version);
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
