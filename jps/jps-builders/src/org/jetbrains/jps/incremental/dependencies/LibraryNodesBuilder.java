// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.dependencies;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.impl.PathSourceMapper;
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

  public Iterable<Node<?, ?>> processLibraryRoot(final String libName, NodeSource libRoot) throws IOException {
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
              addNode(file, libName, nodes);
            }
            return FileVisitResult.CONTINUE;
          }
        });
      }
    }
    return nodes;
  }

  private static void addNode(@NotNull Path classFile, String libraryName, List<Node<?, ?>> acc) throws IOException {
    FailSafeClassReader reader = new FailSafeClassReader(Files.readAllBytes(classFile));
    JVMClassNode<?, ?> node = JvmClassNodeBuilder.createForLibrary("$" + libraryName + FileUtil.toSystemIndependentName(classFile.toString()), reader).getResult();
    if (node.getFlags().isPublic()) {
      // todo: maybe too restrictive
      acc.add(node);
    }
  }

  private static String getFileName(@NotNull Path path) {
    Path fname = path.getFileName();
    return fname == null? "" : fname.toString();
  }

  public static void main(String[] args) throws IOException {
    String jarPath = args[0];
    GraphConfiguration graphConfig = new GraphConfiguration() {
      private final PathSourceMapper mapper = new PathSourceMapper();

      @Override
      public @NotNull NodeSourcePathMapper getPathMapper() {
        return mapper;
      }

      @Override
      public @NotNull DependencyGraph getGraph() {
        return null;
      }
    };
    LibraryNodesBuilder builder = new LibraryNodesBuilder(graphConfig);
    NodeSource src = graphConfig.getPathMapper().toNodeSource(jarPath);
    int count = 0;
    for (Node<?, ?> node : builder.processLibraryRoot("generic-lib", src)) {
      count += 1;
      System.out.println(node.getReferenceID() + " from " + src);
    }
    System.out.println("Total " + count + " nodes");
  }
}
