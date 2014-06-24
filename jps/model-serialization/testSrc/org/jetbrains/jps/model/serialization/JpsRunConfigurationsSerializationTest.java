/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.serialization;

import com.intellij.util.containers.ContainerUtil;
import junit.framework.AssertionFailedError;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationProperties;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationType;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfiguration;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfigurationType;
import org.jetbrains.jps.model.runConfiguration.JpsTypedRunConfiguration;
import org.jetbrains.jps.model.serialization.runConfigurations.JpsUnknownRunConfigurationType;

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

    List<JpsRunConfiguration> all = myProject.getRunConfigurations();
    JpsRunConfiguration junit = findByName(all, "test");
    JpsRunConfigurationType type = ((JpsTypedRunConfiguration)junit).getType();
    assertEquals("JUnit", assertInstanceOf(type, JpsUnknownRunConfigurationType.class).getTypeId());
  }

  private static JpsRunConfiguration findByName(List<JpsRunConfiguration> configurations, String name) {
    for (JpsRunConfiguration configuration : configurations) {
      if (configuration.getName().equals(name)) {
        return configuration;
      }
    }
    throw new AssertionFailedError("'" + name + "' run configuration not found");
  }
}
