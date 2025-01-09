// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl;
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl;
import org.jetbrains.jps.builders.impl.BuildTargetRegistryImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompilerEncodingConfiguration;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.indices.impl.IgnoredFileIndexImpl;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsProjectData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eugene Zhuravlev
 */
public class CompilerEncodingConfigurationTest {
  private File myDataStorageRoot;

  @BeforeEach
  public void setUp() throws Exception {
    myDataStorageRoot = FileUtil.createTempDirectory("compile-server-compiler-encoding", null);
  }

  @AfterEach
  protected void tearDown() throws Exception {
    FileUtil.delete(myDataStorageRoot);
  }

  @Test
  public void test() {
    JpsProjectData projectData = JpsProjectData.loadFromTestData("jps/jps-builders/testData/compilerEncoding/compilerEncoding.ipr", getClass());
    JpsModel model = projectData.getProject().getModel();
    BuildTargetRegistryImpl targetRegistry = new BuildTargetRegistryImpl(model);
    ModuleExcludeIndex index = new ModuleExcludeIndexImpl(model);
    IgnoredFileIndexImpl ignoredFileIndex = new IgnoredFileIndexImpl(model);
    BuildDataPaths dataPaths = new BuildDataPathsImpl(myDataStorageRoot.toPath());
    BuildRootIndexImpl rootIndex = new BuildRootIndexImpl(targetRegistry, model, index, dataPaths, ignoredFileIndex);

    JpsEncodingProjectConfiguration projectConfig = JpsEncodingConfigurationService.getInstance().getEncodingConfiguration(projectData.getProject());
    assertNotNull(projectConfig);
    assertEncoding("UTF-8", "moduleA/src/dir/with-encoding.xml", projectConfig, projectData); // overridden by file's internal encoding
    assertEncoding("windows-1251", "moduleA/src/dir/with-encoding.txt", projectConfig, projectData);
    assertEncoding("UTF-8", "moduleA/src/dir/without-encoding.xml", projectConfig, projectData);
    assertEncoding("UTF-8", "moduleA/src/dir/without-encoding.txt", projectConfig, projectData);
    assertEncoding("UTF-8", "moduleA/src/dir/non-existent.xml", projectConfig, projectData);
    assertEncoding("windows-1252", "moduleB/src/non-existent.xml", projectConfig, projectData);
    final CompilerEncodingConfiguration compilerEncodingConfiguration = new CompilerEncodingConfiguration(model, rootIndex);
    for (JpsModule module : projectData.getProject().getModules()) {
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

  protected void assertEncoding(final String encoding, final String path, JpsEncodingProjectConfiguration configuration,
                                JpsProjectData projectData) {
    assertEquals(encoding, configuration.getEncoding(projectData.resolvePath(path).toFile()));
  }
}
