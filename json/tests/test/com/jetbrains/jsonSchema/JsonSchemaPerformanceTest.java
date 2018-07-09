// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;

import java.util.Collections;

/**
 * @author Irina.Chernushina on 10/9/2017.
 */
public class JsonSchemaPerformanceTest extends JsonSchemaHeavyAbstractTest {
  public static final String BASE_PATH = "/tests/testData/jsonSchema/performance/";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  public void testSwaggerHighlighting() {
    doPerformanceTest(8000, "swagger");
  }

  public void testTsLintSchema() {
    doPerformanceTest(7000, "tslint-schema");
  }

  private void doPerformanceTest(int expectedMs, String jsonFileNameWithoutExtension) {
    final ThrowableRunnable<Exception> test = () -> skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration(jsonFileNameWithoutExtension, JsonSchemaVersion.SCHEMA_4,
                                                         moduleDir + "/" + jsonFileNameWithoutExtension +  ".json", false, Collections.emptyList()));
        myDoCompletion = false;
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/" + jsonFileNameWithoutExtension + ".json");
      }

      @Override
      public void doCheck() {
        doHighlighting();
      }
    });
    PlatformTestUtil.startPerformanceTest(getTestName(false), expectedMs, test).attempts(1).usesAllCPUCores().assertTiming();
  }
}
