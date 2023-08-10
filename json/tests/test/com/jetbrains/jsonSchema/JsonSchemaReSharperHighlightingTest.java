// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.function.Predicate;

public class JsonSchemaReSharperHighlightingTest extends JsonSchemaHighlightingTestBase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData/jsonSchema/highlighting/resharper";
  }

  @Override
  protected String getTestFileName() {
    return "config.json";
  }

  @Override
  protected InspectionProfileEntry getInspectionProfile() {
    return new JsonSchemaComplianceInspection();
  }

  @Override
  protected Predicate<VirtualFile> getAvailabilityPredicate() {
    return file -> file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(
      JsonLanguage.INSTANCE);
  }

  private void doTestFiles(String file, String schema) throws Exception {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/" + schema + ".json"));
    String inputText = FileUtil.loadFile(new File(getTestDataPath() + "/" + file + ".json"));
    configureInitially(schemaText, inputText, "json");
    myFixture.getFile().getVirtualFile().putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, getTestDataPath() + "/" + getName() + ".json");
    myFixture.checkHighlighting(true, false, false);
  }

  //  generated code below
  public void test001() throws Exception {
    doTestFiles("test001", "schema001");
  }
  public void test002() throws Exception {
    doTestFiles("test002", "schema002");
  }
  public void test003() throws Exception {
    doTestFiles("test003", "schema003");
  }
  public void test004() throws Exception {
    doTestFiles("test004", "schema004");
  }
  public void test004_2() throws Exception {
    doTestFiles("test004_2", "schema004");
  }
  public void test005() throws Exception {
    doTestFiles("test005", "schema005");
  }
  public void test005_2() throws Exception {
    doTestFiles("test005_2", "schema005");
  }
  public void test006() throws Exception {
    doTestFiles("test006", "schema006");
  }
  public void test007() throws Exception {
    doTestFiles("test007", "schema007");
  }
  public void test008() throws Exception {
    doTestFiles("test008", "schema008");
  }
  public void test008_2() throws Exception {
    doTestFiles("test008_2", "schema008");
  }
  public void test008_3() throws Exception {
    doTestFiles("test008_3", "schema008");
  }
  public void test009() throws Exception {
    doTestFiles("test009", "schema009");
  }
  public void test010() throws Exception {
    doTestFiles("test010", "schema010");
  }
  public void test011() throws Exception {
    doTestFiles("test011", "schema011");
  }
  public void test012() throws Exception {
    doTestFiles("test012", "schema012");
  }
  public void test012_2() throws Exception {
    doTestFiles("test012_2", "schema012");
  }
  public void test012_3() throws Exception {
    doTestFiles("test012_3", "schema012");
  }
  public void test013() throws Exception {
    doTestFiles("test013", "schema013");
  }
  public void test014() throws Exception {
    doTestFiles("test014", "schema014");
  }
  public void test015() throws Exception {
    doTestFiles("test015", "schema015");
  }
  public void test016() throws Exception {
    doTestFiles("test016", "schema016");
  }
  public void test016_2() throws Exception {
    doTestFiles("test016_2", "schema016");
  }
  public void test016_3() throws Exception {
    doTestFiles("test016_3", "schema016");
  }
  public void test016_4() throws Exception {
    doTestFiles("test016_4", "schema016");
  }
  public void test016_5() throws Exception {
    doTestFiles("test016_5", "schema016");
  }
  public void test017() throws Exception {
    doTestFiles("test017", "schema017");
  }
  public void test017_2() throws Exception {
    doTestFiles("test017_2", "schema017");
  }
  public void test017_3() throws Exception {
    doTestFiles("test017_3", "schema017");
  }
  public void test018() throws Exception {
    doTestFiles("test018", "schema018");
  }
  public void test019() throws Exception {
    doTestFiles("test019", "schema019");
  }
  public void test019_2() throws Exception {
    doTestFiles("test019_2", "schema019");
  }
  public void test020() throws Exception {
    doTestFiles("test020", "schema020");
  }
  public void test020_2() throws Exception {
    doTestFiles("test020_2", "schema020");
  }
  public void test021() throws Exception {
    doTestFiles("test021", "schema021");
  }
  public void test022() throws Exception {
    doTestFiles("test022", "schema022");
  }
  public void test023() throws Exception {
    doTestFiles("test023", "schema023");
  }
  public void test024() throws Exception {
    doTestFiles("test024", "schema024");
  }
  public void test025() throws Exception {
    doTestFiles("test025", "schema025");
  }
  public void test026() throws Exception {
    doTestFiles("test026", "schema026");
  }
  public void _test027() throws Exception { // todo file refs cannot be resolved in tests for now
    doTestFiles("test027", "schema027");
  }
  public void test028() throws Exception {
    doTestFiles("test028", "schema028");
  }
  public void _test029() throws Exception { // TODO bug
    doTestFiles("test029", "schema029");
  }
  public void test030() throws Exception {
    doTestFiles("test030", "schema030");
  }

}
