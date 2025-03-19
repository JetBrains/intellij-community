// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.remote;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.jsonSchema.JsonSchemaHeavyAbstractTest;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

public class JsonSchemaCatalogManagerTest extends BasePlatformTestCase {

  private JsonSchemaCatalogManager myCatalogManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myCatalogManager = configureCatalog();
  }

  @Override
  protected String getTestDataPath() {
    return JsonSchemaHeavyAbstractTest.getJsonSchemaTestDataFilePath("schemaStore/");
  }

  public void testPackageJson() {
    doTest("package.json", "https://json.schemastore.org/package.json");
    doTest("package1.json", null);
  }

  public void testCircleCI() {
    doTest(".circleci/config.yml", "https://json.schemastore.org/circleciconfig.json");
    doTest(".circleci/config.disable.yml", null);
  }

  public void testGithubWorkflow() {
    doTest(".github/workflows/nodejs.yml", "https://json.schemastore.org/github-workflow.json");
    doTest(".github/workflows/main.yaml", "https://json.schemastore.org/github-workflow.json");
    doTest(".github/workflows/a/linter.yml", null);
    doTest(".github/workflows/b/main.yaml", null);
  }

  public void testMisc() {
    doTest("jenkins-x.yml", "https://jenkins-x.io/schemas/jx-schema.json");
    doTest("jenkins-x1.yml", "https://jenkins-x.io/schemas/jx-schema.json");
    doTest("jenkins-y.yml", null);
    doTest("my.schema.json", "https://json-schema.org/draft-07/schema");
    doTest("schema.json", null);
  }

  public void testPerformance() {
    VirtualFile file = myFixture.addFileToProject("some/unknown.json", "").getVirtualFile();
    VirtualFile schemaFile = myCatalogManager.getSchemaFileForFile(file);
    Assert.assertNull(schemaFile);
    Benchmark.newBenchmark(getTestName(false), () -> {
      for (int i = 0; i < 1000000; i++) {
        VirtualFile result = myCatalogManager.getSchemaFileForFile(file);
        Assert.assertNull(result);
      }
    }).start();
  }

  private void doTest(@NotNull String filePath, @Nullable String expectedSchemaUrl) {
    VirtualFile file = myFixture.addFileToProject(filePath, "").getVirtualFile();
    VirtualFile schemaFile = myCatalogManager.getSchemaFileForFile(file);
    String schemaUrl = schemaFile != null ? schemaFile.getUrl() : null;
    Assert.assertEquals(expectedSchemaUrl, schemaUrl);
  }

  @NotNull
  private JsonSchemaCatalogManager configureCatalog() {
    JsonSchemaCatalogManager catalogManager = JsonSchemaService.Impl.get(getProject()).getCatalogManager();
    String path = JsonSchemaHeavyAbstractTest.getJsonSchemaTestDataFilePath("schemaStore/catalog.json");
    VirtualFile catalogFile = LocalFileSystem.getInstance().findFileByPath(path);
    Assert.assertNotNull(catalogFile);
    catalogManager.registerTestSchemaStoreFile(catalogFile, getTestRootDisposable());
    return catalogManager;
  }
}
