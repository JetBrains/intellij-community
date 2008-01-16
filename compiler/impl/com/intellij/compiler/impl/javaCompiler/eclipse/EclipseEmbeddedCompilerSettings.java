package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

@State(
  name = "EclipseEmbeddedCompilerSettings",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class EclipseEmbeddedCompilerSettings extends EclipseCompilerSettings implements PersistentStateComponent<Element>, ProjectComponent {
  public static EclipseEmbeddedCompilerSettings getInstance(Project project) {
    return project.getComponent(EclipseEmbeddedCompilerSettings.class);
  }

  @NotNull
  public String getComponentName() {
    return "EclipseEmbeddedCompilerSettings";
  }
}
