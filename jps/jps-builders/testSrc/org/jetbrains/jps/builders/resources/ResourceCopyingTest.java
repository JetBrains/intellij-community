package org.jetbrains.jps.builders.resources;

import com.intellij.util.PathUtil;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;

import static com.intellij.util.io.TestFileSystemItem.fs;

/**
 * @author nik
 */
public class ResourceCopyingTest extends JpsBuildTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(myProject).addResourcePattern("*.xml");
  }

  public void testSimple() {
    String file = createFile("src/a.xml");
    JpsModule m = addModule("m", PathUtil.getParentPath(file));
    rebuildAll();
    assertOutput(m, fs().file("a.xml"));
  }
  public void testPackagePrefix() {
    String file = createFile("src/a.xml");
    JpsModule m = addModule("m", PathUtil.getParentPath(file));
    JpsModuleSourceRoot sourceRoot = assertOneElement(m.getSourceRoots());
    JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> typed = sourceRoot.asTyped(JavaSourceRootType.SOURCE);
    assertNotNull(typed);
    typed.getProperties().setData(new JavaSourceRootProperties("xxx"));
    rebuildAll();
    assertOutput(m, fs().dir("xxx").file("a.xml"));
  }
}
