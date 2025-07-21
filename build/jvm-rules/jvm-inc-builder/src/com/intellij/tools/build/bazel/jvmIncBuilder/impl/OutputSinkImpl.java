// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.StorageManager;
import com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputFile;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink;
import kotlin.metadata.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.java.*;
import org.jetbrains.jps.util.Iterators;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.util.*;

public class OutputSinkImpl implements OutputSink {

  private static final String IMPORT_WILDCARD_SUFFIX = ".*";
  private final ZipOutputBuilder myOut;
  private @Nullable final ZipOutputBuilder myJavaAbiOut;
  private final Map<OutputOrigin.Kind, Map<OutputFile.Kind, Set<String>>> myOutputsIndex = new EnumMap<>(OutputOrigin.Kind.class);

  // -----------------------------------------------------------
  private final Map<String, Collection<String>> myClassImportRefs = new HashMap<>();
  private final Map<String, Collection<String>> myStaticImportRefs = new HashMap<>();
  private final Map<String, Collection<ConstantRef>> myConstantRefs = new HashMap<>();
  private final Map<String, Set<Usage>> myAdditionalUsages = new HashMap<>();
  private final Map<NodeSource, Set<Usage>> myPerSourceAdditionalUsages = new HashMap<>();
  private final List<BuilderWithSources> myBuilders = new ArrayList<>();
  private final List<NodeWithSources> myNodes = new ArrayList<>();

  public OutputSinkImpl(StorageManager sm) throws IOException {
    myOut = sm.getOutputBuilder();
    ZipOutputBuilderImpl abiOut = sm.getAbiOutputBuilder();
    myJavaAbiOut = abiOut != null? new JavaAbiFilter(abiOut) : null;
  }

  @Override
  public void addFile(OutputFile outFile, OutputOrigin origin) {
    // todo: make sure the outFile.getPath() is relative to output root
    processAndSave(outFile, origin);
  }

  @Override
  public Iterable<String> getGeneratedOutputPaths(OutputOrigin.Kind originKind, OutputFile.Kind outputKind) {
    return getOutputs(originKind, outputKind);
  }

  @Override
  public Iterable<String> listFiles(String packageName, boolean recurse) {
    String dirName = packageName.replace('.', '/') + "/";
    var result = recurse? Iterators.recurse(dirName, myOut::listEntries, false) : myOut.listEntries(dirName);
    return Iterators.filter(result, n -> !ZipOutputBuilder.isDirectoryName(n));
  }

  @Override
  public Iterable<String> list(String packageName, boolean recurse) {
    String dirName = packageName.replace('.', '/') + "/";
    var result = recurse? Iterators.recurse(dirName, myOut::listEntries, false) : myOut.listEntries(dirName);
    return result;
  }

  @Override
  public byte @Nullable [] getFileContent(String path) {
    return myOut.getContent(path);
  }

  private void processAndSave(OutputFile outFile, OutputOrigin origin) {
    getOutputs(origin.getKind(), outFile.getKind()).add(outFile.getPath());
    byte[] content = outFile.getContent();
    // make file content immediately available so that the instrumenter ClassFinder are able to access the original version
    myOut.putEntry(outFile.getPath(), content);

    if (origin.getKind() == OutputOrigin.Kind.java && myJavaAbiOut != null) {
      // for kotlin the ABI output is produced separately by the dedicated compiler plugin
      myJavaAbiOut.putEntry(outFile.getPath(), content);
    }

    if (outFile.getKind() == OutputFile.Kind.bytecode) {
      // todo: parse/instrument files and create nodes asynchronously?
      associate(outFile.getPath(), origin.getSources(), new FailSafeClassReader(content), outFile.isFromGeneratedSource());
    }
  }

  private record BuilderWithSources(JvmClassNodeBuilder builder, Iterable<NodeSource> sources) {}

  private void associate(String classFileName, Iterable<NodeSource> sources, ClassReader cr, boolean isGenerated) {
    myBuilders.add(new BuilderWithSources(
      JvmClassNodeBuilder.create(classFileName, cr, isGenerated),
      sources
    ));
  }

