// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.openapi.util.Pair;
import kotlin.metadata.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.bazel.DiagnosticSink;
import org.jetbrains.jps.bazel.Message;
import org.jetbrains.jps.bazel.ZipOutputBuilder;
import org.jetbrains.jps.bazel.runner.BytecodeInstrumenter;
import org.jetbrains.jps.bazel.runner.OutputSink;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.java.*;
import org.jetbrains.jps.javac.Iterators;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.util.*;

public class OutputSinkImpl implements OutputSink {
  private static final String IMPORT_WILDCARD_SUFFIX = ".*";
  private final DiagnosticSink myDiagnostic;
  private final ZipOutputBuilder myOutBuilder;
  @Nullable
  private final AbiJarBuilder myAbiOutputBuilder;
  private final List<BytecodeInstrumenter> myInstrumenters;

  // -----------------------------------------------------------
  private final Map<String, Pair<Collection<String>, Collection<String>>> myImportRefs = new HashMap<>();
  private final Map<String, Collection<ConstantRef>> myConstantRefs = new HashMap<>();
  private final Map<String, Set<Usage>> myAdditionalUsages = new HashMap<>();
  private final Map<NodeSource, Set<Usage>> myPerSourceAdditionalUsages = new HashMap<>();
  private final List<Pair<Node<?, ?>, Iterable<NodeSource>>> myNodes = new ArrayList<>();
  private final Map<NodeSource, Set<Usage>> mySelfUsages = new HashMap<>();

  public OutputSinkImpl(DiagnosticSink diagnostic, ZipOutputBuilder outBuilder, @Nullable AbiJarBuilder abiOutputBuilder, List<BytecodeInstrumenter> instrumenters) {
    myDiagnostic = diagnostic;
    myOutBuilder = outBuilder;
    myAbiOutputBuilder = abiOutputBuilder;
    myInstrumenters = instrumenters;
  }

  @Override
  public void addFile(OutputFile outFile, Iterable<NodeSource> originSources) {
    // todo: make sure the outFile.getPath() is relative to output root
    processAndSave(outFile, originSources);
  }

  @Override
  public boolean deletePath(String path) {
    if (myAbiOutputBuilder != null) {
      myAbiOutputBuilder.deleteEntry(path);
    }
    return myOutBuilder.deleteEntry(path);
  }

  @Override
  public Iterable<String> listFiles(String packageName, boolean recurse) {
    String dirName = packageName.replace('.', '/') + "/";
    var result = recurse? Iterators.recurse(dirName, myOutBuilder::listEntries, false) : myOutBuilder.listEntries(dirName);
    return Iterators.filter(result, n -> !ZipOutputBuilder.isDirectoryName(n));
  }

  @Override
  public byte @Nullable [] getFileContent(String path) {
    return myOutBuilder.getContent(path);
  }

  private void processAndSave(OutputFile outFile, Iterable<NodeSource> originSources) {
    byte[] content = outFile.getContent();
    myOutBuilder.putEntry(outFile.getPath(), content); // make file content immediately available
    if (myAbiOutputBuilder != null) {
      myAbiOutputBuilder.putEntry(outFile.getPath(), content);
    }

    if (outFile.getKind() == OutputFile.Kind.bytecode) {
      // todo: parse/instrument files and create nodes asynchronously?
      ClassReader reader = new FailSafeClassReader(content);
      associate(outFile.getPath(), originSources, reader, outFile.isFromGeneratedSource());

      InstrumentationClassFinder finder = getInstrumentationClassFinder();
      if (finder != null) {
        boolean changes = false;
        for (BytecodeInstrumenter instrumenter : myInstrumenters) {
          try {
            if (reader == null) {
              reader = new FailSafeClassReader(content);
            }
            int version = InstrumenterClassWriter.getClassFileVersion(reader);
            ClassWriter writer = new InstrumenterClassWriter(reader, InstrumenterClassWriter.getAsmClassWriterFlags(version), finder);
            final byte[] instrumented = instrumenter.instrument(outFile.getPath(), reader, writer, finder);
            if (instrumented != null) {
              changes = true;
              content = instrumented;
              finder.cleanCachedData(reader.getClassName());
              reader = null;
            }
          }
          catch (Exception e) {
            // todo: better diagnostics?
            myDiagnostic.report(Message.error(instrumenter, e.getMessage()));
          }
        }
        if (changes) {
          myOutBuilder.putEntry(outFile.getPath(), content);
          if (myAbiOutputBuilder != null) {
            myAbiOutputBuilder.putEntry(outFile.getPath(), content);
          }
        }
      }
    }
  }

  private InstrumentationClassFinder getInstrumentationClassFinder() {
    return null; // todo: required for the instrumentation
  }

  private void associate(String classFileName, Iterable<NodeSource> sources, ClassReader cr, boolean isGenerated) {
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
        mySelfUsages.computeIfAbsent(src, s -> new HashSet<>()).add(lookup);
      }
    }

    myNodes.add(new Pair<>(node, sources));
  }

  public List<Pair<Node<?, ?>, Iterable<NodeSource>>> getNodes() {

    if (!myPerSourceAdditionalUsages.isEmpty()) {
      for (Map.Entry<NodeSource, Set<Usage>> entry : myPerSourceAdditionalUsages.entrySet()) {
        NodeSource src = entry.getKey();
        Set<Usage> usages = entry.getValue();
        Set<Usage> selfUsages = mySelfUsages.get(src);
        if (selfUsages != null) {
          usages.removeAll(selfUsages);
        }
        myNodes.add(new Pair<>(new FileNode(src.toString(), usages), List.of(src)));
      }
      myPerSourceAdditionalUsages.clear();
    }

    return myNodes;
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

}
