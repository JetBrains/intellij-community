package org.jetbrains.jps.model.serialization;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationProperties;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationType;
import org.jetbrains.jps.model.runConfiguration.JpsTypedRunConfiguration;

import java.util.List;

/**
 * @author nik
 */
public class JpsRunConfigurationsSerializationTest extends JpsSerializationTestCase {
  public void testLoadIpr() {
    doTest("jps/model-serialization/testData/run-configurations/run-configurations.ipr");
  }

  public void testLoadDirectoryBased() {
    doTest("jps/model-serialization/testData/run-configurations-dir");
  }

  private void doTest(final String relativePath) {
    loadProject(relativePath);
    List<JpsTypedRunConfiguration<JpsApplicationRunConfigurationProperties>>
      configurations = ContainerUtil.newArrayList(myProject.getRunConfigurations(JpsApplicationRunConfigurationType.INSTANCE));
    assertEquals(2, configurations.size());

    JpsTypedRunConfiguration<JpsApplicationRunConfigurationProperties> shared = configurations.get(0);
    assertEquals("shared", shared.getName());
    assertEquals("xxx.Main2", shared.getProperties().getMainClass());

    JpsTypedRunConfiguration<JpsApplicationRunConfigurationProperties> main = configurations.get(1);
    assertEquals("Main", main.getName());
    assertEquals("xxx.Main", main.getProperties().getMainClass());
  }
}
