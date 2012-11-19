package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.javac.BinaryContent;
import org.jetbrains.jps.javac.OutputFileConsumer;
import org.jetbrains.jps.javac.OutputFileObject;

import javax.tools.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;

/**
* @author Eugene Zhuravlev
*         Date: 2/16/12
*/
class OutputFilesSink implements OutputFileConsumer {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.java.OutputFilesSink");

  private final CompileContext myContext;
  private final ModuleLevelBuilder.OutputConsumer myOutputConsumer;
  private final Callbacks.Backend myMappingsCallback;
  private final Set<File> mySuccessfullyCompiled = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);

  public OutputFilesSink(CompileContext context, ModuleLevelBuilder.OutputConsumer outputConsumer, Callbacks.Backend callback) {
    myContext = context;
    myOutputConsumer = outputConsumer;
    myMappingsCallback = callback;
  }

  public void save(final @NotNull OutputFileObject fileObject) {
    final BinaryContent content = fileObject.getContent();
    final File srcFile = fileObject.getSourceFile();
    boolean isTemp = false;
    final JavaFileObject.Kind outKind = fileObject.getKind();

    if (srcFile != null && content != null) {
      final String sourcePath = FileUtil.toSystemIndependentName(srcFile.getPath());
      final JavaSourceRootDescriptor rootDescriptor = myContext.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(myContext, srcFile);
      if (rootDescriptor != null) {
        isTemp = rootDescriptor.isTemp;
        if (!isTemp) {
          // first, handle [src->output] mapping and register paths for files_generated event
          try {
            if (outKind == JavaFileObject.Kind.CLASS) {
              myOutputConsumer.registerCompiledClass(rootDescriptor.target, new CompiledClass(fileObject.getFile(), srcFile, fileObject.getClassName(), content)); // todo: avoid array copying?
            }
            else {
              myOutputConsumer.registerOutputFile(rootDescriptor.target, fileObject.getFile(), Collections.<String>singleton(sourcePath));
            }
          }
          catch (IOException e) {
            myContext.processMessage(new CompilerMessage(JavaBuilder.BUILDER_NAME, e));
          }
        }
      }

      if (!isTemp && outKind == JavaFileObject.Kind.CLASS && !Utils.errorsDetected(myContext)) {
        // register in mappings any non-temp class file
        final ClassReader reader = new ClassReader(content.getBuffer(), content.getOffset(), content.getLength());
        myMappingsCallback.associate(FileUtil.toSystemIndependentName(fileObject.getFile().getPath()), sourcePath, reader);
      }
    }

    if (!isTemp && outKind != JavaFileObject.Kind.CLASS && outKind != JavaFileObject.Kind.SOURCE) {
      try {
        // this should be a generated resource
        FSOperations.markDirty(myContext, fileObject.getFile());
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    try {
      writeToDisk(fileObject, isTemp);
    }
    catch (IOException e) {
      myContext.processMessage(new CompilerMessage(JavaBuilder.BUILDER_NAME, BuildMessage.Kind.ERROR, e.getMessage()));
    }
  }

  public Set<File> getSuccessfullyCompiled() {
    return Collections.unmodifiableSet(mySuccessfullyCompiled);
  }

  private void writeToDisk(@NotNull OutputFileObject fileObject, boolean isTemp) throws IOException {
    final File file = fileObject.getFile();
    final BinaryContent content = fileObject.getContent();
    if (content == null) {
      throw new IOException("Missing content for file " + file);
    }

    try {
      _writeToFile(file, content);
    }
    catch (IOException e) {
      // assuming the reason is non-existing parent
      final File parentFile = file.getParentFile();
      if (parentFile == null) {
        throw e;
      }
      if (!parentFile.mkdirs()) {
        throw e;
      }
      // second attempt
      _writeToFile(file, content);
    }
    
    final File source = fileObject.getSourceFile();
    if (!isTemp && source != null) {
      mySuccessfullyCompiled.add(source);
      final String className = fileObject.getClassName();
      if (className != null) {
        myContext.processMessage(new ProgressMessage("Compiled " + className));
      }
    }
  }

  private static void _writeToFile(final File file, BinaryContent content) throws IOException {
    final OutputStream stream = new FileOutputStream(file);
    try {
      stream.write(content.getBuffer(), content.getOffset(), content.getLength());
    }
    finally {
      stream.close();
    }
  }

  public void markError(@NotNull final File sourceFile) {
    mySuccessfullyCompiled.remove(sourceFile);
  }
  public void markError(@NotNull final Set<File> problematic) {
    mySuccessfullyCompiled.removeAll(problematic);
  }
}
