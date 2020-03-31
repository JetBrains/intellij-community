// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.java;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.javac.OutputFileConsumer;
import org.jetbrains.jps.javac.OutputFileObject;
import org.jetbrains.org.objectweb.asm.ClassReader;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
* @author Eugene Zhuravlev
*/
class OutputFilesSink implements OutputFileConsumer {
  private static final Logger LOG = Logger.getInstance(OutputFilesSink.class);
  private final CompileContext myContext;
  private final ModuleLevelBuilder.OutputConsumer myOutputConsumer;
  private final Callbacks.Backend myMappingsCallback;
  private final String myChunkName;
  private final Set<File> mySuccessfullyCompiled = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);

  OutputFilesSink(CompileContext context,
                         ModuleLevelBuilder.OutputConsumer outputConsumer,
                         Callbacks.Backend callback,
                         String chunkName) {
    myContext = context;
    myOutputConsumer = outputConsumer;
    myMappingsCallback = callback;
    myChunkName = "[" +chunkName + "]";
  }

  @Override
  public void save(final @NotNull OutputFileObject fileObject) {
    final BinaryContent content = fileObject.getContent();
    final File srcFile = fileObject.getSourceFile();
    boolean isTemp = false;
    final JavaFileObject.Kind outKind = fileObject.getKind();

    if (srcFile != null && content != null) {
      final String sourcePath = FileUtil.toSystemIndependentName(srcFile.getPath());
      final JavaSourceRootDescriptor rootDescriptor = myContext.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(myContext, srcFile);
      try {
        if (rootDescriptor != null) {
          isTemp = rootDescriptor.isTemp;
          if (!isTemp) {
            // first, handle [src->output] mapping and register paths for files_generated event
            if (outKind == JavaFileObject.Kind.CLASS) {
              myOutputConsumer.registerCompiledClass(rootDescriptor.target, new CompiledClass(fileObject.getFile(), srcFile, fileObject.getClassName(), content)); // todo: avoid array copying?
            }
            else {
              myOutputConsumer.registerOutputFile(rootDescriptor.target, fileObject.getFile(), Collections.singleton(sourcePath));
            }
          }
        }
        else {
          // was not able to determine the source root descriptor or the source root is excluded from compilation (e.g. for annotation processors)
          if (outKind == JavaFileObject.Kind.CLASS) {
            myOutputConsumer.registerCompiledClass(null, new CompiledClass(fileObject.getFile(), srcFile, fileObject.getClassName(), content));
          }
        }
      }
      catch (IOException e) {
        myContext.processMessage(new CompilerMessage(JavaBuilder.BUILDER_NAME, e));
      }

      if (!isTemp && outKind == JavaFileObject.Kind.CLASS) {
        // register in mappings any non-temp class file
        try {
          final ClassReader reader = new FailSafeClassReader(content.getBuffer(), content.getOffset(), content.getLength());
          myMappingsCallback.associate(FileUtil.toSystemIndependentName(fileObject.getFile().getPath()), sourcePath, reader);
        }
        catch (Throwable e) {
          // need this to make sure that unexpected errors in, for example, ASM will not ruin the compilation
          final String message = "Class dependency information may be incomplete! Error parsing generated class " + fileObject.getFile().getPath();
          LOG.info(message, e);
          myContext.processMessage(new CompilerMessage(
            JavaBuilder.BUILDER_NAME, BuildMessage.Kind.WARNING, message + "\n" + CompilerMessage.getTextFromThrowable(e), sourcePath)
          );
        }
      }
    }

    if (outKind == JavaFileObject.Kind.CLASS) {
      myContext.processMessage(new ProgressMessage("Writing classes... " + myChunkName));
      if (!isTemp && srcFile != null) {
        mySuccessfullyCompiled.add(srcFile);
      }
    }
  }

  public Set<File> getSuccessfullyCompiled() {
    return Collections.unmodifiableSet(mySuccessfullyCompiled);
  }

  public void markError(@NotNull final File sourceFile) {
    mySuccessfullyCompiled.remove(sourceFile);
  }
  public void markError(@NotNull final Set<File> problematic) {
    mySuccessfullyCompiled.removeAll(problematic);
  }
}
