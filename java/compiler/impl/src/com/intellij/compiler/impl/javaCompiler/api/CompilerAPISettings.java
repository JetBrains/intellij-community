package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;

@State(
  name = "CompilerAPISettings",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class CompilerAPISettings extends JavacSettings {
  public CompilerAPISettings(Project project) {
    super(project);
  }
  public static CompilerAPISettings getInstance(Project project) {
    return ServiceManager.getService(project, CompilerAPISettings.class);
  }
}
