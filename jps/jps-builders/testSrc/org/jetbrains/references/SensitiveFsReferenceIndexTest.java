// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.references;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.CompilerRef;
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter;
import org.jetbrains.jps.backwardRefs.JavaCompilerBackwardReferenceIndex;
import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.builders.TestProjectBuilderLogger;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;

import static org.jetbrains.jps.backwardRefs.index.JavaCompilerIndices.BACK_USAGES;

public class SensitiveFsReferenceIndexTest extends ReferenceIndexTestBase {

  @Override
  public void setUp() {
    super.setUp();
    System.setProperty(JavaBackwardReferenceIndexWriter.FS_KEY, "true");
  }

  @Override
  protected @Nullable String getTestDataRootPath() {
    return FileUtil.toCanonicalPath(PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/referencesIndex").getAbsolutePath(),
                                    '/');
  }

  public void testCaseSensitivity() throws StorageException {
    assertIndexOnRebuild("pack/Foo.java", "pack/Bar.java");

    ProjectDescriptor descriptor = createProjectDescriptor(new BuildLoggingManager(new TestProjectBuilderLogger()));
    BuildDataManager manager = descriptor.dataManager;
    Path root = manager.getDataPaths().getDataStorageDir();
    JavaCompilerBackwardReferenceIndex index = new JavaCompilerBackwardReferenceIndex(root,
                                                                                      new PathRelativizerService(myProject, true), true,
                                                                                      true);
    PersistentStringEnumerator filePathEnumerator = index.getFilePathEnumerator();

    HashSet<String> fileNames = new HashSet<>();
    fileNames.add("Foo.java");
    IndexStorage<CompilerRef, Integer> storage =
      ((MapReduceIndex<CompilerRef, Integer, CompiledFileData>)index.get(BACK_USAGES)).getStorage();
    ((MapIndexStorage<CompilerRef, Integer>)storage).processKeys(usage -> {
      try {
        index.get(BACK_USAGES).withData(usage, data -> {
            data.forEach((id, value) -> {
              try {
                String fullName = filePathEnumerator.valueOf(id);
                String fileName = PathUtil.getFileName(fullName);
                fileNames.remove(fileName);
              }
              catch (IOException e) {
                throw new RuntimeException(e);
              }
              return false;
            });
            return true;
        });
      }
      catch (StorageException e) {
        throw new RuntimeException(e);
      }
      return false;
    });

    Assertions.assertTrue(fileNames.isEmpty());
  }
}
