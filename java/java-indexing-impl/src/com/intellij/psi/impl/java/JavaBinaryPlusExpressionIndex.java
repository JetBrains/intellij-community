// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.BooleanDataDescriptor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
      int[] offsets = new StringSearcher("+", true, true).findAllOccurrences(text);
      if (offsets.length == 0) return Collections.emptyMap();

      LighterAST tree = ((PsiDependentFileContent)inputData).getLighterAST();
      TIntArrayList result = new TIntArrayList(offsets.length);
      Map<LighterASTNode, Boolean> stringConcatenations = new HashMap<>();
      LightTreeUtil.processLeavesAtOffsets(offsets, tree, (leaf, offset) -> {
        LighterASTNode element = leaf == null ? null : tree.getParent(leaf);
        if (element == null) return;

        if ((element.getTokenType() == JavaElementType.BINARY_EXPRESSION
             || element.getTokenType() == JavaElementType.POLYADIC_EXPRESSION) &&
            !stringConcatenations.computeIfAbsent(element, __ -> isStringConcatenation(element, tree))) {
          result.add(offset);
        }
      });
      THashMap<Boolean, PlusOffsets> resultMap = new THashMap<>();
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
    return 2;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
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

  private static boolean isStringConcatenation(@NotNull LighterASTNode concatExpr, @NotNull LighterAST tree) {
    return LightTreeUtil
      .getChildrenOfType(tree, concatExpr, ElementType.EXPRESSION_BIT_SET)
      .stream()
      .allMatch(e -> e.getTokenType() == JavaElementType.LITERAL_EXPRESSION);
  }
}
