// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.syntax.element.JavaSyntaxTokenType;
import com.intellij.java.syntax.element.SyntaxElementTypes;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.syntax.lexer.TokenList;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.util.indexing.*;
import com.intellij.util.io.BooleanDataDescriptor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.platform.syntax.lexer.TokenListUtil.*;

public final class JavaBinaryPlusExpressionIndex extends FileBasedIndexExtension<Boolean, JavaBinaryPlusExpressionIndex.PlusOffsets> {
  public static final ID<Boolean, PlusOffsets> INDEX_ID = ID.create("java.binary.plus.expression");

  @Override
  public @NotNull ID<Boolean, PlusOffsets> getName() {
    return INDEX_ID;
  }

  @Override
  public @NotNull DataIndexer<Boolean, PlusOffsets, FileContent> getIndexer() {
    return inputData -> {
      if (Strings.indexOf(inputData.getContentAsText(), '+') < 0) return Map.of();

      TokenList tokens = JavaParserUtil.obtainTokens(inputData.getPsiFile());

      IntList result = new IntArrayList();
      for (int i = 0; i < tokens.getTokenCount(); i++) {
        if (hasType(tokens, i, JavaSyntaxTokenType.PLUS) &&
            (hasType(tokens, forwardWhile(tokens, i + 1, JavaParserUtil.WS_COMMENTS), SyntaxElementTypes.INSTANCE.getALL_LITERALS()) !=
             hasType(tokens, backWhile(tokens, i - 1, JavaParserUtil.WS_COMMENTS), SyntaxElementTypes.INSTANCE.getALL_LITERALS()))) {
          result.add(tokens.getTokenStart(i));
        }
      }

      if (result.isEmpty()) return Collections.emptyMap();

      Map<Boolean, PlusOffsets> resultMap = new HashMap<>();
      resultMap.put(Boolean.TRUE, new PlusOffsets(result.toIntArray()));
      return resultMap;
    };
  }

  @Override
  public @NotNull KeyDescriptor<Boolean> getKeyDescriptor() {
    return BooleanDataDescriptor.INSTANCE;
  }

  @Override
  public @NotNull DataExternalizer<PlusOffsets> getValueExternalizer() {
    return new DataExternalizer<>() {
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
    return 4;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }

  @Override
  public boolean needsForwardIndexWhenSharing() {
    return false;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
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

}
