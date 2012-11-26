package org.jetbrains.jps.model.serialization.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsMacroExpander;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsModuleClasspathSerializer {
  private final String myClasspathId;

  protected JpsModuleClasspathSerializer(String classpathId) {
    myClasspathId = classpathId;
  }

  public final String getClasspathId() {
    return myClasspathId;
  }

  public abstract void loadClasspath(@NotNull JpsModule module,
                                     @Nullable String classpathDir,
                                     @NotNull String baseModulePath,
                                     JpsMacroExpander expander,
                                     List<String> paths,
                                     JpsSdkType<?> projectSdkType);
}
