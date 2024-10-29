// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.backwardRefs.index;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.CompilerRef;
import org.jetbrains.jps.backwardRefs.CompilerRefDescriptor;
import org.jetbrains.jps.backwardRefs.SignatureData;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class JavaCompilerIndices {
  //TODO manage version separately
  public static final int VERSION = 8;

  public static final IndexId<CompilerRef, Integer> BACK_USAGES = IndexId.create("back.refs");
  public static final IndexId<CompilerRef, Collection<CompilerRef>> BACK_HIERARCHY = IndexId.create("back.hierarchy");
  public static final IndexId<CompilerRef, Void> BACK_CLASS_DEF = IndexId.create("back.class.def");
  public static final IndexId<SignatureData, Collection<CompilerRef>> BACK_MEMBER_SIGN = IndexId.create("back.member.sign");
  public static final IndexId<CompilerRef, Collection<CompilerRef>> BACK_CAST = IndexId.create("back.cast");
  public static final IndexId<CompilerRef, Void> IMPLICIT_TO_STRING = IndexId.create("implicit.to.string");

  public static List<IndexExtension<?, ?, CompiledFileData>> getIndices() {
    return Arrays.asList(createBackwardClassDefinitionExtension(),
                         createBackwardUsagesExtension(),
                         createBackwardHierarchyExtension(),
                         createBackwardSignatureExtension(),
                         createBackwardCastExtension(),
                         createImplicitToStringExtension());
  }

  private static IndexExtension<CompilerRef, Void, CompiledFileData> createImplicitToStringExtension() {
    return new IndexExtension<CompilerRef, Void, CompiledFileData>() {
      @Override
      public @NotNull IndexId<CompilerRef, Void> getName() {
        return IMPLICIT_TO_STRING;
      }

      @Override
      public @NotNull DataIndexer<CompilerRef, Void, CompiledFileData> getIndexer() {
        return CompiledFileData::getImplicitToString;
      }

      @Override
      public @NotNull KeyDescriptor<CompilerRef> getKeyDescriptor() {
        return CompilerRefDescriptor.INSTANCE;
      }

      @Override
      public @NotNull DataExternalizer<Void> getValueExternalizer() {
        return VoidDataExternalizer.INSTANCE;
      }

      @Override
      public int getVersion() {
        return VERSION;
      }
    };
  }

  private static IndexExtension<CompilerRef, Collection<CompilerRef>, CompiledFileData> createBackwardCastExtension() {
    return new IndexExtension<CompilerRef, Collection<CompilerRef>, CompiledFileData>() {
      @Override
      public @NotNull IndexId<CompilerRef, Collection<CompilerRef>> getName() {
        return BACK_CAST;
      }

      @Override
      public @NotNull DataIndexer<CompilerRef, Collection<CompilerRef>, CompiledFileData> getIndexer() {
        return CompiledFileData::getCasts;
      }

      @Override
      public @NotNull KeyDescriptor<CompilerRef> getKeyDescriptor() {
        return CompilerRefDescriptor.INSTANCE;
      }

      @Override
      public @NotNull DataExternalizer<Collection<CompilerRef>> getValueExternalizer() {
        return createCompilerRefSeqExternalizer();
      }

      @Override
      public int getVersion() {
        return VERSION;
      }
    };
  }

  private static IndexExtension<CompilerRef, Integer, CompiledFileData> createBackwardUsagesExtension() {
    return new IndexExtension<CompilerRef, Integer, CompiledFileData>() {
      @Override
      public int getVersion() {
        return VERSION;
      }

      @Override
      public @NotNull IndexId<CompilerRef, Integer> getName() {
        return BACK_USAGES;
      }

      @Override
      public @NotNull DataIndexer<CompilerRef, Integer, CompiledFileData> getIndexer() {
        return CompiledFileData::getReferences;
      }

      @Override
      public @NotNull KeyDescriptor<CompilerRef> getKeyDescriptor() {
        return CompilerRefDescriptor.INSTANCE;
      }

      @Override
      public @NotNull DataExternalizer<Integer> getValueExternalizer() {
        return new UnsignedByteExternalizer();
      }
    };
  }

  private static final class UnsignedByteExternalizer implements DataExternalizer<Integer> {
    @Override
    public void save(@NotNull DataOutput out, Integer value) throws IOException {
      int v = value;
      if (v > 255) {
        v = 255;
      }
      out.writeByte(v);
    }

    @Override
    public Integer read(@NotNull DataInput in) throws IOException {
      return in.readByte() & 0xFF;
    }
  }

  private static IndexExtension<CompilerRef, Collection<CompilerRef>, CompiledFileData> createBackwardHierarchyExtension() {
    return new IndexExtension<CompilerRef, Collection<CompilerRef>, CompiledFileData>() {
      @Override
      public int getVersion() {
        return VERSION;
      }

      @Override
      public @NotNull IndexId<CompilerRef, Collection<CompilerRef>> getName() {
        return BACK_HIERARCHY;
      }

      @Override
      public @NotNull DataIndexer<CompilerRef, Collection<CompilerRef>, CompiledFileData> getIndexer() {
        return CompiledFileData::getBackwardHierarchy;
      }

      @Override
      public @NotNull KeyDescriptor<CompilerRef> getKeyDescriptor() {
        return CompilerRefDescriptor.INSTANCE;
      }

      @Override
      public @NotNull DataExternalizer<Collection<CompilerRef>> getValueExternalizer() {
        return createCompilerRefSeqExternalizer();
      }
    };
  }

  private static IndexExtension<CompilerRef, Void, CompiledFileData> createBackwardClassDefinitionExtension() {
    return new IndexExtension<CompilerRef, Void, CompiledFileData>() {
      @Override
      public int getVersion() {
        return VERSION;
      }

      @Override
      public @NotNull IndexId<CompilerRef, Void> getName() {
        return BACK_CLASS_DEF;
      }

      @Override
      public @NotNull DataIndexer<CompilerRef, Void, CompiledFileData> getIndexer() {
        return CompiledFileData::getDefinitions;
      }

      @Override
      public @NotNull KeyDescriptor<CompilerRef> getKeyDescriptor() {
        return CompilerRefDescriptor.INSTANCE;
      }

      @Override
      public @NotNull DataExternalizer<Void> getValueExternalizer() {
        return VoidDataExternalizer.INSTANCE;
      }
    };
  }

  private static IndexExtension<SignatureData, Collection<CompilerRef>, CompiledFileData> createBackwardSignatureExtension() {
    return new IndexExtension<SignatureData, Collection<CompilerRef>, CompiledFileData>() {
      @Override
      public @NotNull IndexId<SignatureData, Collection<CompilerRef>> getName() {
        return BACK_MEMBER_SIGN;
      }

      @Override
      public @NotNull DataIndexer<SignatureData, Collection<CompilerRef>, CompiledFileData> getIndexer() {
        return CompiledFileData::getSignatureData;
      }

      @Override
      public @NotNull KeyDescriptor<SignatureData> getKeyDescriptor() {
        return createSignatureDataDescriptor();
      }

      @Override
      public @NotNull DataExternalizer<Collection<CompilerRef>> getValueExternalizer() {
        return createCompilerRefSeqExternalizer();
      }

      @Override
      public int getVersion() {
        return VERSION;
      }
    };
  }

  private static @NotNull DataExternalizer<Collection<CompilerRef>> createCompilerRefSeqExternalizer() {
    return new DataExternalizer<Collection<CompilerRef>>() {
      @Override
      public void save(final @NotNull DataOutput out, Collection<CompilerRef> value) throws IOException {
        DataInputOutputUtilRt.writeSeq(out, value, lightRef -> CompilerRefDescriptor.INSTANCE.save(out, lightRef));
      }

      @Override
      public Collection<CompilerRef> read(final @NotNull DataInput in) throws IOException {
        return DataInputOutputUtilRt.readSeq(in, () -> CompilerRefDescriptor.INSTANCE.read(in));
      }
    };
  }

  private static KeyDescriptor<SignatureData> createSignatureDataDescriptor() {
    return new KeyDescriptor<SignatureData>() {
      @Override
      public int getHashCode(SignatureData value) {
        return value.hashCode();
      }

      @Override
      public boolean isEqual(SignatureData val1, SignatureData val2) {
        return val1.equals(val2);
      }

      @Override
      public void save(@NotNull DataOutput out, SignatureData value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.getRawReturnType());
        out.writeByte(value.getIteratorKind());
        out.writeBoolean(value.isStatic());
      }

      @Override
      public SignatureData read(@NotNull DataInput in) throws IOException {
        return new SignatureData(DataInputOutputUtil.readINT(in), in.readByte(), in.readBoolean());
      }
    };
  }
}
