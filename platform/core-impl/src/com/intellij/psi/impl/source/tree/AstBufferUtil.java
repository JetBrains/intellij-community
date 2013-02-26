/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class AstBufferUtil {
  private AstBufferUtil() { }

  public static int toBuffer(@NotNull ASTNode element, @Nullable char[] buffer, int offset) {
    return toBuffer(element, buffer, offset, false);
  }

  public static int toBuffer(@NotNull ASTNode element, @Nullable char[] buffer, int offset, boolean skipWhitespaceAndComments) {
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

    public BufferVisitor(boolean skipWhitespace, boolean skipComments, int offset, @Nullable char[] buffer) {
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

    public char[] getBuffer() {
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
