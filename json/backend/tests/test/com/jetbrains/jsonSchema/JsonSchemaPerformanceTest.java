// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.JsonFileType;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.RegistryKey;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ThrowableRunnable;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaDeprecationInspection;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaRefReferenceInspection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHeavyTestFixtureKt.jsonSchemaHeavyFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixture.registerJsonSchema;

@PerformanceUnitTest
@StressTestApplication
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData/jsonSchema/performance")
@RegistryKey(key = "json.schema.use.networknt.validation", value = "false")
public class JsonSchemaPerformanceTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHeavyTestFixture> schemaFixture = jsonSchemaHeavyFixture(codeInsightFixture);

  protected CodeInsightTestFixture myFixture;
  protected JsonSchemaHeavyTestFixture mySchemaFixture;
  protected Disposable myTestRootDisposable;

  @BeforeEach
  void setUp(@TestDisposable Disposable testRootDisposable) {
    myFixture = codeInsightFixture.get();
    mySchemaFixture = schemaFixture.get();
    myTestRootDisposable = testRootDisposable;
  }

  @Test
  public void testAzureHighlightingAgainstNewSchemaImplementation() throws IOException {
    doTestAzurePerformance(true);
  }

  @Test
  public void testAzureHighlightingAgainstOldSchemaImplementation() throws IOException {
    doTestAzurePerformance(false);
  }

  private void doTestAzurePerformance(boolean useNewImplementation) throws IOException {
    Registry.get("json.schema.object.v2").setValue(useNewImplementation, myTestRootDisposable);

    Benchmark.newBenchmark("Highlight azure json by schema", () -> {
      myFixture.enableInspections(JsonSchemaComplianceInspection.class);
      myFixture.enableInspections(JsonSchemaRefReferenceInspection.class);
      myFixture.enableInspections(JsonSchemaDeprecationInspection.class);
      String schemaText = FileUtil.loadFile(new File(myFixture.getTestDataPath() + "/azure-schema.json"));
      registerJsonSchema(myFixture, schemaText, "json", it -> true);
      myFixture.configureByFile("/azure-file.json");
      myFixture.checkHighlighting(true, false, true);
    }).attempts(5).start();
  }

  @Test
  public void testAzureRehighlightAfterTyping() throws IOException {
    doTestAzureRehighlight();
  }

  protected void doTestAzureRehighlight() throws IOException {
    myFixture.enableInspections(JsonSchemaComplianceInspection.class);
    String schemaText = FileUtil.loadFile(new File(myFixture.getTestDataPath() + "/azure-schema.json"));
    registerJsonSchema(myFixture, schemaText, "json", it -> true);
    myFixture.configureByFile("/azure-file.json");

    // warmup — initial highlighting
    myFixture.doHighlighting();

    Benchmark.newBenchmark("Azure rehighlight after typing", () -> {
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        myFixture.getEditor().getDocument().insertString(1, "x");
      });
      myFixture.doHighlighting();
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        myFixture.getEditor().getDocument().deleteString(1, 2);
      });
      myFixture.doHighlighting();
    }).warmupIterations(10).attempts(100).start();
  }

  @Test
  public void testSwaggerRehighlightAfterTyping() throws Exception {
    doTestSwaggerRehighlight();
  }

  protected void doTestSwaggerRehighlight() throws Exception {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = mySchemaFixture.getUrlUnderTestRoot("testSwaggerHighlighting");
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration("swagger", JsonSchemaVersion.SCHEMA_4,
                                                                         moduleDir + "/swagger.json", false,
                                                                         Collections.emptyList()));
        mySchemaFixture.setDoCompletion(false);
      }

      @Override
      public void configureFiles() {
        myFixture.enableInspections(JsonSchemaComplianceInspection.class);
        myFixture.configureByFiles("/swagger.json");
      }

      @Override
      public void doCheck() {
        // warmup
        myFixture.doHighlighting();

        Benchmark.newBenchmark("Swagger rehighlight after typing", () -> {
          WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
            myFixture.getEditor().getDocument().insertString(1, "x");
          });
          myFixture.doHighlighting();
          WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
            myFixture.getEditor().getDocument().deleteString(1, 2);
          });
          myFixture.doHighlighting();
        }).warmupIterations(10).attempts(50).start();
      }
    });
  }

  @Test
  public void testSwaggerHighlighting() {
    doPerformanceTest("swagger");
  }

  @Test
  public void testTsLintSchema() {
    doPerformanceTest("tslint-schema");
  }

  protected void doPerformanceTest(String jsonFileNameWithoutExtension) {
    final ThrowableRunnable<Exception> test = () -> mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = mySchemaFixture.getUrlUnderTestRoot(mySchemaFixture.getTestName(true));
        mySchemaFixture.addSchema(new UserDefinedJsonSchemaConfiguration(jsonFileNameWithoutExtension, JsonSchemaVersion.SCHEMA_4,
                                                                         moduleDir + "/" + jsonFileNameWithoutExtension + ".json", false,
                                                                         Collections.emptyList()));
        mySchemaFixture.setDoCompletion(false);
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
    Benchmark.newBenchmark(mySchemaFixture.getTestName(false), test).attempts(5).start();
  }

  @Test
  public void testEslintHighlightingPerformance() {
    Benchmark.newBenchmark(mySchemaFixture.getTestName(true), () -> {
      PsiFile psiFile = myFixture.configureByFile(mySchemaFixture.getTestName(true) + "/.eslintrc.json");

      for (int i = 0; i < 10; i++) {
        myFixture.doHighlighting();

        final int index = i;
        final JsonProperty camelcase = ReadAction.computeBlocking(() -> {
          Assertions.assertInstanceOf(JsonFile.class, psiFile);
          final JsonValue value = ((JsonFile)psiFile).getTopLevelValue();
          Assertions.assertInstanceOf(JsonObject.class, value);
          final JsonProperty rules = ((JsonObject)value).findProperty("rules");
          Assertions.assertNotNull(rules);
          Assertions.assertInstanceOf(JsonObject.class, rules.getValue());
          return ((JsonObject)rules.getValue()).findProperty("camelcase");
        });
        Assertions.assertNotNull(camelcase);
        final JsonProperty a = ReadAction.computeBlocking(() -> {
          final PsiFile dummyFile = PsiFileFactory.getInstance(myFixture.getProject())
            .createFileFromText("1.json", JsonFileType.INSTANCE, "{\"a\": " + (index % 2 == 0 ? 1 : 2) + "}");
          return ((JsonObject)((JsonFile)dummyFile).getTopLevelValue()).findProperty("a");
        });
        Assertions.assertNotNull(a);
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), (Runnable)() -> camelcase.getValue().replace(a.getValue()));
        myFixture.doHighlighting();
      }
    }).start();
  }
}
