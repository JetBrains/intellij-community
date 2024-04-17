// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.java.*;
import org.jetbrains.jps.javac.Iterators;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.nio.file.Path;
import java.util.*;

final class BackendCallbackToGraphDeltaAdapter implements Callbacks.Backend {

  private static final String IMPORT_WILDCARD_SUFFIX = ".*";
  // className -> {imports; static_imports}
  private final Map<String, Pair<Collection<String>, Collection<String>>> myImportRefs = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, Collection<Callbacks.ConstantRef>> myConstantRefs = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, Set<Usage>> myAdditionalUsages = Collections.synchronizedMap(new HashMap<>());
  private final Map<Path, Set<Usage>> myPerSourceAdditionalUsages = Collections.synchronizedMap(new HashMap<>());
  private final List<Pair<Node<?, ?>, Iterable<NodeSource>>> myNodes = new ArrayList<>();
  private final GraphConfiguration myGraphConfig;

  BackendCallbackToGraphDeltaAdapter(GraphConfiguration graphConfig) {
    myGraphConfig = graphConfig;
  }

  @Override
  public void associate(String classFileName, Collection<String> sources, ClassReader cr, boolean isGenerated) {
    JvmClassNodeBuilder builder = JvmClassNodeBuilder.create(classFileName, cr, isGenerated);

    JvmNodeReferenceID nodeID = builder.getReferenceID();
    String nodeName = nodeID.getNodeName();
    addConstantUsages(builder, nodeName, myConstantRefs.remove(nodeName));
    Pair<Collection<String>, Collection<String>> imports = myImportRefs.remove(nodeName);
    if (imports != null) {
      addImportUsages(builder, imports.getFirst(), imports.getSecond());
    }
    Set<Usage> additionalUsages = myAdditionalUsages.remove(nodeName);
    if (additionalUsages != null) {
      for (Usage usage : additionalUsages) {
        builder.addUsage(usage);
      }
    }
    for (Usage usage : Iterators.flat(Iterators.map(sources, src -> myPerSourceAdditionalUsages.get(Path.of(src))))) {
      builder.addUsage(usage);
    }
    var node = builder.getResult();
    if (!node.isPrivate()) {
      myNodes.add(new Pair<>(node, Iterators.collect(Iterators.map(sources, myGraphConfig.getPathMapper()::toNodeSource), new SmartList<>())));
    }
  }

  public List<Pair<Node<?, ?>, Iterable<NodeSource>>> getNodes() {
    try {
      NodeSourcePathMapper pathMapper = myGraphConfig.getPathMapper();
      for (NodeSource source : Iterators.flat(Iterators.map(myNodes, p -> p.getSecond()))) {
        myPerSourceAdditionalUsages.remove(pathMapper.toPath(source));
      }
      for (Map.Entry<Path, Set<Usage>> entry : myPerSourceAdditionalUsages.entrySet()) {
        NodeSource src = pathMapper.toNodeSource(entry.getKey());
        myNodes.add(new Pair<>(new FileNode(src.toString(), entry.getValue()), List.of(src)));
      }
      return myNodes;
    }
    finally {
      myPerSourceAdditionalUsages.clear();
    }
  }

  @Override
  public void registerImports(String className, Collection<String> classImports, Collection<String> staticImports) {
    final String key = className.replace('.', '/');
    if (!classImports.isEmpty() || !staticImports.isEmpty()) {
      myImportRefs.put(key, Pair.create(classImports, staticImports));
    }
    else {
      myImportRefs.remove(key);
    }
  }

  @Override
  public void registerConstantReferences(String className, Collection<Callbacks.ConstantRef> cRefs) {
    final String key = className.replace('.', '/');
    if (!cRefs.isEmpty()) {
      myConstantRefs.put(key, cRefs);
    }
    else {
      myConstantRefs.remove(key);
    }
  }

  @Override
  public void registerUsage(String className, Usage usage) {
    myAdditionalUsages.computeIfAbsent(className.replace('.', '/'), k -> Collections.synchronizedSet(new HashSet<>())).add(usage);
  }

  @Override
  public void registerUsage(Path source, Usage usage) {
    myPerSourceAdditionalUsages.computeIfAbsent(source, k -> Collections.synchronizedSet(new HashSet<>())).add(usage);
  }

  private static void addImportUsages(JvmClassNodeBuilder builder, Collection<String> classImports, Collection<String> staticImports) {
    for (final String anImport : classImports) {
      if (anImport.endsWith(IMPORT_WILDCARD_SUFFIX)) {
        builder.addUsage(new ImportPackageOnDemandUsage(anImport.substring(0, anImport.length() - IMPORT_WILDCARD_SUFFIX.length()).replace('.', '/')));
      }
      else {
        builder.addUsage(new ClassUsage(anImport.replace('.', '/')));
      }
    }
    for (String anImport : staticImports) {
      if (anImport.endsWith(IMPORT_WILDCARD_SUFFIX)) {
        final String iname = anImport.substring(0, anImport.length() - IMPORT_WILDCARD_SUFFIX.length()).replace('.', '/');
        builder.addUsage(new ClassUsage(iname));
        builder.addUsage(new ImportStaticOnDemandUsage(iname));
      }
      else {
        final int i = anImport.lastIndexOf('.');
        if (i > 0 && i < anImport.length() - 1) {
          final String iname = anImport.substring(0, i).replace('.', '/');
          final String memberName = anImport.substring(i + 1);
          builder.addUsage(new ClassUsage(iname));
          builder.addUsage(new ImportStaticMemberUsage(iname, memberName));
        }
      }
    }
  }

  private static void addConstantUsages(JvmClassNodeBuilder builder, String nodeName, Collection<? extends Callbacks.ConstantRef> cRefs) {
    if (cRefs != null) {
      for (Callbacks.ConstantRef ref : cRefs) {
        final String constantOwner = ref.getOwner().replace('.', '/');
        if (!constantOwner.equals(nodeName)) {
          builder.addUsage(new FieldUsage(constantOwner, ref.getName(), ref.getDescriptor()));
        }
      }
    }
  }

}
