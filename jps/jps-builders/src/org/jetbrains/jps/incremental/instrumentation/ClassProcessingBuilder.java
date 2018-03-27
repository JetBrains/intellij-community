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

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public abstract class ClassProcessingBuilder extends ModuleLevelBuilder {
  private static final Key<InstrumentationClassFinder> CLASS_FINDER = Key.create("_cached_instrumentation_class_finder_");

  public ClassProcessingBuilder(BuilderCategory category) {
    super(category);
  }

  protected abstract boolean isEnabled(CompileContext context, ModuleChunk chunk);

  protected abstract String getProgressMessage();

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    final InstrumentationClassFinder finder = CLASS_FINDER.get(context);
    if (finder != null) {
      CLASS_FINDER.set(context, null);
      finder.releaseResources();
    }
  }

  @Override
  public final ExitCode build(CompileContext context, ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder, OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    if (outputConsumer.getCompiledClasses().isEmpty() || !isEnabled(context, chunk)) {
      return ExitCode.NOTHING_DONE;
    }

    final String progress = getProgressMessage();
    final boolean shouldShowProgress = !StringUtil.isEmptyOrSpaces(progress);
    if (shouldShowProgress) {
      context.processMessage(new ProgressMessage(progress + " [" + chunk.getPresentableShortName() + "]"));
    }

    ExitCode exitCode = ExitCode.NOTHING_DONE;
    try {
      InstrumentationClassFinder finder = CLASS_FINDER.get(context); // try using shared finder
      if (finder == null) {
        final Collection<File> platformCp = ProjectPaths.getPlatformCompilationClasspath(chunk, false);
        final Collection<File> classpath = new ArrayList<>();
        classpath.addAll(ProjectPaths.getCompilationClasspath(chunk, false));
        classpath.addAll(ProjectPaths.getSourceRootsWithDependents(chunk).keySet());
        final JpsSdk<JpsDummyElement> sdk = chunk.representativeTarget().getModule().getSdk(JpsJavaSdkType.INSTANCE);
        finder = createInstrumentationClassFinder(sdk, platformCp, classpath, outputConsumer);
        CLASS_FINDER.set(context, finder);
      }

      exitCode = performBuild(context, chunk, finder, outputConsumer);
    }
    finally {
      if (shouldShowProgress) {
        context.processMessage(new ProgressMessage("")); // cleanup progress
      }
    }
    return exitCode;
  }

  @Override
  public List<String> getCompilableFileExtensions() {
    return Collections.emptyList();
  }

  protected abstract ExitCode performBuild(CompileContext context, ModuleChunk chunk, InstrumentationClassFinder finder, OutputConsumer outputConsumer);


  // utility methods
  public static InstrumentationClassFinder createInstrumentationClassFinder(@Nullable JpsSdk<?> sdk,
                                                                            Collection<File> platformCp,
                                                                            Collection<File> cp,
                                                                            final OutputConsumer outputConsumer) throws
                                                                                                                                                                   MalformedURLException {
    final URL[] platformUrls;
    int index = 0;
    if (sdk != null && JpsJavaSdkType.getJavaVersion(sdk) >= 9) {
      platformUrls = new URL[1 + platformCp.size()];
      platformUrls[index++] = InstrumentationClassFinder.createJDKPlatformUrl(sdk.getHomePath());
    }
    else {
      platformUrls = new URL[platformCp.size()];
    }

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
    final Ref<Integer> result = new Ref<>(0);
    reader.accept(new ClassVisitor(Opcodes.API_VERSION) {
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        result.set(version);
      }
    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return result.get();
  }

}
