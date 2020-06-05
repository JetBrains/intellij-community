// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.instrumentation;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class NotNullInstrumentingBuilder extends BaseInstrumentingBuilder{
  private static final Logger LOG = Logger.getInstance(NotNullInstrumentingBuilder.class);

  public NotNullInstrumentingBuilder() {
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "NotNull instrumentation";
  }

  @Override
  protected String getProgressMessage() {
    return "Adding @NotNull assertions...";
  }

  @Override
  protected boolean isEnabled(CompileContext context, ModuleChunk chunk) {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    return JpsJavaExtensionService.getInstance().getCompilerConfiguration(pd.getProject()).isAddNotNullAssertions();
  }

  @Override
  protected boolean canInstrument(CompiledClass compiledClass, int classFileVersion) {
    return (classFileVersion & 0xFFFF) >= Opcodes.V1_5 && !"module-info".equals(compiledClass.getClassName());
  }

  // todo: probably instrument other NotNull-like annotations defined in project settings?
  @Override
  @Nullable
  protected BinaryContent instrument(CompileContext context,
                                     CompiledClass compiledClass,
                                     ClassReader reader,
                                     ClassWriter writer,
                                     InstrumentationClassFinder finder) {
    try {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      final List<String> notNulls = JpsJavaExtensionService.getInstance().getCompilerConfiguration(pd.getProject()).getNotNullAnnotations();
      if (NotNullVerifyingInstrumenter.processClassFile(reader, writer, ArrayUtilRt.toStringArray(notNulls))) {
        return new BinaryContent(writer.toByteArray());
      }
    }
    catch (Throwable e) {
      LOG.error(e);
      final Collection<File> sourceFiles = compiledClass.getSourceFiles();
      String msg = "Cannot instrument " + ContainerUtil.map(sourceFiles, file -> file.getName()) + ": " + e.getMessage();
      context.processMessage(new CompilerMessage(getPresentableName(),
                                                 BuildMessage.Kind.ERROR,
                                                 msg,
                                                 ContainerUtil.getFirstItem(compiledClass.getSourceFilesPaths())));
    }
    return null;
  }

}
