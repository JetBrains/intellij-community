// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixtureKt.jsonSchemaHighlightingFixture;

@TestApplication
public class JsonSchemaReSharperHighlightingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHighlightingTestFixture> schemaFixture =
    jsonSchemaHighlightingFixture(codeInsightFixture, "config.json", () -> new JsonSchemaComplianceInspection(), JsonLanguage.INSTANCE);

  protected CodeInsightTestFixture myFixture;
  protected JsonSchemaHighlightingTestFixture mySchemaFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    mySchemaFixture = schemaFixture.get();
    Registry.get("json.schema.use.networknt.validation").setValue(false, myFixture.getTestRootDisposable());
  }

  @NotNull
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/backend/tests/testData/jsonSchema/highlighting/resharper";
  }

  private void doTestFiles(String file, String schema) throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/" + schema + ".json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/" + file + ".json"));
    mySchemaFixture.configureInitially(schemaText, inputText, "json");
    myFixture.getFile().getVirtualFile().putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, getTestDataPath() + "/" + file + ".json");
    myFixture.checkHighlighting(true, false, false);
  }

  //  generated code below
  @Test
  public void test001() throws Exception {
    doTestFiles("test001", "schema001");
  }
  @Test
  public void test002() throws Exception {
    doTestFiles("test002", "schema002");
  }
  @Test
  public void test003() throws Exception {
    doTestFiles("test003", "schema003");
  }
  @Test
  public void test004() throws Exception {
    doTestFiles("test004", "schema004");
  }
  @Test
  public void test004_2() throws Exception {
    doTestFiles("test004_2", "schema004");
  }
  @Test
  public void test005() throws Exception {
    doTestFiles("test005", "schema005");
  }
  @Test
  public void test005_2() throws Exception {
    doTestFiles("test005_2", "schema005");
  }
  @Test
  public void test006() throws Exception {
    doTestFiles("test006", "schema006");
  }
  @Test
  public void test007() throws Exception {
    doTestFiles("test007", "schema007");
  }
  @Test
  public void test008() throws Exception {
    doTestFiles("test008", "schema008");
  }
  @Test
  public void test008_2() throws Exception {
    doTestFiles("test008_2", "schema008");
  }
  @Test
  public void test008_3() throws Exception {
    doTestFiles("test008_3", "schema008");
  }
  @Test
  public void test009() throws Exception {
    doTestFiles("test009", "schema009");
  }
  @Test
  public void test010() throws Exception {
    doTestFiles("test010", "schema010");
  }
  @Test
  public void test011() throws Exception {
    doTestFiles("test011", "schema011");
  }
  @Test
  public void test012() throws Exception {
    doTestFiles("test012", "schema012");
  }
  @Test
  public void test012_2() throws Exception {
    doTestFiles("test012_2", "schema012");
  }
  @Test
  public void test012_3() throws Exception {
    doTestFiles("test012_3", "schema012");
  }
  @Test
  public void test013() throws Exception {
    doTestFiles("test013", "schema013");
  }
  @Test
  public void test014() throws Exception {
    doTestFiles("test014", "schema014");
  }
  @Test
  public void test015() throws Exception {
    doTestFiles("test015", "schema015");
  }
  @Test
  public void test016() throws Exception {
    doTestFiles("test016", "schema016");
  }
  @Test
  public void test016_2() throws Exception {
    doTestFiles("test016_2", "schema016");
  }
  @Test
  public void test016_3() throws Exception {
    doTestFiles("test016_3", "schema016");
  }
  @Test
  public void test016_4() throws Exception {
    doTestFiles("test016_4", "schema016");
  }
  @Test
  public void test016_5() throws Exception {
    doTestFiles("test016_5", "schema016");
  }
  @Test
  public void test017() throws Exception {
    doTestFiles("test017", "schema017");
  }
  @Test
  public void test017_2() throws Exception {
    doTestFiles("test017_2", "schema017");
  }
  @Test
  public void test017_3() throws Exception {
    doTestFiles("test017_3", "schema017");
  }
  @Test
  public void test018() throws Exception {
    doTestFiles("test018", "schema018");
  }
  @Test
  public void test019() throws Exception {
    doTestFiles("test019", "schema019");
  }
  @Test
  public void test019_2() throws Exception {
    doTestFiles("test019_2", "schema019");
  }
  @Test
  public void test020() throws Exception {
    doTestFiles("test020", "schema020");
  }
  @Test
  public void test020_2() throws Exception {
    doTestFiles("test020_2", "schema020");
  }
  @Test
  public void test021() throws Exception {
    doTestFiles("test021", "schema021");
  }
  @Test
  public void test022() throws Exception {
    doTestFiles("test022", "schema022");
  }
  @Test
  public void test023() throws Exception {
    doTestFiles("test023", "schema023");
  }
  @Test
  public void test024() throws Exception {
    doTestFiles("test024", "schema024");
  }
  @Test
  public void test025() throws Exception {
    doTestFiles("test025", "schema025");
  }
  @Test
  public void test026() throws Exception {
    doTestFiles("test026", "schema026");
  }
  public void _test027() throws Exception { // todo file refs cannot be resolved in tests for now
    doTestFiles("test027", "schema027");
  }
  @Test
  public void test028() throws Exception {
    doTestFiles("test028", "schema028");
  }
  public void _test029() throws Exception { // TODO bug
    doTestFiles("test029", "schema029");
  }
  @Test
  public void test030() throws Exception {
    doTestFiles("test030", "schema030");
  }

}
