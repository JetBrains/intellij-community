package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jdom.Element;

@State(
  name = "EclipseEmbeddedCompilerSettings",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class EclipseEmbeddedCompilerSettings extends EclipseCompilerSettings implements PersistentStateComponent<Element> {
  public static EclipseEmbeddedCompilerSettings getInstance(Project project) {
    return ServiceManager.getService(project, EclipseEmbeddedCompilerSettings.class);
  }
}
