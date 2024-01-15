// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.idea.HardwareAgentRequired;
import com.intellij.json.JsonFileType;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase.registerJsonSchema;

@HardwareAgentRequired
public class JsonSchemaPerformanceTest extends JsonSchemaHeavyAbstractTest {
  public static final String BASE_PATH = "/tests/testData/jsonSchema/performance/";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  public void testAzureHighlighting() throws IOException {
    String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/azure-schema.json"));
    registerJsonSchema(myFixture, schemaText, "json", it -> true);

    myFixture.enableInspections(JsonSchemaComplianceInspection.class);
    myFixture.configureByFile("/azure-file.json");
    myFixture.testHighlighting(true, false, true);
  }

  public void testSwaggerHighlighting() {
    doPerformanceTest(35_000, "swagger");
  }

  public void testTsLintSchema() {
    doPerformanceTest(20_000, "tslint-schema");
  }

  private void doPerformanceTest(int expectedMs, String jsonFileNameWithoutExtension) {
    myFixture.configureByFiles("/" + jsonFileNameWithoutExtension + ".json");
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // process VFS events before perf test

    final ThrowableRunnable<Exception> test = () -> skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getUrlUnderTestRoot(getTestName(true));
        addSchema(new UserDefinedJsonSchemaConfiguration(jsonFileNameWithoutExtension, JsonSchemaVersion.SCHEMA_4,
                                                         moduleDir + "/" + jsonFileNameWithoutExtension + ".json", false,
                                                         Collections.emptyList()));
        myDoCompletion = false;
      }

      @Override
      public void configureFiles() {
        // files have been configured before the performance test started to not influence the results
      }

      @Override
      public void doCheck() {
        myFixture.doHighlighting();
      }
    });
    PlatformTestUtil.startPerformanceTest(getTestName(false), expectedMs, test).usesAllCPUCores().assertTiming();
  }


  public void testEslintHighlightingPerformance() {
    myFixture.configureByFile(getTestName(true) + "/.eslintrc.json");
    PsiFile psiFile = myFixture.getFile();
    PlatformTestUtil.startPerformanceTest(getTestName(true), (int)TimeUnit.SECONDS.toMillis(15), () -> {
      for (int i = 0; i < 10; i++) {
        myFixture.doHighlighting();

        Assert.assertTrue(psiFile instanceof JsonFile);
        final JsonValue value = ((JsonFile)psiFile).getTopLevelValue();
        Assert.assertTrue(value instanceof JsonObject);
        final JsonProperty rules = ((JsonObject)value).findProperty("rules");
        Assert.assertNotNull(rules);
        Assert.assertTrue(rules.getValue() instanceof JsonObject);

        final JsonProperty camelcase = ((JsonObject)rules.getValue()).findProperty("camelcase");
        Assert.assertNotNull(camelcase);
        final PsiFile dummyFile = PsiFileFactory.getInstance(getProject())
          .createFileFromText("1.json", JsonFileType.INSTANCE, "{\"a\": " + (i % 2 == 0 ? 1 : 2) + "}");
        final JsonProperty a = ((JsonObject)((JsonFile)dummyFile).getTopLevelValue()).findProperty("a");
        Assert.assertNotNull(a);
        WriteCommandAction.runWriteCommandAction(getProject(), (Runnable)() -> camelcase.getValue().replace(a.getValue()));
        myFixture.doHighlighting();
      }
    }).assertTiming();
  }
}
