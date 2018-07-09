/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl;
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl;
import org.jetbrains.jps.builders.impl.BuildTargetRegistryImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompilerEncodingConfiguration;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.indices.impl.IgnoredFileIndexImpl;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 */
public class CompilerEncodingConfigurationTest extends JpsEncodingConfigurationServiceTest {
  private File myDataStorageRoot;
  private BuildRootIndexImpl myRootIndex;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    BuildTargetRegistryImpl targetRegistry = new BuildTargetRegistryImpl(myModel);
    ModuleExcludeIndex index = new ModuleExcludeIndexImpl(myModel);
    IgnoredFileIndexImpl ignoredFileIndex = new IgnoredFileIndexImpl(myModel);
    myDataStorageRoot = FileUtil.createTempDirectory("compile-server-" + StringUtil.decapitalize(StringUtil.trimStart(getName(), "test")), null);
    BuildDataPaths dataPaths = new BuildDataPathsImpl(myDataStorageRoot);
    myRootIndex = new BuildRootIndexImpl(targetRegistry, myModel, index, dataPaths, ignoredFileIndex);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    FileUtil.delete(myDataStorageRoot);
  }

  public void test() {
    loadProject("/jps/jps-builders/testData/compilerEncoding/compilerEncoding.ipr");
    JpsEncodingProjectConfiguration projectConfig = JpsEncodingConfigurationService.getInstance().getEncodingConfiguration(myProject);
    assertNotNull(projectConfig);
    assertEncoding("UTF-8", "moduleA/src/dir/with-encoding.xml", projectConfig); // overridden by file's internal encoding
    assertEncoding("windows-1251", "moduleA/src/dir/with-encoding.txt", projectConfig);
    assertEncoding("UTF-8", "moduleA/src/dir/without-encoding.xml", projectConfig);
    assertEncoding("UTF-8", "moduleA/src/dir/without-encoding.txt", projectConfig);
    assertEncoding("UTF-8", "moduleA/src/dir/non-existent.xml", projectConfig);
    assertEncoding("windows-1252", "moduleB/src/non-existent.xml", projectConfig);
    final CompilerEncodingConfiguration compilerEncodingConfiguration = new CompilerEncodingConfiguration(myModel, myRootIndex);
    for (JpsModule module : myProject.getModules()) {
      final String moduleEncoding = compilerEncodingConfiguration.getPreferredModuleEncoding(module);
      if ("moduleA".equals(module.getName())) {
        assertEquals("UTF-8", moduleEncoding);
      }
      else if ("moduleB".equals(module.getName())) {
        assertNull(moduleEncoding);
      }
      else if ("moduleC".equals(module.getName())) {
        assertEquals("windows-1252", moduleEncoding);
      }
      else {
        fail("Unexpected module in project: " + module.getName());
      }
    }
  }

}
