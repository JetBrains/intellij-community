package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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
}
