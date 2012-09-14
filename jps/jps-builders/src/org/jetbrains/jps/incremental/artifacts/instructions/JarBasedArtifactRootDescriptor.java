package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.artifacts.JarPathUtil;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;

import java.io.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author nik
 */
public class JarBasedArtifactRootDescriptor extends ArtifactRootDescriptor {
  private final String myPathInJar;

  public JarBasedArtifactRootDescriptor(@NotNull File jarFile,
                                        @NotNull String pathInJar,
                                        @NotNull SourceFileFilter filter,
                                        int index,
                                        ArtifactBuildTarget target) {
    super(jarFile, filter, index, target);
    myPathInJar = pathInJar;
  }

  @Override
  public String toString() {
    return myRoot.getPath() + JarPathUtil.JAR_SEPARATOR + myPathInJar;
  }

  public void processEntries(EntryProcessor processor) throws IOException {
    String prefix = StringUtil.trimStart(myPathInJar, "/");
    if (!StringUtil.endsWithChar(prefix, '/')) prefix += "/";
    if (prefix.equals("/")) {
      prefix = "";
    }

    ZipFile zipFile = new ZipFile(myRoot);
    try {
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();

      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        final String name = entry.getName();
        if (name.startsWith(prefix)) {
          String relativePath = name.substring(prefix.length());
          processor.process(entry.isDirectory() ? null : zipFile.getInputStream(entry), relativePath);
        }
      }
    }
    finally {
      zipFile.close();
    }
  }

  public void copyFromRoot(final String filePath,
                           final int rootIndex, final String outputPath,
                           CompileContext context, final SourceToOutputMapping srcOutMapping,
                           final ArtifactOutputToSourceMapping outSrcMapping) throws IOException {
    context.getLoggingManager().getArtifactBuilderLogger().fileCopied(filePath);
    processEntries(new EntryProcessor() {
      @Override
      public void process(@Nullable InputStream inputStream, @NotNull String relativePath) throws IOException {
        final String fullOutputPath = FileUtil.toSystemDependentName(JpsPathUtil.appendToPath(outputPath, relativePath));
        final File outputFile = new File(fullOutputPath);

        FileUtil.createParentDirs(outputFile);
        if (inputStream == null) {
          outputFile.mkdir();
        }
        else {
          String fullSourcePath = filePath + JarPathUtil.JAR_SEPARATOR + relativePath;
          if (outSrcMapping.getState(fullOutputPath) == null) {
            final BufferedInputStream from = new BufferedInputStream(inputStream);
            final BufferedOutputStream to = new BufferedOutputStream(new FileOutputStream(outputFile));
            try {
              FileUtil.copy(from, to);
            }
            finally {
              from.close();
              to.close();
            }
            srcOutMapping.appendData(filePath, Collections.singletonList(fullOutputPath));
          }
          outSrcMapping.appendData(fullOutputPath, Collections.singletonList(new ArtifactOutputToSourceMapping.SourcePathAndRootIndex(fullSourcePath, rootIndex)));
        }
      }
    });
  }

  public interface EntryProcessor {
    void process(@Nullable InputStream inputStream, @NotNull String relativePath) throws IOException;
  }
}
