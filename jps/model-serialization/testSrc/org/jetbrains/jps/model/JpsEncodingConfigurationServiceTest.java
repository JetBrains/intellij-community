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
package org.jetbrains.jps.model;

import org.jetbrains.jps.model.serialization.JpsProjectData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JpsEncodingConfigurationServiceTest {
  @Test
  public void test() {
    JpsProjectData projectData = JpsProjectData.loadFromTestData("jps/model-serialization/testData/fileEncoding/fileEncoding.ipr", getClass());
    JpsEncodingProjectConfiguration configuration = JpsEncodingConfigurationService.getInstance().getEncodingConfiguration(projectData.getProject());
    assertNotNull(configuration);
    assertEncoding("windows-1251", "dir/a.txt", configuration, projectData);
    assertEncoding("UTF-8", "dir/with-encoding.xml", configuration, projectData);
    assertEncoding("windows-1251", "dir/without-encoding.xml", configuration, projectData);
    assertEncoding("windows-1251", "dir/non-existent.xml", configuration, projectData);
  }

  protected void assertEncoding(final String encoding, final String path, JpsEncodingProjectConfiguration configuration,
                                JpsProjectData projectData) {
    assertEquals(encoding, configuration.getEncoding(projectData.resolvePath(path).toFile()));
  }
}
