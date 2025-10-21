package com.intellij.tools.build.bazel.jvmIncBuilder.impl.instrumentation;

import com.intellij.tools.build.bazel.jvmIncBuilder.*;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumenterClassWriter;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.CompilerRunner;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputFile;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jetbrains.jps.util.Iterators.filter;

public class BytecodeInstrumentationRunner implements CompilerRunner {
  private static final Logger LOG = Logger.getLogger("com.intellij.tools.build.bazel.jvmIncBuilder.impl.InstrumenterRunner");

  private static final List<BytecodeInstrumenter> ourInstrumenters = List.of(
    new ThreadingModelInstrumenter(), new NotNullInstrumenter()
  );

  private final StorageManager myStorageManager;

  public BytecodeInstrumentationRunner(BuildContext context, StorageManager storageManager) {
    myStorageManager = storageManager;
  }

  @Override
  public boolean canCompile(NodeSource src) {
    return false;
  }

  @Override
  public ExitCode compile(Iterable<NodeSource> sources, Iterable<NodeSource> deletedSources, DiagnosticSink diagnostic, OutputSink outSink) throws Exception {
    for (OutputOrigin.Kind originKind : OutputOrigin.Kind.values()) {
      for (String generatedFile : outSink.getGeneratedOutputPaths(originKind, OutputFile.Kind.bytecode)) {
        boolean changes = false;
        byte[] content = null;
        ClassReader reader = null;

        for (BytecodeInstrumenter instrumenter : filter(ourInstrumenters, inst -> inst.getSupportedOrigins().contains(originKind))) {
          if (content == null) {
            content = outSink.getFileContent(generatedFile);
          }
          try {
            if (reader == null) {
              reader = new FailSafeClassReader(content);
            }
            InstrumentationClassFinder classFinder = myStorageManager.getInstrumentationClassFinder();
            int version = InstrumenterClassWriter.getClassFileVersion(reader);
            ClassWriter writer = new InstrumenterClassWriter(reader, InstrumenterClassWriter.getAsmClassWriterFlags(version), classFinder);
            final byte[] instrumented = instrumenter.instrument(generatedFile, reader, writer, classFinder);
            if (instrumented != null) {
              changes = true;
              content = instrumented;
              classFinder.cleanCachedData(reader.getClassName());
              reader = null;
            }
          }
          catch (Exception e) {
            LOG.log(Level.WARNING, "Error running instrumenter " + instrumenter.getName(), e);
            diagnostic.report(Message.create(instrumenter, Message.Kind.ERROR, e.getMessage(), generatedFile));
            break;
          }
        }
        
        if (changes && !diagnostic.hasErrors()) {
          myStorageManager.getOutputBuilder().putEntry(generatedFile, content);
        }
      }
    }
    return ExitCode.OK;
  }

  @Override
  public String getName() {
    return "Bytecode Instrumentation";
  }
}
