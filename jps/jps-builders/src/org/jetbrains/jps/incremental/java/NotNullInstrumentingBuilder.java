package org.jetbrains.jps.incremental.java;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.ClassWriter;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.javac.BinaryContent;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 11/21/12
 */
public class NotNullInstrumentingBuilder extends ModuleLevelBuilder{

  public NotNullInstrumentingBuilder() {
    super(BuilderCategory.CLASS_INSTRUMENTER);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "NotNull instrumentation";
  }

  @Override
  public ExitCode build(CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    ExitCode exitCode = ExitCode.NOTHING_DONE;

    final ProjectDescriptor pd = context.getProjectDescriptor();
    final boolean addNotNullAssertions = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(pd.getProject()).isAddNotNullAssertions();
    if (addNotNullAssertions) {
      final ProjectPaths paths = context.getProjectPaths();
      final Collection<File> classpath = paths.getCompilationClasspath(chunk, false);
      final Collection<File> platformCp = paths.getPlatformCompilationClasspath(chunk, false);
      final InstrumentationClassFinder finder = createInstrumentationClassFinder(platformCp, classpath, outputConsumer);
      try {
        try {
          context.processMessage(new ProgressMessage("Adding NotNull assertions [" + chunk.getName() + "]"));
          exitCode = instrumentNotNull(context, outputConsumer, finder);
        }
        finally {
          context.processMessage(new ProgressMessage("Finished adding NotNull assertions [" + chunk.getName() + "]"));
        }
      }
      finally {
        finder.releaseResources();
      }
    }
    return exitCode;
  }

  // todo: probably instrument other NotNull-like annotations defined in project settings?
  private ExitCode instrumentNotNull(CompileContext context, OutputConsumer outputConsumer, final InstrumentationClassFinder finder) {
    boolean doneSomething = false;
    for (final CompiledClass compiledClass : outputConsumer.getCompiledClasses().values()) {
      final BinaryContent originalContent = compiledClass.getContent();
      final ClassReader reader = new ClassReader(originalContent.getBuffer(), originalContent.getOffset(), originalContent.getLength());
      final int version = getClassFileVersion(reader);
      if (version >= Opcodes.V1_5) {
        final ClassWriter writer = new InstrumenterClassWriter(getAsmClassWriterFlags(version), finder);
        try {
          final NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer);
          reader.accept(instrumenter, 0);
          if (instrumenter.isModification()) {
            compiledClass.setContent(new BinaryContent(writer.toByteArray()));
            doneSomething = true;
          }
        }
        catch (Throwable e) {
          doneSomething = true;
          final StringBuilder msg = new StringBuilder();
          msg.append("@NotNull instrumentation failed ");
          final File sourceFile = compiledClass.getSourceFile();
          msg.append(" for ").append(sourceFile.getName());
          msg.append(": ").append(e.getMessage());
          context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, msg.toString(), sourceFile.getPath()));
        }
      }
    }
    return doneSomething? ExitCode.OK : ExitCode.NOTHING_DONE;
  }

  private static InstrumentationClassFinder createInstrumentationClassFinder(Collection<File> platformCp, Collection<File> classpath, final OutputConsumer outputConsumer) throws MalformedURLException {
    final URL[] platformUrls = new URL[platformCp.size()];
    int index = 0;
    for (File file : platformCp) {
      platformUrls[index++] = file.toURI().toURL();
    }

    final List<URL> urls = new ArrayList<URL>(classpath.size());
    for (File file : classpath) {
      urls.add(file.toURI().toURL());
    }

    return new InstrumentationClassFinder(platformUrls, urls.toArray(new URL[urls.size()])) {
      protected InputStream lookupClassBeforeClasspath(String internalClassName) {
        final BinaryContent content = outputConsumer.lookupClassBytes(internalClassName.replace("/", "."));
        if (content != null) {
          return new ByteArrayInputStream(content.getBuffer(), content.getOffset(), content.getLength());
        }
        return null;
      }
    };
  }

  private static int getClassFileVersion(ClassReader reader) {
    final Ref<Integer> result = new Ref<Integer>(0);
    reader.accept(new ClassVisitor(Opcodes.ASM4) {
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        result.set(version);
      }
    }, 0);
    return result.get();
  }

  private static int getAsmClassWriterFlags(int version) {
    return version >= Opcodes.V1_6 && version != Opcodes.V1_1 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS;
  }
}
