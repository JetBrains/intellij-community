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

import org.jetbrains.jps.model.serialization.JpsSerializationTestCase;

import java.io.File;

public class JpsEncodingConfigurationServiceTest extends JpsSerializationTestCase {
  public void test() {
    loadProject("/jps/model-serialization/testData/fileEncoding/fileEncoding.ipr");
    JpsEncodingProjectConfiguration configuration = JpsEncodingConfigurationService.getInstance().getEncodingConfiguration(myProject);
    assertNotNull(configuration);
    assertEncoding("windows-1251", "dir/a.txt", configuration);
    assertEncoding("UTF-8", "dir/with-encoding.xml", configuration);
    assertEncoding("windows-1251", "dir/without-encoding.xml", configuration);
    assertEncoding("windows-1251", "dir/non-existent.xml", configuration);
  }

  protected void assertEncoding(final String encoding, final String path, JpsEncodingProjectConfiguration configuration) {
    assertEquals(encoding, configuration.getEncoding(new File(getAbsolutePath(path))));
  }
}
