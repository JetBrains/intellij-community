package org.jetbrains.jps.incremental.instrumentation;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.ClassWriter;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.javac.BinaryContent;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: 11/25/12
 */
public abstract class BaseInstrumentingBuilder extends ModuleLevelBuilder{
  private static final Key<InstrumentationClassFinder> CLASS_FINDER = Key.create("_cached_instrumentation_class_finder_");

  public BaseInstrumentingBuilder() {
    super(BuilderCategory.CLASS_INSTRUMENTER);
  }

  @Override
  public void chunkBuildStarted(CompileContext context, ModuleChunk chunk) {
  }

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    final InstrumentationClassFinder finder = CLASS_FINDER.get(context);
    CLASS_FINDER.set(context, null);
    if (finder != null) {
      finder.releaseResources();
    }
  }

  @Override
  public final ExitCode build(CompileContext context, ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder, OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    ExitCode exitCode = ExitCode.NOTHING_DONE;
    if (outputConsumer.getCompiledClasses().isEmpty() || !isEnabled(context, chunk)) {
      return exitCode;
    }

    InstrumentationClassFinder finder = null;
    final String progress = getProgressMessage();
    final boolean shouldShowProgress = !StringUtil.isEmptyOrSpaces(progress);
    if (shouldShowProgress) {
      context.processMessage(new ProgressMessage(progress + " [" + chunk.getName() + "]"));
    }

    try {
      for (CompiledClass compiledClass : outputConsumer.getCompiledClasses().values()) {
        final BinaryContent originalContent = compiledClass.getContent();
        final ClassReader reader = new ClassReader(originalContent.getBuffer(), originalContent.getOffset(), originalContent.getLength());
        final int version = getClassFileVersion(reader);
        if (!canInstrument(compiledClass, version)) {
          continue;
        }

        if (finder == null) { // lazy init for this particular builder
          finder = CLASS_FINDER.get(context); // try using shared finder
          if (finder == null) {
            final ProjectPaths paths = context.getProjectPaths();
            final Collection<File> platformCp = paths.getPlatformCompilationClasspath(chunk, false);

            final Collection<File> classpath = new ArrayList<File>();
            classpath.addAll(paths.getCompilationClasspath(chunk, false));
            classpath.addAll(ProjectPaths.getSourceRootsWithDependents(chunk).keySet());

            finder = createInstrumentationClassFinder(platformCp, classpath, outputConsumer);
            CLASS_FINDER.set(context, finder);
          }
        }

        final ClassWriter writer = new InstrumenterClassWriter(getAsmClassWriterFlags(version), finder);
        final BinaryContent instrumented = instrument(context, compiledClass, reader, writer, finder);
        if (instrumented != null) {
          compiledClass.setContent(instrumented);
          exitCode = ExitCode.OK;
        }
      }
    }
    finally {
      if (shouldShowProgress) {
        context.processMessage(new ProgressMessage("")); // cleanup progress
      }
    }
    return exitCode;
  }

  protected abstract boolean isEnabled(CompileContext context, ModuleChunk chunk);

  protected abstract boolean canInstrument(CompiledClass compiledClass, int classFileVersion);

  @Nullable
  protected abstract BinaryContent instrument(CompileContext context,
                                              CompiledClass compiled,
                                              ClassReader reader,
                                              ClassWriter writer,
                                              InstrumentationClassFinder finder);

  protected abstract String getProgressMessage();
  // utility methods

  public static InstrumentationClassFinder createInstrumentationClassFinder(Collection<File> platformCp, Collection<File> cp, final OutputConsumer outputConsumer) throws MalformedURLException {
    final URL[] platformUrls = new URL[platformCp.size()];
    int index = 0;
    for (File file : platformCp) {
      platformUrls[index++] = file.toURI().toURL();
    }

    final URL[] urls = new URL[cp.size()];
    index = 0;
    for (File file : cp) {
      urls[index++] = file.toURI().toURL();
    }

    return new InstrumentationClassFinder(platformUrls, urls) {
      protected InputStream lookupClassBeforeClasspath(String internalClassName) {
        final BinaryContent content = outputConsumer.lookupClassBytes(internalClassName.replace("/", "."));
        if (content != null) {
          return new ByteArrayInputStream(content.getBuffer(), content.getOffset(), content.getLength());
        }
        return null;
      }
    };
  }

  public static int getAsmClassWriterFlags(int version) {
    return version >= Opcodes.V1_6 && version != Opcodes.V1_1 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS;
  }

  public static int getClassFileVersion(ClassReader reader) {
    final Ref<Integer> result = new Ref<Integer>(0);
    reader.accept(new ClassVisitor(Opcodes.ASM4) {
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        result.set(version);
      }
    }, 0);
    return result.get();
  }

}
