package org.jetbrains.jps.incremental.instrumentation;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;
import org.jetbrains.jps.model.java.compiler.RmicCompilerOptions;

import java.io.File;
import java.io.IOException;
import java.rmi.Remote;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: 11/30/12
 */
public class RmiStubsGenerator extends ClassProcessingBuilder {
  private static final String REMOTE_INTERFACE_NAME = Remote.class.getName();
  private static Key<Boolean> IS_ENABLED = Key.create("_rmic_compiler_enabled_");

  public RmiStubsGenerator() {
    super(BuilderCategory.CLASS_INSTRUMENTER);
  }

  @Override
  protected String getProgressMessage() {
    return "Generating RMI stubs...";
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "rmic";
  }

  @Override
  public void buildStarted(CompileContext context) {
    super.buildStarted(context);
    boolean enabled = false;
    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(context.getProjectDescriptor().getProject());
    if (config != null) {
      final JpsJavaCompilerOptions options = config.getCompilerOptions("Rmic");
      enabled = options instanceof RmicCompilerOptions && ((RmicCompilerOptions)options).IS_EANABLED;
    }
    IS_ENABLED.set(context, enabled);
  }

  @Override
  protected boolean isEnabled(CompileContext context, ModuleChunk chunk) {
    return IS_ENABLED.get(context, Boolean.FALSE);
  }

  @Override
  protected ExitCode performBuild(CompileContext context, ModuleChunk chunk, InstrumentationClassFinder finder, OutputConsumer outputConsumer) {
    ExitCode exitCode = ExitCode.NOTHING_DONE;
    if (!outputConsumer.getCompiledClasses().isEmpty()) {
      final Map<BuildTarget<?>, Collection<ClassItem>> remoteClasses = new THashMap<BuildTarget<?>, Collection<ClassItem>>();
      for (ModuleBuildTarget target : chunk.getTargets()) {
        for (CompiledClass compiledClass : outputConsumer.getTargetCompiledClasses(target)) {
          try {
            if (isRemote(compiledClass, finder)) {
              Collection<ClassItem> list = remoteClasses.get(target);
              if (list ==  null) {
                list = new ArrayList<ClassItem>();
                remoteClasses.put(target, list);
              }
              list.add(new ClassItem(compiledClass));
            }
          }
          catch (IOException e) {
            context.processMessage(new CompilerMessage(getPresentableName(), e));
          }
        }
      }
      if (!remoteClasses.isEmpty()) {
        exitCode = generateRmiStubs(remoteClasses, chunk, outputConsumer);
      }
    }
    return exitCode;
  }

  private ExitCode generateRmiStubs(Map<BuildTarget<?>, Collection<ClassItem>> remoteClasses, ModuleChunk chunk, OutputConsumer outputConsumer) {
    ExitCode exitCode = ExitCode.NOTHING_DONE;
    // todo: start rmic compiler
    return exitCode;
  }

  private static boolean isRemote(CompiledClass compiled, InstrumentationClassFinder finder) throws IOException{
    try {
      final InstrumentationClassFinder.PseudoClass pseudoClass = finder.loadClass(compiled.getClassName());
      if (pseudoClass != null) {
        for (InstrumentationClassFinder.PseudoClass anInterface : pseudoClass.getInterfaces()) {
          if (isRemoteInterface(anInterface, REMOTE_INTERFACE_NAME)) {
            return true;
          }
        }
      }
    }
    catch (ClassNotFoundException ignored) {
    }
    return false;
  }

  private static boolean isRemoteInterface(InstrumentationClassFinder.PseudoClass iface, final String remoteInterfaceName)
    throws IOException, ClassNotFoundException {
    if (remoteInterfaceName.equals(iface.getName())) {
      return true;
    }
    for (InstrumentationClassFinder.PseudoClass superIface : iface.getInterfaces()) {
      if (isRemoteInterface(superIface, remoteInterfaceName)) {
        return true;
      }
    }
    return false;
  }

  private static final class ClassItem {
    final CompiledClass compiledClass;
    final File stubFile;
    final File skelFile;
    final File tieFile;

    private ClassItem(CompiledClass compiledClass) {
      this.compiledClass = compiledClass;
      final File outputFile = compiledClass.getOutputFile();
      final File parent = outputFile.getParentFile();
      final String baseName = StringUtil.trimEnd(outputFile.getName(), ".class");
      stubFile = new File(parent, baseName + "_Stub.class");
      skelFile = new File(parent, baseName + "_Skel.class");
      tieFile = new File(parent, baseName + "_Tie.class");
    }
  }

}
