// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AstBufferUtil {
  private AstBufferUtil() { }

  public static int toBuffer(@NotNull ASTNode element, char @Nullable [] buffer, int offset) {
    return toBuffer(element, buffer, offset, false);
  }

  public static int toBuffer(@NotNull ASTNode element, char @Nullable [] buffer, int offset, boolean skipWhitespaceAndComments) {
    BufferVisitor visitor = new BufferVisitor(skipWhitespaceAndComments, skipWhitespaceAndComments, offset, buffer);
    ((TreeElement)element).acceptTree(visitor);
    return visitor.end;
  }

  public static String getTextSkippingWhitespaceComments(@NotNull ASTNode element) {
    int length = toBuffer(element, null, 0, true);
    char[] buffer = new char[length];
    toBuffer(element, buffer, 0, true);
    return StringFactory.createShared(buffer);
  }

  public static class BufferVisitor extends RecursiveTreeElementWalkingVisitor {
    private final boolean skipWhitespace;
    private final boolean skipComments;

    protected final int offset;
    protected int end;
    protected final char[] buffer;

    public BufferVisitor(PsiElement element, boolean skipWhitespace, boolean skipComments) {
      this(skipWhitespace, skipComments, 0, new char[element.getTextLength()]);
      ((TreeElement)element.getNode()).acceptTree(this);
    }

    public BufferVisitor(boolean skipWhitespace, boolean skipComments, int offset, char @Nullable [] buffer) {
      super(false);

      this.skipWhitespace = skipWhitespace;
      this.skipComments = skipComments;
      this.buffer = buffer;
      this.offset = offset;
      end = offset;
    }

    public int getEnd() {
      return end;
    }

    public char @NotNull [] getBuffer() {
      assert buffer != null;
      return buffer;
    }

    public CharSequence createCharSequence() {
      assert buffer != null;
      return new CharArrayCharSequence(buffer, offset, end);
    }

    @Override
    public void visitLeaf(LeafElement element) {
      ProgressIndicatorProvider.checkCanceled();
      if (!isIgnored(element)) {
        end = element.copyTo(buffer, end);
      }
    }

    protected boolean isIgnored(LeafElement element) {
      return element instanceof ForeignLeafPsiElement ||
             (skipWhitespace && element instanceof PsiWhiteSpace) ||
             (skipComments && element instanceof PsiComment);
    }

    @Override
    public void visitComposite(CompositeElement composite) {
      if (composite instanceof LazyParseableElement) {
        LazyParseableElement lpe = (LazyParseableElement)composite;
        int lpeResult = lpe.copyTo(buffer, end);
        if (lpeResult >= 0) {
          end = lpeResult;
          return;
        }
        assert lpe.isParsed();
      }

      super.visitComposite(composite);
    }
  }
}
