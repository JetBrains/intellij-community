// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.instrumentation;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;

/**
 * @author Eugene Zhuravlev
 */
public abstract class BaseInstrumentingBuilder extends ClassProcessingBuilder {
  private static final Logger LOG = Logger.getInstance(BaseInstrumentingBuilder.class);

  // every instance of builder must have its own marker!
  private final Key<Boolean> IS_INSTRUMENTED_KEY = Key.create("_instrumentation_marker_" + getPresentableName());

  public BaseInstrumentingBuilder() {
    super(BuilderCategory.CLASS_INSTRUMENTER);
  }

  @Override
  protected final ExitCode performBuild(CompileContext context, ModuleChunk chunk, InstrumentationClassFinder finder, OutputConsumer outputConsumer) {
    ExitCode exitCode = ExitCode.NOTHING_DONE;
    for (CompiledClass compiledClass : outputConsumer.getCompiledClasses().values()) {
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.debug("checking " + compiledClass + " by " + getClass());
      }
      final BinaryContent originalContent = compiledClass.getContent();
      final ClassReader reader = new FailSafeClassReader(originalContent.getBuffer(), originalContent.getOffset(), originalContent.getLength());
      final int version = InstrumenterClassWriter.getClassFileVersion(reader);
      if (IS_INSTRUMENTED_KEY.get(compiledClass, Boolean.FALSE) || !canInstrument(compiledClass, version)) {
        // do not instrument the same content twice
        continue;
      }
      final ClassWriter writer = new InstrumenterClassWriter(reader, InstrumenterClassWriter.getAsmClassWriterFlags(version), finder);
      try {
        if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
          LOG.debug("instrumenting " + compiledClass + " by " + getClass());
        }
        final BinaryContent instrumented = instrument(context, compiledClass, reader, writer, finder);
        if (instrumented != null) {
          compiledClass.setContent(instrumented);
          String className = compiledClass.getClassName();
          assert className != null : compiledClass;
          finder.cleanCachedData(className);
          IS_INSTRUMENTED_KEY.set(compiledClass, Boolean.TRUE);
          exitCode = ExitCode.OK;
        }
      }
      catch (Throwable e) {
        LOG.info(e);
        final String message = e.getMessage();
        if (message != null) {
          String sourcePath = ContainerUtil.getFirstItem(compiledClass.getSourceFilesPaths());
          context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, message, sourcePath));
        }
        else {
          context.processMessage(CompilerMessage.createInternalCompilationError(getPresentableName(), e));
        }
      }
    }
    return exitCode;
  }

  protected abstract boolean canInstrument(CompiledClass compiledClass, int classFileVersion);

  @Nullable
  protected abstract BinaryContent instrument(CompileContext context,
                                              CompiledClass compiled,
                                              ClassReader reader,
                                              ClassWriter writer,
                                              InstrumentationClassFinder finder);
}