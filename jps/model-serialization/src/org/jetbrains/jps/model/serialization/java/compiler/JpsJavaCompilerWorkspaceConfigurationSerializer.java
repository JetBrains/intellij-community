package org.jetbrains.jps.model.serialization.java.compiler;

import com.intellij.openapi.util.JDOMExternalizerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

/**
 * @author nik
 */
public class JpsJavaCompilerWorkspaceConfigurationSerializer extends JpsProjectExtensionSerializer {
  public JpsJavaCompilerWorkspaceConfigurationSerializer() {
    super(WORKSPACE_FILE, "CompilerWorkspaceConfiguration");
  }

  @Override
  public void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    String assertNotNull = JDOMExternalizerUtil.readField(componentTag, "ASSERT_NOT_NULL");
    if (assertNotNull != null) {
      configuration.setAddNotNullAssertions(Boolean.parseBoolean(assertNotNull));
    }
    String clearOutputDirectory = JDOMExternalizerUtil.readField(componentTag, "CLEAR_OUTPUT_DIRECTORY");
    configuration.setClearOutputDirectoryOnRebuild(clearOutputDirectory == null || Boolean.parseBoolean(clearOutputDirectory));
  }

  @Override
  public void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag) {
  }
}
