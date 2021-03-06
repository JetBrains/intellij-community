// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.artifacts.IncArtifactBuilder;
import org.jetbrains.jps.incremental.artifacts.JarPathUtil;
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactPathUtil;

import java.io.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarBasedArtifactRootDescriptor extends ArtifactRootDescriptor {
  private final String myPathInJar;
  private final Condition<? super String> myPathInJarFilter;

  public JarBasedArtifactRootDescriptor(@NotNull File jarFile,
                                        @NotNull String pathInJar,
                                        @NotNull SourceFileFilter filter,
                                        int index,
                                        @NotNull ArtifactBuildTarget target,
                                        @NotNull DestinationInfo destinationInfo,
                                        @NotNull Condition<? super String> pathInJarFilter) {
    super(jarFile, filter, index, target, destinationInfo);
    myPathInJar = pathInJar;
    myPathInJarFilter = pathInJarFilter;
  }

  public void processEntries(EntryProcessor processor) throws IOException {
    if (!myRoot.isFile()) return;

    String prefix = StringUtil.trimStart(myPathInJar, "/");
    if (!StringUtil.endsWithChar(prefix, '/')) prefix += "/";
    if (prefix.equals("/")) {
      prefix = "";
    }

    try (ZipFile zipFile = new ZipFile(myRoot)) {
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();

      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        final String name = entry.getName();
        if (name.startsWith(prefix)) {
          String relativePath = name.substring(prefix.length());
          if (myPathInJarFilter.value(relativePath)) {
            processor.process(entry.isDirectory() ? null : zipFile.getInputStream(entry), relativePath, entry);
          }
        }
      }
    }
    catch (IOException e) {
      throw new IOException("Error occurred during processing zip file " + myRoot + ": " + e.getMessage(), e);
    }
  }

  @Override
  protected String getFullPath() {
    return myRoot.getPath() + JarPathUtil.JAR_SEPARATOR + myPathInJar;
  }

  @Override
  public void copyFromRoot(final String filePath,
                           final int rootIndex, final String outputPath,
                           CompileContext context, final BuildOutputConsumer outputConsumer,
                           final ArtifactOutputToSourceMapping outSrcMapping) throws IOException {
    if (!myRoot.isFile()) return;
    ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
    if (logger.isEnabled()) {
      logger.logCompiledPaths(Collections.singletonList(filePath), IncArtifactBuilder.BUILDER_ID, "Extracting archive:");
    }
    processEntries(new EntryProcessor() {
      @Override
      public void process(@Nullable InputStream inputStream, @NotNull String relativePath, ZipEntry entry) throws IOException {
        final String fullOutputPath = JpsArtifactPathUtil.appendToPath(outputPath, relativePath);
        final File outputFile = new File(fullOutputPath);

        FileUtil.createParentDirs(outputFile);
        if (inputStream == null) {
          outputFile.mkdir();
        }
        else {
          if (outSrcMapping.getState(fullOutputPath) == null) {
            try (BufferedInputStream from = new BufferedInputStream(inputStream);
                 BufferedOutputStream to = new BufferedOutputStream(new FileOutputStream(outputFile))) {
              FileUtil.copy(from, to);
            }
            outputConsumer.registerOutputFile(outputFile, Collections.singletonList(filePath));
          }
          outSrcMapping.appendData(fullOutputPath, rootIndex, filePath);
        }
      }
    });
  }

  public interface EntryProcessor {
    void process(@Nullable InputStream inputStream, @NotNull String relativePath, ZipEntry entry) throws IOException;
  }
}
