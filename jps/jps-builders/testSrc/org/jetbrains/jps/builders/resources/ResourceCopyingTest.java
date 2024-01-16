// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.resources;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.nio.file.Files;

import static com.intellij.util.io.TestFileSystemItem.fs;

public class ResourceCopyingTest extends JpsBuildTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JpsJavaExtensionService.getInstance().getCompilerConfiguration(myProject).addResourcePattern("*.xml");
  }

  public void testSimple() {
    String file = createFile("src/a.xml");
    JpsModule m = addModule("m", PathUtil.getParentPath(file));
    rebuildAllModules();
    assertOutput(m, fs().file("a.xml"));
  }

  public void testReadonly() {
    String source = createFile("src/a.xml");
    final File sourceFile = new File(source);

    assertTrue("Unable to make file readonly: ", sourceFile.setWritable(false));

    JpsModule m = addModule("m", PathUtil.getParentPath(source));
    rebuildAllModules();
    assertOutput(m, fs().file("a.xml"));

    final File outputFile = new File(getModuleOutput(m), "a.xml");
    assertTrue(outputFile.exists());
    assertFalse(Files.isWritable(outputFile.toPath()));

    sourceFile.setWritable(true); // need this to perform the change
    change(source, "changed content");
    assertTrue("Unable to make file readonly: ", sourceFile.setWritable(false));

    buildAllModules().assertSuccessful();

    assertTrue(outputFile.exists());
    assertFalse(Files.isWritable(outputFile.toPath()));
  }

  public void testCaseChange() {
    String file = createFile("src/a.xml");
    JpsModule m = addModule("m", PathUtil.getParentPath(file));
    rebuildAllModules();
    assertOutput(m, fs().file("a.xml"));
    rename(file, "A.xml");
    buildAllModules();
    assertOutput(m, fs().file("A.xml"));
  }

  public void testPackagePrefix() {
    String file = createFile("src/a.xml");
    JpsModule m = addModule("m", PathUtil.getParentPath(file));
    JpsModuleSourceRoot sourceRoot = assertOneElement(m.getSourceRoots());
    JpsTypedModuleSourceRoot<JavaSourceRootProperties> typed = sourceRoot.asTyped(JavaSourceRootType.SOURCE);
    assertNotNull(typed);
    typed.getProperties().setPackagePrefix("xxx");
    rebuildAllModules();
    assertOutput(m, fs().dir("xxx").file("a.xml"));
  }

  public void testResourceRoot() {
    String file = createFile("res/A.java", "xxx");
    JpsModule m = addModule("m");
    m.addSourceRoot(JpsPathUtil.pathToUrl(PathUtil.getParentPath(file)), JavaResourceRootType.RESOURCE);
    rebuildAllModules();
    assertOutput(m, fs().file("A.java", "xxx"));
  }

  public void testFileAdSourceRoot() {
    String file = createFile("src/data.xml", "xxx");
    JpsModule m = addModule("m");
    m.addSourceRoot(JpsPathUtil.pathToUrl(file), JavaSourceRootType.SOURCE);
    rebuildAllModules();
    assertOutput(m, fs().file("data.xml", "xxx"));
  }

  public void testResourceRootWithJavadocSnippet() {
    String file = createFile("res/A.java", "xxx");
    String snippetFile = createFile("res/snippet-files/A.java", "XXX");
    JpsModule m = addModule("m");
    m.addSourceRoot(JpsPathUtil.pathToUrl(PathUtil.getParentPath(file)), JavaResourceRootType.RESOURCE);
    rebuildAllModules();
    assertOutput(m, fs().file("A.java", "xxx").dir("snippet-files").file("A.java", "XXX"));
  }

  public void testExcludesInResourceRoot() {
    String file = createFile("res/A.java", "xxx");
    String excludedFile = createFile("res/excluded.java", "XXX");
    JpsModule m = addModule("m");
    m.addSourceRoot(JpsPathUtil.pathToUrl(PathUtil.getParentPath(file)), JavaResourceRootType.RESOURCE);
    JpsJavaExtensionService.getInstance().getCompilerConfiguration(myProject).getCompilerExcludes().addExcludedFile(
      "file://" + FileUtil.toSystemIndependentName(excludedFile)
    );
    rebuildAllModules();
    assertOutput(m, fs().file("A.java", "xxx"));
  }
}