  @Override
  public Iterable<NodeWithSources> getNodes() {
    Map<NodeSource, Set<Usage>> fileLocalUsages = new HashMap<>();

    for (BuilderWithSources bs : myBuilders) {
      JvmClassNodeBuilder builder = bs.builder();
      Iterable<NodeSource> sources = bs.sources();

      JvmNodeReferenceID nodeID = builder.getReferenceID();
      String nodeName = nodeID.getNodeName();
      addConstantUsages(builder, nodeName, myConstantRefs.remove(nodeName));
      Collection<String> classImports = myClassImportRefs.remove(nodeName);
      Collection<String> staticImports = myStaticImportRefs.remove(nodeName);
      if (classImports != null || staticImports != null) {
        addImportUsages(builder, classImports != null? classImports : Set.of(), staticImports != null? staticImports : Set.of());
      }
      Set<Usage> additionalUsages = myAdditionalUsages.remove(nodeName);
      if (additionalUsages != null) {
        for (Usage usage : additionalUsages) {
          builder.addUsage(usage);
        }
      }

      var node = builder.getResult();

      Iterable<LookupNameUsage> lookups = Iterators.flat(Iterators.map(node.getMetadata(KotlinMeta.class), meta -> {
        KmDeclarationContainer container = meta.getDeclarationContainer();
        final JvmNodeReferenceID owner;
        LookupNameUsage clsUsage = null;
        if (container instanceof KmPackage) {
          owner = new JvmNodeReferenceID(JvmClass.getPackageName(node.getName()));
        }
        else if (container instanceof KmClass) {
          owner = new JvmNodeReferenceID(((KmClass)container).getName());
          String ownerName = owner.getNodeName();
          String scopeName = JvmClass.getPackageName(ownerName);
          String symbolName = scopeName.isEmpty()? ownerName : ownerName.substring(scopeName.length() + 1);
          clsUsage = new LookupNameUsage(scopeName, symbolName);
        }
        else {
          owner = null;
        }
        if (owner == null) {
          return Collections.emptyList();
        }
        Iterable<LookupNameUsage> memberLookups =
          Iterators.map(Iterators.unique(Iterators.flat(Iterators.map(container.getFunctions(), KmFunction::getName), Iterators.map(container.getProperties(), KmProperty::getName))), name -> new LookupNameUsage(owner, name));
        return clsUsage == null? memberLookups : Iterators.flat(Iterators.asIterable(clsUsage), memberLookups);
      }));

      for (LookupNameUsage lookup : lookups) {
        for (NodeSource src : sources) {
          fileLocalUsages.computeIfAbsent(src, s -> new HashSet<>()).add(lookup);
        }
      }

      myNodes.add(new NodeWithSources(node, sources));
    }
    myBuilders.clear();

    for (Map.Entry<NodeSource, Set<Usage>> entry : myPerSourceAdditionalUsages.entrySet()) {
      NodeSource src = entry.getKey();
      Set<Usage> usages = entry.getValue();
      Set<Usage> selfUsages = fileLocalUsages.get(src);
      if (selfUsages != null) {
        usages.removeAll(selfUsages);
      }
      myNodes.add(new NodeWithSources(new FileNode(src.toString(), usages), List.of(src)));
    }
    myPerSourceAdditionalUsages.clear();

    return myNodes;
  }

  @Override
  public void registerImports(String className, Collection<String> classImports, Collection<String> staticImports) {
    final String key = className.replace('.', '/');
    if (!classImports.isEmpty()) {
      myClassImportRefs.put(key, classImports);
    }
    else {
      myClassImportRefs.remove(key);
    }
    if (!staticImports.isEmpty()) {
      myStaticImportRefs.put(key, staticImports);
    }
    else {
      myStaticImportRefs.remove(key);
    }
  }

  @Override
  public void registerConstantReferences(String className, Collection<ConstantRef> cRefs) {
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
  public void registerUsage(NodeSource source, Usage usage) {
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

  private static void addConstantUsages(JvmClassNodeBuilder builder, String nodeName, Collection<? extends ConstantRef> cRefs) {
    if (cRefs != null) {
      for (ConstantRef ref : cRefs) {
        final String constantOwner = ref.getOwner().replace('.', '/');
        if (!constantOwner.equals(nodeName)) {
          builder.addUsage(new FieldUsage(constantOwner, ref.getName(), ref.getDescriptor()));
        }
      }
    }
  }

  private @NotNull Set<String> getOutputs(OutputOrigin.Kind originKind, OutputFile.Kind outputKind) {
    return getOutputMap(originKind).computeIfAbsent(outputKind, k -> new HashSet<>());
  }

  private Map<OutputFile.Kind, Set<String>> getOutputMap(OutputOrigin.Kind origin) {
    return myOutputsIndex.computeIfAbsent(origin, k -> new EnumMap<>(OutputFile.Kind.class));
  }
}
