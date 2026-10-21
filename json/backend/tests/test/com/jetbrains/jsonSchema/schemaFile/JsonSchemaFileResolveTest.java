/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.jsonSchema.schemaFile;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.jsonSchema.JsonSchemaHeavyTestFixture;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHeavyTestFixtureKt.jsonSchemaHeavyFixture;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData/jsonSchema/schemaFile/resolve")
public class JsonSchemaFileResolveTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHeavyTestFixture> schemaFixture = jsonSchemaHeavyFixture(codeInsightFixture);

  private CodeInsightTestFixture myFixture;
  private JsonSchemaHeavyTestFixture mySchemaFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    mySchemaFixture = schemaFixture.get();
    mySchemaFixture.setDoCompletion(false);
  }

  @Test
  public void testResolveLocalRef() {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void doCheck() {
        ReadAction.runBlocking(() -> {
          final int offset = myFixture.getEditor().getCaretModel().getCurrentCaret().getOffset();
          final PsiElement atOffset = PsiTreeUtil.findElementOfClassAtOffset(myFixture.getFile(), offset, PsiElement.class, false);
          Assertions.assertNotNull(atOffset);
          PsiReference position = myFixture.getFile().findReferenceAt(offset);
          Assertions.assertNotNull(position);
          PsiElement resolve = position.resolve();
          Assertions.assertNotNull(resolve);
          Assertions.assertEquals("""
                                {
                                      "type": "string",
                                      "enum": ["one", "two"]
                                    }""", resolve.getText());
        });
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFile("localRefSchema.json");
      }

      @Override
      public void registerSchemes() {
        final String path = mySchemaFixture.getUrlUnderTestRoot("localRefSchema.json");
        final UserDefinedJsonSchemaConfiguration info =
          new UserDefinedJsonSchemaConfiguration("test", JsonSchemaVersion.SCHEMA_4, path, false, Collections.emptyList());
        mySchemaFixture.addSchema(info);
      }
    });
  }
}
