// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.BooleanDataDescriptor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class JavaBinaryPlusExpressionIndex extends FileBasedIndexExtension<Boolean, JavaBinaryPlusExpressionIndex.PlusOffsets> implements PsiDependentIndex {
  public static final ID<Boolean, PlusOffsets> INDEX_ID = ID.create("java.binary.plus.expression");

  @NotNull
  @Override
  public ID<Boolean, PlusOffsets> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<Boolean, PlusOffsets, FileContent> getIndexer() {
    return inputData -> {
      CharSequence text = inputData.getContentAsText();
      if (text.chars().noneMatch(c -> c == '+')) return Collections.emptyMap();

      LighterAST tree = ((FileContentImpl)inputData).getLighterASTForPsiDependentIndex();
      TIntArrayList result = new TIntArrayList(1);
      new RecursiveLighterASTNodeWalkingVisitor(tree) {
        @Override
        public void visitNode(@NotNull LighterASTNode element) {
          super.visitNode(element);
          ProgressManager.checkCanceled();
          if ((element.getTokenType() == JavaElementType.BINARY_EXPRESSION ||
               element.getTokenType() == JavaElementType.POLYADIC_EXPRESSION)) {
            int[] offsets = getStringConcatenationPlusOffsets(element, tree);
            if (offsets != null) {
              result.add(offsets);
            }
          }
        }
      }.visitNode(tree.getRoot());
      THashMap<Boolean, PlusOffsets> resultMap = ContainerUtil.newTroveMap();
      resultMap.put(Boolean.TRUE, new PlusOffsets(result.toNativeArray()));
      return resultMap;
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<Boolean> getKeyDescriptor() {
    return BooleanDataDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<PlusOffsets> getValueExternalizer() {
    return new DataExternalizer<PlusOffsets>() {
      @Override
      public void save(@NotNull DataOutput out, PlusOffsets value) throws IOException {
        int[] offsets = value.getOffsets();
        DataInputOutputUtil.writeINT(out, offsets.length);
        for (int i : offsets) {
          DataInputOutputUtil.writeINT(out, i);
        }
      }

      @Override
      public PlusOffsets read(@NotNull DataInput in) throws IOException {
        int[] result = new int[DataInputOutputUtil.readINT(in)];
        for (int i = 0; i < result.length; i++) {
          result[i] = DataInputOutputUtil.readINT(in);
        }
        return new PlusOffsets(result);
      }
    };
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return super.acceptInput(file) && JavaFileElementType.isInSourceContent(file);
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  public static class PlusOffsets {
    private final int[] offsets;

    PlusOffsets(int[] offsets) {this.offsets = offsets;}

    public int[] getOffsets() {
      return offsets;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PlusOffsets offsets1 = (PlusOffsets)o;

      return Arrays.equals(offsets, offsets1.offsets);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(offsets);
    }
  }

  @Nullable
  private static int[] getStringConcatenationPlusOffsets(@NotNull LighterASTNode concatExpr, @NotNull LighterAST tree) {
    TIntArrayList result = null;
    boolean nonLiteralOccurred = false;
    for (LighterASTNode child : tree.getChildren(concatExpr)) {
      IElementType childTokenType = child.getTokenType();
      if (ElementType.EXPRESSION_BIT_SET.contains(childTokenType)) {
        if (!nonLiteralOccurred && !JavaElementType.LITERAL_EXPRESSION.equals(childTokenType)) {
          nonLiteralOccurred = true;
        }
        continue;
      }
      if (JavaTokenType.PLUS.equals(childTokenType)) {
        if (result == null) {
          result = new TIntArrayList();
        }
        result.add(child.getStartOffset());
        continue;
      }
    }
    return result == null || !nonLiteralOccurred ? null : result.toNativeArray();
  }
}
