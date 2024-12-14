// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.json.JsonFileType;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ThrowableRunnable;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaDeprecationInspection;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaRefReferenceInspection;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase.registerJsonSchema;

public class JsonSchemaPerformanceTest extends JsonSchemaHeavyAbstractTest {
  public static final String BASE_PATH = "/tests/testData/jsonSchema/performance/";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  public void testAzureHighlightingAgainstNewSchemaImplementation() throws IOException {
    doTestAzurePerformance(true);
  }

  public void testAzureHighlightingAgainstOldSchemaImplementation() throws IOException {
    doTestAzurePerformance(false);
  }

  private void doTestAzurePerformance(boolean useNewImplementation) throws IOException {
    Registry.get("json.schema.object.v2").setValue(useNewImplementation);

    Benchmark.newBenchmark("Highlight azure json by schema", () -> {
      myFixture.enableInspections(JsonSchemaComplianceInspection.class);
      myFixture.enableInspections(JsonSchemaRefReferenceInspection.class);
      myFixture.enableInspections(JsonSchemaDeprecationInspection.class);
      String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/azure-schema.json"));
      registerJsonSchema(myFixture, schemaText, "json", it -> true);
      myFixture.configureByFile("/azure-file.json");
      myFixture.checkHighlighting(true, false, true);
    }).attempts(5).start();
  }

  public void testSwaggerHighlighting() {
    doPerformanceTest("swagger");
  }

  public void testTsLintSchema() {
    doPerformanceTest("tslint-schema");
  }

  private void doPerformanceTest(String jsonFileNameWithoutExtension) {
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
        myFixture.enableInspections(JsonSchemaComplianceInspection.class);
        myFixture.enableInspections(JsonSchemaRefReferenceInspection.class);
        myFixture.enableInspections(JsonSchemaDeprecationInspection.class);
        myFixture.configureByFiles("/" + jsonFileNameWithoutExtension + ".json");
      }

      @Override
      public void doCheck() {
        myFixture.doHighlighting();
      }
    });
    Benchmark.newBenchmark(getTestName(false), test).attempts(5).start();
  }

  public void testEslintHighlightingPerformance() {
    Benchmark.newBenchmark(getTestName(true), () -> {
      PsiFile psiFile = myFixture.configureByFile(getTestName(true) + "/.eslintrc.json");

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
    }).start();
  }
}
