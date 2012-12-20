package org.jetbrains.jps.incremental.instrumentation;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassWriter;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: 11/21/12
 */
public class NotNullInstrumentingBuilder extends BaseInstrumentingBuilder{

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
    return classFileVersion >= Opcodes.V1_5;
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
      final NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer);
      reader.accept(instrumenter, 0);
      if (instrumenter.isModification()) {
        return new BinaryContent(writer.toByteArray());
      }
    }
    catch (Throwable e) {
      final StringBuilder msg = new StringBuilder();
      msg.append("@NotNull instrumentation failed ");
      final File sourceFile = compiledClass.getSourceFile();
      msg.append(" for ").append(sourceFile.getName());
      msg.append(": ").append(e.getMessage());
      context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, msg.toString(), sourceFile.getPath()));
    }
    return null;
  }

}
