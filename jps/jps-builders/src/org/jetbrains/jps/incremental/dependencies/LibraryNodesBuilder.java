// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.dependencies;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphConfiguration;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.java.JVMClassNode;
import org.jetbrains.jps.dependency.java.JvmClassNodeBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class LibraryNodesBuilder {
  private final GraphConfiguration myGraphConfig;

  LibraryNodesBuilder(GraphConfiguration graphConfig) {
    myGraphConfig = graphConfig;
  }

  public Iterable<Node<?, ?>> processLibraryRoot(final String namespace, NodeSource libRoot) throws IOException {
    if (!LibraryDef.isLibraryPath(libRoot)) {
      return Collections.emptyList();
    }
    Path jarPath = myGraphConfig.getPathMapper().toPath(libRoot);
    List<Node<?, ?>> nodes = new ArrayList<>();
    URI uri = URI.create("jar:" + jarPath.toUri());
    try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
      for (Path root : fs.getRootDirectories()) {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(@NotNull Path file, BasicFileAttributes attrs) throws IOException {
            if (LibraryDef.isClassFile(getFileName(file))) {
              addNode(file, namespace, nodes);
            }
            return FileVisitResult.CONTINUE;
          }
        });
      }
    }
    return nodes;
  }

  private static void addNode(@NotNull Path classFile, String namespace, List<Node<?, ?>> acc) throws IOException {
    FailSafeClassReader reader = new FailSafeClassReader(Files.readAllBytes(classFile));
    JVMClassNode<?, ?> node = JvmClassNodeBuilder.createForLibrary("$" + namespace + FileUtil.toSystemIndependentName(classFile.toString()), reader).getResult();
    if (node.getFlags().isPublic()) {
      // todo: maybe too restrictive
      acc.add(node);
    }
  }

  private static String getFileName(@NotNull Path path) {
    Path fname = path.getFileName();
    return fname == null? "" : fname.toString();
  }
}
