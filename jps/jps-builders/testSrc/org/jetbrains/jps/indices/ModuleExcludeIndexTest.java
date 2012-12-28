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
package org.jetbrains.jps.indices;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.JpsJavaModelTestCase;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author nik
 */
public class ModuleExcludeIndexTest extends JpsJavaModelTestCase {
  private File myRoot;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRoot = FileUtil.createTempDirectory("excludes", null);
  }

  public void testExcludeProjectOutput() throws IOException {
    File out = new File(myRoot, "out");
    getJavaService().getOrCreateProjectExtension(myProject).setOutputUrl(JpsPathUtil.pathToUrl(out.getAbsolutePath()));
    JpsModule module1 = addModule();
    getJavaService().getOrCreateModuleExtension(module1).setInheritOutput(true);
    JpsModule module2 = addModule();
    module2.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(out.getAbsolutePath()));
    getJavaService().getOrCreateModuleExtension(module2).setInheritOutput(true);

    assertNotExcluded(myRoot);
    assertExcluded(out);
    assertEmpty(getModuleExcludes(module1));
    assertSameElements(getModuleExcludes(module2), out);
  }

  public void testExcludeModuleOutput() {
    File out = new File(myRoot, "out");
    JpsModule module = addModule();
    JpsJavaModuleExtension extension = getJavaService().getOrCreateModuleExtension(module);
    extension.setExcludeOutput(true);
    extension.setOutputUrl(JpsPathUtil.pathToUrl(out.getAbsolutePath()));

    assertNotExcluded(myRoot);
    assertExcluded(out);
    assertSameElements(getModuleExcludes(module), out);

    extension.setExcludeOutput(false);
    assertNotExcluded(out);
    assertEmpty(getModuleExcludes(module));
  }

  public void testExcludeExcludedFolder() {
    File exc = new File(myRoot, "exc");
    JpsModule module = addModule();
    module.getExcludeRootsList().addUrl(JpsPathUtil.pathToUrl(exc.getAbsolutePath()));

    assertNotExcluded(myRoot);
    assertExcluded(exc);
    assertSameElements(getModuleExcludes(module), exc);
  }

  private Collection<File> getModuleExcludes(JpsModule module) {
    return new ModuleExcludeIndexImpl(myModel).getModuleExcludes(module);
  }

  private void assertExcluded(File file) {
    assertTrue(new ModuleExcludeIndexImpl(myModel).isExcluded(file));
  }

  private void assertNotExcluded(File file) {
    assertFalse(new ModuleExcludeIndexImpl(myModel).isExcluded(file));
  }
}
