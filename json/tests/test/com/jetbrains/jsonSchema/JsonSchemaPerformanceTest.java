// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;

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
    final ThrowableRunnable<Exception> test = () -> skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("swagger", moduleDir + "/swagger.json", false, Collections.emptyList()));
        myDoCompletion = false;
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/swagger.json");
      }

      @Override
      public void doCheck() {
        doHighlighting();
      }
    });
    PlatformTestUtil.startPerformanceTest(getTestName(false), 20000, test).attempts(1).usesAllCPUCores().assertTiming();
  }
}
