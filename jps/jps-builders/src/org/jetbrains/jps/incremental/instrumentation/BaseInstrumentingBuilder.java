package org.jetbrains.jps.incremental.instrumentation;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassWriter;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.javac.BinaryContent;

/**
 * @author Eugene Zhuravlev
 *         Date: 11/25/12
 */
public abstract class BaseInstrumentingBuilder extends ClassProcessingBuilder {
  // every instance of builder must have its own marker!
  private final Key<Boolean> IS_INSTRUMENTED_KEY = Key.create("_instrumentation_marker_" + getPresentableName());

  public BaseInstrumentingBuilder() {
    super(BuilderCategory.CLASS_INSTRUMENTER);
  }

  @Override
  protected final ExitCode performBuild(CompileContext context, ModuleChunk chunk, InstrumentationClassFinder finder, OutputConsumer outputConsumer) {
    ExitCode exitCode = ExitCode.NOTHING_DONE;
    for (CompiledClass compiledClass : outputConsumer.getCompiledClasses().values()) {
      final BinaryContent originalContent = compiledClass.getContent();
      final ClassReader reader = new ClassReader(originalContent.getBuffer(), originalContent.getOffset(), originalContent.getLength());
      final int version = getClassFileVersion(reader);
      if (IS_INSTRUMENTED_KEY.get(compiledClass, Boolean.FALSE) || !canInstrument(compiledClass, version)) {
        // do not instrument the same content twice
        continue;
      }
      final ClassWriter writer = new InstrumenterClassWriter(getAsmClassWriterFlags(version), finder);
      final BinaryContent instrumented = instrument(context, compiledClass, reader, writer, finder);
      if (instrumented != null) {
        compiledClass.setContent(instrumented);
        IS_INSTRUMENTED_KEY.set(compiledClass, Boolean.TRUE);
        exitCode = ExitCode.OK;
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
