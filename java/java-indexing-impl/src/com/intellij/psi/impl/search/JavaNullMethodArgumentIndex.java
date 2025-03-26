// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.syntax.element.JavaSyntaxTokenType;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.lexer.TokenList;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.platform.syntax.lexer.TokenListUtil.*;

public final class JavaNullMethodArgumentIndex extends ScalarIndexExtension<JavaNullMethodArgumentIndex.MethodCallData> {
  public static final ID<MethodCallData, Void> INDEX_ID = ID.create("java.null.method.argument");

  @Override
  public @NotNull ID<MethodCallData, Void> getName() {
    return INDEX_ID;
  }

  private static final StringSearcher ourSearcher = new StringSearcher("null", true, true);

  @Override
  public @NotNull DataIndexer<MethodCallData, Void, FileContent> getIndexer() {
    return inputData -> {
      if (ourSearcher.scan(inputData.getContentAsText()) < 0) return Map.of();

      Map<MethodCallData, Void> result = new HashMap<>();

      TokenList tokens = JavaParserUtil.obtainTokens(inputData.getPsiFile());
      for (int i = 0; i < tokens.getTokenCount(); i++) {
        if (hasType(tokens, i, JavaSyntaxTokenType.NULL_KEYWORD)) {
          MethodCallData data = findCallData(tokens, i);
          if (data != null) {
            result.put(data, null);
          }
        }
      }
      return result;
    };
  }

  private static @Nullable MethodCallData findCallData(TokenList tokens, int nullIndex) {
    if (!hasType(tokens, forwardWhile(tokens, nullIndex + 1, JavaParserUtil.WS_COMMENTS), JavaSyntaxTokenType.RPARENTH, JavaSyntaxTokenType.COMMA)) return null;

    int i = backWhile(tokens, nullIndex - 1, JavaParserUtil.WS_COMMENTS);
    if (!hasType(tokens, i, JavaSyntaxTokenType.LPARENTH, JavaSyntaxTokenType.COMMA)) return null;


    int commaCount = 0;
    while (true) {
      if (hasType(tokens, i, null, JavaSyntaxTokenType.SEMICOLON, JavaSyntaxTokenType.EQ, JavaSyntaxTokenType.RBRACE)) {
        return null;
      }

      SyntaxElementType type = tokens.getTokenType(i);
      if (type == JavaSyntaxTokenType.COMMA) {
        commaCount++;
      }
      else if (type == JavaSyntaxTokenType.LPARENTH) {
        String name = findMethodName(tokens, i);
        return name == null ? null : new MethodCallData(name, commaCount);
      }

      i = backWithBraceMatching(tokens, i, JavaSyntaxTokenType.LPARENTH, JavaSyntaxTokenType.RPARENTH);
    }
  }

  private static @Nullable String findMethodName(TokenList tokens, int lparenth) {
    int i = backWhile(tokens, lparenth - 1, JavaParserUtil.WS_COMMENTS);
    if (hasType(tokens, i, JavaSyntaxTokenType.GT)) {
      i = backWhile(tokens, backWithBraceMatching(tokens, i, JavaSyntaxTokenType.LT, JavaSyntaxTokenType.GT), JavaParserUtil.WS_COMMENTS);
    }
    return tokens.getTokenType(i) == JavaSyntaxTokenType.IDENTIFIER ? tokens.getTokenText(i).toString() : null;
  }

  @Override
  public @NotNull KeyDescriptor<MethodCallData> getKeyDescriptor() {
    return new KeyDescriptor<>() {
      @Override
      public int getHashCode(MethodCallData value) {
        return value.hashCode();
      }

      @Override
      public boolean isEqual(MethodCallData val1, MethodCallData val2) {
        return val1.equals(val2);
      }

      @Override
      public void save(@NotNull DataOutput out, MethodCallData value) throws IOException {
        EnumeratorStringDescriptor.INSTANCE.save(out, value.getMethodName());
        DataInputOutputUtil.writeINT(out, value.getNullParameterIndex());
      }

      @Override
      public MethodCallData read(@NotNull DataInput in) throws IOException {
        return new MethodCallData(EnumeratorStringDescriptor.INSTANCE.read(in),
                                  DataInputOutputUtil.readINT(in));
      }
    };
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return JavaFileElementType.isInSourceContent(file);
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }

  @Override
  public boolean needsForwardIndexWhenSharing() {
    return false;
  }

  public static final class MethodCallData {
    private final @NotNull String myMethodName;
    private final int myNullParameterIndex;

    public MethodCallData(@NotNull String name, int index) {
      myMethodName = name;
      myNullParameterIndex = index;
    }

    public @NotNull String getMethodName() {
      return myMethodName;
    }

    public int getNullParameterIndex() {
      return myNullParameterIndex;
    }


    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MethodCallData data = (MethodCallData)o;

      if (myNullParameterIndex != data.myNullParameterIndex) return false;
      if (!myMethodName.equals(data.myMethodName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myMethodName.hashCode();
      result = 31 * result + myNullParameterIndex;
      return result;
    }

    @Override
    public String toString() {
      return "MethodCallData{" +
             "myMethodName='" + myMethodName + '\'' +
             ", myNullParameterIndex=" + myNullParameterIndex +
             '}';
    }
  }
}
