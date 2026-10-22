// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.remote;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.jetbrains.jsonSchema.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.JsonSchemaHeavyTestFixture;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class JsonSchemaCatalogManagerTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<String> testNameFixture = testNameFixture(false);

  private CodeInsightTestFixture myFixture;
  private JsonSchemaCatalogManager myCatalogManager;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(JsonSchemaHeavyTestFixture.getJsonSchemaTestDataFilePath("schemaStore/"));
    myCatalogManager = configureCatalog();
  }

  @Test
  public void testPackageJson() {
    doTest("package.json", "https://json.schemastore.org/package.json");
    doTest("package1.json", null);
  }

  @Test
  public void testCircleCI() {
    doTest(".circleci/config.yml", "https://json.schemastore.org/circleciconfig.json");
    doTest(".circleci/config.disable.yml", null);
  }

  @Test
  public void testGithubWorkflow() {
    doTest(".github/workflows/nodejs.yml", "https://json.schemastore.org/github-workflow.json");
    doTest(".github/workflows/main.yaml", "https://json.schemastore.org/github-workflow.json");
    doTest(".github/workflows/a/linter.yml", null);
    doTest(".github/workflows/b/main.yaml", null);
  }

  @Test
  public void testMisc() {
    doTest("jenkins-x.yml", "https://jenkins-x.io/schemas/jx-schema.json");
    doTest("jenkins-x1.yml", "https://jenkins-x.io/schemas/jx-schema.json");
    doTest("jenkins-y.yml", null);
    doTest("my.schema.json", "https://json-schema.org/draft-07/schema");
    doTest("schema.json", null);
  }

  @Test
  @PerformanceUnitTest
  public void testPerformance() {
    VirtualFile file = myFixture.addFileToProject("some/unknown.json", "").getVirtualFile();
    JsonSchemaCatalogEntry schemaEntry = ReadAction.computeBlocking(() -> myCatalogManager.getSchemaCatalogEntryForFile(file));
    assertNull(schemaEntry);
    Benchmark.newBenchmark(testNameFixture.get(), () -> ReadAction.runBlocking(() -> {
      for (int i = 0; i < 1000000; i++) {
        JsonSchemaCatalogEntry result = myCatalogManager.getSchemaCatalogEntryForFile(file);
        assertNull(result);
      }
    })).runAsStressTest().start();
  }

  private void doTest(@NotNull String filePath, @Nullable String expectedSchemaUrl) {
    VirtualFile file = myFixture.addFileToProject(filePath, "").getVirtualFile();
    JsonSchemaCatalogEntry entry = ReadAction.computeBlocking(() -> myCatalogManager.getSchemaCatalogEntryForFile(file));
    String schemaUrl = entry != null ? entry.getUrl() : null;
    assertEquals(expectedSchemaUrl, schemaUrl);
  }

  @NotNull
  private JsonSchemaCatalogManager configureCatalog() {
    JsonSchemaCatalogManager catalogManager = JsonSchemaService.Impl.get(myFixture.getProject()).getCatalogManager();
    String path = JsonSchemaHeavyTestFixture.getJsonSchemaTestDataFilePath("schemaStore/catalog.json");
    VirtualFile catalogFile = StandardFileSystems.local().findFileByPath(path);
    assertNotNull(catalogFile);
    catalogManager.registerTestSchemaStoreFile(catalogFile, myFixture.getTestRootDisposable());
    return catalogManager;
  }
}
