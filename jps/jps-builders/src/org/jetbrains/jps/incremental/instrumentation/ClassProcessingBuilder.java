// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.instrumentation;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
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

  protected abstract @Nls(capitalization = Nls.Capitalization.Sentence) String getProgressMessage();

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    final InstrumentationClassFinder finder = CLASS_FINDER.get(context);
    if (finder != null) {
      CLASS_FINDER.set(context, null);
      finder.releaseResources();
    }
  }

  @Override
  public final ExitCode build(CompileContext context,
                              ModuleChunk chunk,
                              DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                              OutputConsumer outputConsumer) throws IOException {
    if (outputConsumer.getCompiledClasses().isEmpty() || !isEnabled(context, chunk)) {
      return ExitCode.NOTHING_DONE;
    }

    final String progress = getProgressMessage();
    final boolean shouldShowProgress = !StringUtil.isEmptyOrSpaces(progress);
    if (shouldShowProgress) {
      context.processMessage(new ProgressMessage(progress + " [" + chunk.getPresentableShortName() + "]"));
    }

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
      return performBuild(context, chunk, finder, outputConsumer);
    }
    finally {
      if (shouldShowProgress) {
        context.processMessage(new ProgressMessage("")); // cleanup progress
      }
    }
  }

  @NotNull
  @Override
  public List<String> getCompilableFileExtensions() {
    return Collections.emptyList();
  }

  protected abstract ExitCode performBuild(CompileContext context, ModuleChunk chunk, InstrumentationClassFinder finder, OutputConsumer outputConsumer);

  /* Utility methods */

  public static InstrumentationClassFinder createInstrumentationClassFinder(@Nullable JpsSdk<?> sdk,
                                                                            Collection<? extends File> platformCp,
                                                                            Collection<? extends File> cp,
                                                                            OutputConsumer outputConsumer) throws MalformedURLException {
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
      @Override
      protected InputStream lookupClassBeforeClasspath(String internalClassName) {
        final BinaryContent content = outputConsumer.lookupClassBytes(internalClassName.replace("/", "."));
        if (content != null) {
          return new ByteArrayInputStream(content.getBuffer(), content.getOffset(), content.getLength());
        }
        return null;
      }
    };
  }
}