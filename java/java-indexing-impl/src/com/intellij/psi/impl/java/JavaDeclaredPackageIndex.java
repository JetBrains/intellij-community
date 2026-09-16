// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An index of the packages which a {@code package} statement declares, and of their direct subpackage names.
 * <p>
 * A key is a package name. A value is the name of a direct subpackage, or {@code null} when the file declares exactly the key.
 * A file with {@code package org.classa;} therefore adds these entries:
 * <pre>
 * ""           -&gt; "org"
 * "org"        -&gt; "classa"
 * "org.classa" -&gt; null
 * </pre>
 * A declared package does not have to correspond to the directory of the file, so {@link com.intellij.openapi.roots.PackageIndex}
 * cannot answer for it. {@link DeclaredPackageElementFinder} uses this index instead.
 */
public final class JavaDeclaredPackageIndex extends FileBasedIndexExtension<String, String> {
  public static final ID<String, String> INDEX_ID = ID.create("java.declared.package");

  /**
   * @return true if some file declares {@code packageName} or a subpackage of it
   */
  public static boolean packageExists(@NotNull String packageName, @NotNull GlobalSearchScope scope) {
    // processValues returns false as soon as the processor does, so a single entry is enough to answer
    return !FileBasedIndex.getInstance().processValues(INDEX_ID, packageName, null, (_, _) -> false, scope);
  }

  /**
   * @return the names of the direct subpackages of {@code packageName}, without the qualifier
   */
  public static @NotNull Set<String> getSubPackageNames(@NotNull String packageName, @NotNull GlobalSearchScope scope) {
    Set<String> result = new HashSet<>();
    for (String subPackageName : FileBasedIndex.getInstance().getValues(INDEX_ID, packageName, scope)) {
      // a null value marks a file which declares exactly packageName; a blank name comes from a broken package statement
      if (subPackageName != null && !subPackageName.isBlank()) {
        result.add(subPackageName);
      }
    }
    return result;
  }

  /**
   * @return the files which declare exactly {@code packageName}
   */
  public static @NotNull List<VirtualFile> getFilesWithExactPackage(@NotNull String packageName,
                                                                    @NotNull GlobalSearchScope scope) {
    List<VirtualFile> result = new ArrayList<>();
    FileBasedIndex.getInstance().processValues(INDEX_ID, packageName, null, (file, value) -> {
      if (value == null) {
        result.add(file);
      }
      return true;
    }, scope);
    return result;
  }

  @Override
  public @NotNull ID<String, String> getName() {
    return INDEX_ID;
  }

  @Override
  public @NotNull DataIndexer<String, String, FileContent> getIndexer() {
    return inputData -> {
      if (!(inputData.getPsiFile() instanceof PsiJavaFile javaFile)) return Map.of();
      String packageName = javaFile.getPackageName();
      Map<String, String> result = new HashMap<>();
      result.put(packageName, null);
      int end = packageName.length();
      while (end > 0) {
        int dot = packageName.lastIndexOf('.', end - 1);
        result.put(dot < 0 ? "" : packageName.substring(0, dot), packageName.substring(dot + 1, end));
        end = dot;
      }
      return result;
    };
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull DataExternalizer<String> getValueExternalizer() {
    return new DataExternalizer<>() {
      @Override
      public void save(@NotNull DataOutput out, @Nullable String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
          IOUtil.writeUTF(out, value);
        }
      }

      @Override
      public @Nullable String read(@NotNull DataInput in) throws IOException {
        return in.readBoolean() ? IOUtil.readUTF(in) : null;
      }
    };
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    // only a file in a source tree can declare a package which does not correspond to its directory
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
}
