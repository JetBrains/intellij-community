/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental.instrumentation;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
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
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.instrumentation.NotNullInstrumentingBuilder");

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
    return JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(pd.getProject()).isAddNotNullAssertions();
  }

  @Override
  protected boolean canInstrument(CompiledClass compiledClass, int classFileVersion) {
    return classFileVersion >= Opcodes.V1_5 && !"module-info".equals(compiledClass.getClassName());
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
      final List<String> notNulls = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(pd.getProject()).getNotNullAnnotations();
      if (NotNullVerifyingInstrumenter.processClassFile((FailSafeClassReader)reader, writer, ArrayUtil.toStringArray(notNulls))) {
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
