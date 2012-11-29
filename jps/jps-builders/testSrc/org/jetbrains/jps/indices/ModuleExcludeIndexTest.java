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
    assertNotExcluded(myRoot);
    assertExcluded(out);
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
