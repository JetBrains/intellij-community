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
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AstBufferUtil {
  private AstBufferUtil() {}

  public static int toBuffer(@NotNull ASTNode element, @Nullable char[] buffer, int offset) {
    return toBuffer(element, buffer, offset, null);
  }

  public static int toBuffer(@NotNull final ASTNode element, @Nullable final char[] buffer, int offset, @Nullable final TokenSet skipTypes) {
    final int[] result = {offset};

    ((TreeElement)element).acceptTree(new RecursiveTreeElementWalkingVisitor(false) {
      @Override
      public void visitLeaf(LeafElement element) {
        if (element instanceof ForeignLeafPsiElement ||
            skipTypes != null && skipTypes.contains(element.getElementType())) {
          return;
        }

        result[0] = element.copyTo(buffer, result[0]);
      }

      @Override
      public void visitComposite(CompositeElement composite) {
        if (composite instanceof LazyParseableElement) {
          LazyParseableElement lpe = (LazyParseableElement)composite;
          int lpeResult = lpe.copyTo(buffer, result[0]);
          if (lpeResult >= 0) {
            result[0] = lpeResult;
            return;
          }
          assert lpe.isParsed();
        }

        super.visitComposite(composite);
      }
    });

    return result[0];
  }

  public static String getTextSkippingTokens(@NotNull ASTNode element, @Nullable TokenSet skipTypes) {
    int length = toBuffer(element, null, 0, skipTypes);
    char[] buffer = new char[length];
    toBuffer(element, buffer, 0, skipTypes);
    return new String(buffer);
  }
}
