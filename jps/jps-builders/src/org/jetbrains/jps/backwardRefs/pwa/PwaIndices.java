// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.pwa;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class PwaIndices {
  public final static int VERSION = 0;
  public final static IndexId<ClassFileSymbol, Collection<ClassFileSymbol>> BACK_USAGES = IndexId.create("symbol.refs");
  public final static IndexId<ClassFileSymbol, Collection<ClassFileSymbol>> BACK_HIERARCHY = IndexId.create("symbol.hierarchy");
  public final static IndexId<ClassFileSymbol, Void> DEF = IndexId.create("symbol.defs");

  private static IndexExtension<ClassFileSymbol, Collection<ClassFileSymbol>, ClassFileData> createBackUsagesIndex() {
    return new IndexExtension<ClassFileSymbol, Collection<ClassFileSymbol>, ClassFileData>() {
      @NotNull
      @Override
      public IndexId<ClassFileSymbol, Collection<ClassFileSymbol>> getName() {
        return BACK_USAGES;
      }

      @NotNull
      @Override
      public DataIndexer<ClassFileSymbol, Collection<ClassFileSymbol>, ClassFileData> getIndexer() {
        return ClassFileData::getUsageMap;
      }

      @NotNull
      @Override
      public KeyDescriptor<ClassFileSymbol> getKeyDescriptor() {
        return ClassFileSymbol.EXTERNALIZER;
      }

      @NotNull
      @Override
      public DataExternalizer<Collection<ClassFileSymbol>> getValueExternalizer() {
        return seqExternalizer();
      }

      @Override
      public int getVersion() {
        return VERSION;
      }
    };
  }

  private static IndexExtension<ClassFileSymbol, Collection<ClassFileSymbol>, ClassFileData> createBackHierarchyIndex() {
    return new IndexExtension<ClassFileSymbol, Collection<ClassFileSymbol>, ClassFileData>() {
      @NotNull
      @Override
      public IndexId<ClassFileSymbol, Collection<ClassFileSymbol>> getName() {
        return BACK_HIERARCHY;
      }

      @NotNull
      @Override
      public DataIndexer<ClassFileSymbol, Collection<ClassFileSymbol>, ClassFileData> getIndexer() {
        return ClassFileData::getHierarchy;
      }

      @NotNull
      @Override
      public KeyDescriptor<ClassFileSymbol> getKeyDescriptor() {
        return ClassFileSymbol.EXTERNALIZER;
      }

      @NotNull
      @Override
      public DataExternalizer<Collection<ClassFileSymbol>> getValueExternalizer() {
        return seqExternalizer();
      }



      @Override
      public int getVersion() {
        return VERSION;
      }
    };
  }

  public static Collection<IndexExtension<?, ?, ClassFileData>> getIndices() {
    return ContainerUtil.list(createBackUsagesIndex(), createBackHierarchyIndex(), createDefsIndex());
  }

  private static IndexExtension<ClassFileSymbol, Void, ClassFileData> createDefsIndex() {
    return new IndexExtension<ClassFileSymbol, Void, ClassFileData>() {
      @NotNull
      @Override
      public IndexId<ClassFileSymbol, Void> getName() {
        return DEF;
      }

      @NotNull
      @Override
      public DataIndexer<ClassFileSymbol, Void, ClassFileData> getIndexer() {
        return ClassFileData::getDefs;
      }

      @NotNull
      @Override
      public KeyDescriptor<ClassFileSymbol> getKeyDescriptor() {
        return ClassFileSymbol.EXTERNALIZER;
      }

      @NotNull
      @Override
      public DataExternalizer<Void> getValueExternalizer() {
        return VoidDataExternalizer.INSTANCE;
      }

      @Override
      public int getVersion() {
        return 0;
      }
    };
  }

  @NotNull
  private static DataExternalizer<Collection<ClassFileSymbol>> seqExternalizer() {
    return new DataExternalizer<Collection<ClassFileSymbol>>() {
      @Override
      public void save(@NotNull DataOutput out, Collection<ClassFileSymbol> value) throws IOException {
        DataInputOutputUtilRt.writeSeq(out, value, symbol -> ClassFileSymbol.EXTERNALIZER.save(out, symbol));
      }

      @Override
      public Collection<ClassFileSymbol> read(@NotNull DataInput in) throws IOException {
        return DataInputOutputUtilRt.readSeq(in, () -> ClassFileSymbol.EXTERNALIZER.read(in));
      }
    };
  }
}
