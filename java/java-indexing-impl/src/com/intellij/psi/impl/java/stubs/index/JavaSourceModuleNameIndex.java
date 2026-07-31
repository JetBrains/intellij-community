// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.ScalarIndexExtension;
import com.intellij.util.indexing.hints.FileTypeInputFilterPredicate;
import com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

/**
 * Maps MANIFEST.MF files to the auto-module name they declare (or derive from the JAR filename). <p>
 * Covers two cases: <ul>
 * <li>MANIFEST.MF with `Automatic-Module-Name`: indexed under the declared name</li>
 * <li>MANIFEST.MF without `Automatic-Module-Name`, inside an actual JAR root: indexed under the filename-derived name</li>
 * </ul>
 * JARs without any MANIFEST.MF are handled by {@link JavaAutoModuleNameIndex}.<p>
 * A MANIFEST.MF found under a source root (not a real JAR) without `Automatic-Module-Name` contributes
 * nothing here; such a module keeps resolving under its normal default name (see {@link com.intellij.psi.impl.search.JavaModuleSearcher}).
 */
public final class JavaSourceModuleNameIndex extends ScalarIndexExtension<String> {
  private static final ID<String, Void> NAME = ID.create("java.source.module.name");

  static final String META_INF_DIR_NAME = JarFile.MANIFEST_NAME.substring(0, JarFile.MANIFEST_NAME.indexOf('/'));
  static final String MANIFEST_FILE_NAME = JarFile.MANIFEST_NAME.substring(JarFile.MANIFEST_NAME.indexOf('/') + 1);

  private final DataIndexer<String, Void, FileContent> myIndexer = data -> {
    try {
      String name = new Manifest(new ByteArrayInputStream(data.getContent()))
                        .getMainAttributes().getValue(PsiJavaModule.AUTO_MODULE_NAME);
      if (name != null) return singletonMap(name, null);
      // fallback: derive from JAR filename, but only for an actual JAR root (not a source root's META-INF)
      VirtualFile metaInf = data.getFile().getParent();
      if (metaInf == null || !META_INF_DIR_NAME.equalsIgnoreCase(metaInf.getName())) return emptyMap();
      VirtualFile jarRoot = metaInf.getParent();
      if (jarRoot == null || jarRoot.getParent() != null || !"jar".equalsIgnoreCase(jarRoot.getExtension())) return emptyMap();
      return singletonMap(LightJavaModule.moduleName(jarRoot.getNameWithoutExtension()), null);
    }
    catch (IOException ignored) {
      return emptyMap();
    }
  };

  @Override
  public @NotNull ID<String, Void> getName() {
    return NAME;
  }

  @Override
  public int getVersion() {
    return 6;
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    FileType manifestFileType = FileTypeRegistry.getInstance().getFileTypeByFileName("Manifest.mf");
    return new FileTypeInputFilterPredicate(FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION, type -> {
      return type.equals(manifestFileType);
    });
  }

  @Override
  public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
    return myIndexer;
  }

  @Override
  public @NotNull Collection<FileType> getFileTypesWithSizeLimitNotApplicable() {
    return Collections.singleton(JavaClassFileType.INSTANCE);
  }

  public static @NotNull Collection<VirtualFile> getFilesByKey(@NotNull String moduleName, @NotNull GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, moduleName, new JavaAutoModuleFilterScope(scope));
  }

  public static @NotNull @Unmodifiable Collection<String> getAllKeys(@NotNull Project project) {
    return FileBasedIndex.getInstance().getAllKeys(NAME, project);
  }
}
