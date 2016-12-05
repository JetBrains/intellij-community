/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.schemaFile;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.JsonSchemaFileType;
import com.jetbrains.jsonSchema.JsonSchemaMappingsConfigurationBase;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.junit.Assert;

import java.util.Collections;

/**
 * @author Irina.Chernushina on 4/1/2016.
 */
public class JsonSchemaFileResolveTest extends DaemonAnalyzerTestCase {
  private final static String BASE_PATH = "/tests/testData/jsonSchema/schemaFile/resolve";
  private FileTypeManager myFileTypeManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFileTypeManager = FileTypeManagerEx.getInstanceEx();
  }

  @Override
  protected String getTestDataPath() {
    PathManagerEx.TestDataLookupStrategy strategy = PathManagerEx.guessTestDataLookupStrategy();
    if (strategy.equals(PathManagerEx.TestDataLookupStrategy.COMMUNITY)) {
      return PathManager.getHomePath() + "/json" + BASE_PATH + "/";
    }
    return PathManager.getHomePath() + "/community/json" + BASE_PATH + "/";
  }

  public void testResolveLocalRef() throws Exception {
    JsonSchemaMappingsConfigurationBase.SchemaInfo schemaInfo = null;
    try {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> myFileTypeManager.associatePattern(JsonSchemaFileType.INSTANCE, "*Schema.json"));
      configureByFile("localRefSchema.json");
      final String path = VfsUtil.getRelativePath(myFile.getVirtualFile(), myProject.getBaseDir());
      schemaInfo = new JsonSchemaMappingsConfigurationBase.SchemaInfo("test", path, false, Collections.emptyList());
      JsonSchemaMappingsProjectConfiguration.getInstance(getProject()).addSchema(schemaInfo);
      JsonSchemaService.Impl.get(myProject).reset();
      doHighlighting();

      final int offset = getEditor().getCaretModel().getCurrentCaret().getOffset();
      final PsiElement atOffset = PsiTreeUtil.findElementOfClassAtOffset(myFile, offset, PsiElement.class, false);
      Assert.assertNotNull(atOffset);
      PsiReference position = myFile.findReferenceAt(offset);
      Assert.assertNotNull(position);
      PsiElement resolve = position.resolve();
      Assert.assertNotNull(resolve);
      Assert.assertEquals("\"baseEnum\"", resolve.getText());

      WriteCommandAction.runWriteCommandAction(getProject(), () -> myFileTypeManager.removeAssociatedExtension(JsonSchemaFileType.INSTANCE, "*Schema.json"));
    } finally {
      if (schemaInfo != null) JsonSchemaMappingsProjectConfiguration.getInstance(getProject()).removeSchema(schemaInfo);
    }
  }
}
