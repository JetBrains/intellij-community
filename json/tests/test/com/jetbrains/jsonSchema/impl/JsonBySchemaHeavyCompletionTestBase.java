// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.jetbrains.jsonSchema.JsonSchemaHeavyAbstractTest;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public abstract class JsonBySchemaHeavyCompletionTestBase extends JsonSchemaHeavyAbstractTest {
  protected void baseCompletionTest(@SuppressWarnings("SameParameterValue") final String folder,
                                  @SuppressWarnings("SameParameterValue") final String testFile, @NotNull String... items) throws Exception {
    baseTest(folder, testFile, () -> {
      complete();
      assertStringItems(items);
    });
  }

  protected void baseInsertTest(@SuppressWarnings("SameParameterValue") final String folder, final String testFile) throws Exception {
    baseTest(folder, testFile, () -> {
      final CodeCompletionHandlerBase handlerBase = new CodeCompletionHandlerBase(CompletionType.BASIC);
      handlerBase.invokeCompletion(getProject(), getEditor());
      if (myItems != null) {
        selectItem(myItems[0]);
      }
      try {
        checkResultByFile("/" + folder + "/" + testFile + "_after." + getExtensionWithoutDot());
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  protected abstract String getExtensionWithoutDot();

  protected void baseTest(@NotNull final String folder, @NotNull final String testFile, @NotNull final Runnable checker) throws Exception {
    skeleton(new JsonSchemaHeavyAbstractTest.Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());

        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, moduleDir + "/Schema.json", false,
                                                 Collections
                                                   .singletonList(new UserDefinedJsonSchemaConfiguration.Item(testFile + "." + getExtensionWithoutDot(), true, false))
          );
        addSchema(base);
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/" + folder + "/" + testFile + "." + getExtensionWithoutDot(), "/" + folder + "/Schema.json");
      }

      @Override
      public void doCheck() {
        checker.run();
      }
    });
  }
}
