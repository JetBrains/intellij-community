package org.jetbrains.jps.model.serialization.java.compiler;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.RmicCompilerOptions;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

/**
 * @author nik
 */
public class RmicCompilerOptionsSerializer extends JpsProjectExtensionSerializer {
  private final String myCompilerId;

  public RmicCompilerOptionsSerializer(String componentName, String compilerId) {
    super("compiler.xml", componentName);
    myCompilerId = compilerId;
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    RmicCompilerOptions options = XmlSerializer.deserialize(componentTag, RmicCompilerOptions.class);
    configuration.setCompilerOptions(myCompilerId, options == null? new RmicCompilerOptions() : options);
  }

  @Override
  public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
  }
}
