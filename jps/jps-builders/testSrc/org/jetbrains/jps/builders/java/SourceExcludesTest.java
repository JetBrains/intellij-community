// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import static com.intellij.util.io.TestFileSystemItem.fs;

public class SourceExcludesTest extends JpsBuildTestCase {

  public void testExcludedFileInSourceRoot() {
    String file = createFile("src/A.java", "public class A {}");
    String excludedFile = createFile("src/excluded.java", "public class excluded {}");
    JpsModule m = addModule("m");
    m.addSourceRoot(JpsPathUtil.pathToUrl(PathUtil.getParentPath(file)), JavaSourceRootType.SOURCE);
    JpsJavaExtensionService.getInstance().getCompilerConfiguration(myProject).getCompilerExcludes().addExcludedFile(
      "file://" + FileUtil.toSystemIndependentName(excludedFile)
    );
    rebuildAllModules();
    assertOutput(m, fs().file("A.class"));
  }
  
  public void testExcludedDirInSourceRoot() {
    String file = createFile("src/A.java", "public class A {}");
    String excludedFile = createFile("src/data/excluded.java", "public class excluded {}");
    JpsModule m = addModule("m");
    m.addSourceRoot(JpsPathUtil.pathToUrl(PathUtil.getParentPath(file)), JavaSourceRootType.SOURCE);
    JpsJavaExtensionService.getInstance().getCompilerConfiguration(myProject).getCompilerExcludes().addExcludedDirectory(
      JpsPathUtil.pathToUrl(PathUtil.getParentPath(excludedFile)), false
    );
    rebuildAllModules();
    assertOutput(m, fs().file("A.class"));
  }

  public void testRecursivelyExcludedDirInSourceRoot() {
    String file = createFile("src/A.java", "public class A {}");
    String excludedFile = createFile("src/data/excluded.java", "public class excluded {}");
    createFile("src/data/pkg/excluded.java", "package pkg; public class excluded {}");
    JpsModule m = addModule("m");
    m.addSourceRoot(JpsPathUtil.pathToUrl(PathUtil.getParentPath(file)), JavaSourceRootType.SOURCE);
    JpsJavaExtensionService.getInstance().getCompilerConfiguration(myProject).getCompilerExcludes().addExcludedDirectory(
      JpsPathUtil.pathToUrl(PathUtil.getParentPath(excludedFile)), true
    );
    rebuildAllModules();
    assertOutput(m, fs().file("A.class"));
  }

  public void testJavadocSnippetsExcludedInSourceRoot() {
    JpsJavaExtensionService.getInstance().getCompilerConfiguration(myProject).addResourcePattern("*.txt");

    String file = createFile(JavadocSnippetsSkipFilter.SNIPPETS_FOLDER + "/src/A.java", "public class A {}");
    createFile(JavadocSnippetsSkipFilter.SNIPPETS_FOLDER + "/src/res.txt", "COPIED RESOURCE");
    createFile(JavadocSnippetsSkipFilter.SNIPPETS_FOLDER + "/src/" + JavadocSnippetsSkipFilter.SNIPPETS_FOLDER + "/B.java", "public class B {}");
    createFile(JavadocSnippetsSkipFilter.SNIPPETS_FOLDER + "/src/" + JavadocSnippetsSkipFilter.SNIPPETS_FOLDER + "/res.txt", "FILTERED RESOURCE");
    JpsModule m = addModule("m");
    m.addSourceRoot(JpsPathUtil.pathToUrl(PathUtil.getParentPath(file)), JavaSourceRootType.SOURCE);
    rebuildAllModules();
    assertOutput(m, fs().file("A.class").file("res.txt", "COPIED RESOURCE"));
  }
}