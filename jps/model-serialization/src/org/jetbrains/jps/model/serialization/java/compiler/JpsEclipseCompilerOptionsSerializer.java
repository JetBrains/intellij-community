package org.jetbrains.jps.model.serialization.java.compiler;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.EclipseCompilerOptions;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

/**
 * @author nik
 */
public class JpsEclipseCompilerOptionsSerializer extends JpsProjectExtensionSerializer {
  private final String myCompilerId;

  public JpsEclipseCompilerOptionsSerializer(String componentName, String compilerId) {
    super("compiler.xml", componentName);
    myCompilerId = compilerId;
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    EclipseCompilerOptions options = XmlSerializer.deserialize(componentTag, EclipseCompilerOptions.class);
    if (options == null) {
      options = new EclipseCompilerOptions();
    }
    configuration.setCompilerOptions(myCompilerId, options);
  }

  @Override
  public void loadExtensionWithDefaultSettings(@NotNull JpsProject project) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    configuration.setCompilerOptions(myCompilerId, new EclipseCompilerOptions());
  }

  @Override
  public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
  }
}
