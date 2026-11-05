// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisSuppressor;
import com.intellij.codeInspection.bytecodeAnalysis.ClassDataIndexer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.util.gist.VirtualFileGist;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class ClassDataIndexerInitializationTest {
  @Test
  void testInitializationDoesNotReadSuppressorVersions(@TestDisposable Disposable disposable) throws Exception {
    var suppressor = new BytecodeAnalysisSuppressor() {
      @Override
      public int getVersion() {
        throw new ProcessCanceledException();
      }

      @Override
      public boolean shouldSuppress(@NotNull VirtualFile file) {
        return true;
      }
    };
    ExtensionTestUtil.maskExtensions(BytecodeAnalysisSuppressor.EP_NAME, List.of(suppressor), disposable);

    var loader = new ClassLoader(ClassDataIndexer.class.getClassLoader()) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.startsWith("com.intellij.codeInspection.bytecodeAnalysis.") ||
            name.equals(BytecodeAnalysisSuppressor.class.getName())) {
          return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
          var loaded = findLoadedClass(name);
          if (loaded == null) {
            try (var stream = getResourceAsStream(name.replace('.', '/') + ".class")) {
              if (stream == null) throw new ClassNotFoundException(name);
              var bytes = stream.readAllBytes();
              loaded = defineClass(name, bytes, 0, bytes.length);
            }
            catch (IOException e) {
              throw new ClassNotFoundException(name, e);
            }
          }
          if (resolve) resolveClass(loaded);
          return loaded;
        }
      }
    };
    var indexerClass = Class.forName(ClassDataIndexer.class.getName(), true, loader);
    assertSame(loader, indexerClass.getClassLoader());
    var indexer = (VirtualFileGist.GistCalculator<?>)indexerClass.getConstructor().newInstance();
    assertEquals(Map.of(), indexer.calcData(null, new LightVirtualFile("Test.class")));
  }
}
