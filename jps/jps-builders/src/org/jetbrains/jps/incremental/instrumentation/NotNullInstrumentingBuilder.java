// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.instrumentation;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.JvmClassFileInstrumenter;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.File;
import java.util.Collection;

@ApiStatus.Internal
public final class NotNullInstrumentingBuilder extends BaseInstrumentingBuilder implements JvmClassFileInstrumenter {
  private static final Logger LOG = Logger.getInstance(NotNullInstrumentingBuilder.class);
  private boolean myIsEnabled;
  private String[] myNotNulls;

  public NotNullInstrumentingBuilder() {
  }

  @Override
  public @NotNull String getId() {
    return "not-null-assertions";
  }

  @Override
  public boolean isEnabled(@NotNull ProjectDescriptor projectDescriptor, @NotNull JpsModule module) {
    JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(projectDescriptor.getProject());
    return config.isAddNotNullAssertions();
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @Override
  public void buildStarted(CompileContext context) {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(pd.getProject());
    myIsEnabled = config.isAddNotNullAssertions();
    myNotNulls = ArrayUtilRt.toStringArray(config.getNotNullAnnotations());
  }

  @Override
  public @NotNull String getPresentableName() {
    return JpsBuildBundle.message("builder.name.notnull.instrumentation");
  }

  @Override
  protected String getProgressMessage() {
    return JpsBuildBundle.message("progress.message.adding.notnull.assertions");
  }

  @Override
  protected boolean isEnabled(CompileContext context, ModuleChunk chunk) {
    return myIsEnabled;
  }

  @Override
  protected boolean canInstrument(CompiledClass compiledClass, int classFileVersion) {
    return (classFileVersion & 0xFFFF) >= Opcodes.V1_5 && !"module-info".equals(compiledClass.getClassName());
  }

  @Override
  protected @Nullable BinaryContent instrument(CompileContext context,
                                               CompiledClass compiledClass,
                                               ClassReader reader,
                                               ClassWriter writer,
                                               InstrumentationClassFinder finder) {
    try {
      if (NotNullVerifyingInstrumenter.processClassFile(reader, writer, myNotNulls)) {
        return new BinaryContent(writer.toByteArray());
      }
    }
    catch (Throwable e) {
      LOG.error(e);
      final Collection<File> sourceFiles = compiledClass.getSourceFiles();
      final String msg = JpsBuildBundle.message("build.message.cannot.instrument.0.1", ContainerUtil.map(sourceFiles, file -> file.getName()), e.getMessage());
      context.processMessage(new CompilerMessage(
        getPresentableName(), BuildMessage.Kind.ERROR, msg, ContainerUtil.getFirstItem(compiledClass.getSourceFilesPaths())
      ));
    }
    return null;
  }

}
