package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.PathUtil;
import org.jetbrains.jps.incremental.artifacts.JarPathUtil;

import java.io.*;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author nik
 */
public class JarBasedArtifactSourceRoot extends ArtifactSourceRoot {
  private final File myJarFile;
  private final String myPathInJar;

  public JarBasedArtifactSourceRoot(@NotNull File jarFile, @NotNull String pathInJar, @NotNull SourceFileFilter filter) {
    super(filter);
    myJarFile = jarFile;
    myPathInJar = pathInJar;
  }

  @NotNull
  @Override
  public File getRootFile() {
    return myJarFile;
  }

  @Override
  public boolean containsFile(String filePath) {
    return new File(FileUtil.toSystemDependentName(filePath)).equals(myJarFile);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    JarBasedArtifactSourceRoot root = (JarBasedArtifactSourceRoot)o;
    return myJarFile.equals(root.myJarFile) && myPathInJar.equals(root.myPathInJar);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * super.hashCode() + myJarFile.hashCode()) + myPathInJar.hashCode();
  }

  @Override
  public String toString() {
    return myJarFile.getPath() + JarPathUtil.JAR_SEPARATOR + myPathInJar;
  }

  public void processEntries(EntryProcessor processor) throws IOException {
    String prefix = StringUtil.trimStart(myPathInJar, "/");
    if (!StringUtil.endsWithChar(prefix, '/')) prefix += "/";
    if (prefix.equals("/")) {
      prefix = "";
    }

    ZipFile zipFile = new ZipFile(myJarFile);
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

  public void copyFromRoot(String filePath, final String outputPath, final List<String> outputs) throws IOException {
    processEntries(new EntryProcessor() {
      @Override
      public void process(@Nullable InputStream inputStream, @NotNull String relativePath) throws IOException {
        final String fullOutputPath = FileUtil.toSystemDependentName(PathUtil.appendToPath(outputPath, relativePath));
        final File outputFile = new File(fullOutputPath);

        FileUtil.createParentDirs(outputFile);
        if (inputStream == null) {
          outputFile.mkdir();
        }
        else {
          final BufferedInputStream from = new BufferedInputStream(inputStream);
          final BufferedOutputStream to = new BufferedOutputStream(new FileOutputStream(outputFile));
          try {
            FileUtil.copy(from, to);
          }
          finally {
            from.close();
            to.close();
          }
        }
        outputs.add(fullOutputPath);
      }
    });
  }

  public interface EntryProcessor {
    void process(@Nullable InputStream inputStream, @NotNull String relativePath) throws IOException;
  }
}
