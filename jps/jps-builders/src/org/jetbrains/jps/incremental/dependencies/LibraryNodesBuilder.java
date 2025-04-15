// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.dependencies;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.util.lang.HashMapZipFile;
import com.intellij.util.lang.ImmutableZipEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphConfiguration;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.java.JVMClassNode;
import org.jetbrains.jps.dependency.java.JvmClassNodeBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class LibraryNodesBuilder {
  private final GraphConfiguration myGraphConfig;

  LibraryNodesBuilder(GraphConfiguration graphConfig) {
    myGraphConfig = graphConfig;
  }

  public Iterable<Node<?, ?>> processLibraryRoot(@NotNull String namespace, NodeSource libRoot) throws IOException {
    if (!LibraryDef.isLibraryPath(libRoot)) {
      return List.of();
    }

    Path jarPath = myGraphConfig.getPathMapper().toPath(libRoot);
    HashMapZipFile zipFile = HashMapZipFile.loadIfNotEmpty(jarPath);
    if (zipFile == null) {
      return List.of();
    }

    List<Node<?, ?>> nodes = new ArrayList<>();
    try (zipFile) {
      for (ImmutableZipEntry entry : zipFile.getEntries()) {
        if (!entry.isDirectory() && LibraryDef.isClassFile(entry.getName())) {
          addNode(entry.getData(zipFile), entry.getName(), namespace, nodes);
        }
      }
    }
    catch (IOException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return nodes;
  }

  private static void addNode(byte @NotNull [] classFileData, @NotNull String classFileName, @NotNull String namespace, List<Node<?, ?>> acc)
    throws IOException {
    FailSafeClassReader reader = new FailSafeClassReader(classFileData);
    JVMClassNode<?, ?> node = JvmClassNodeBuilder.createForLibrary("$" + namespace + "/" + classFileName, reader).getResult();
    if (node.isPublic() || node.isProtected()) {
      acc.add(node);
    }
  }
}
