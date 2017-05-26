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

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.JsonSchemaHeavyAbstractTest;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import org.junit.Assert;

import java.util.Collections;

/**
 * @author Irina.Chernushina on 4/1/2016.
 */
public class JsonSchemaFileResolveTest extends JsonSchemaHeavyAbstractTest {
  @Override
  protected String getBasePath() {
    return "/tests/testData/jsonSchema/schemaFile/resolve";
  }

  public void testResolveLocalRef() throws Exception {
    skeleton(new Callback() {
      @Override
      public void doCheck() {
        final int offset = getEditor().getCaretModel().getCurrentCaret().getOffset();
        final PsiElement atOffset = PsiTreeUtil.findElementOfClassAtOffset(myFile, offset, PsiElement.class, false);
        Assert.assertNotNull(atOffset);
        PsiReference position = myFile.findReferenceAt(offset);
        Assert.assertNotNull(position);
        PsiElement resolve = position.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("\"baseEnum\"", resolve.getText());
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFile("localRefSchema.json");
      }

      @Override
      public void registerSchemes() {
        final String path = VfsUtilCore.getRelativePath(myFile.getVirtualFile(), myProject.getBaseDir());
        final UserDefinedJsonSchemaConfiguration info =
          new UserDefinedJsonSchemaConfiguration("test", path, false, Collections.emptyList());
        JsonSchemaFileResolveTest.this.addSchema(info);
      }
    });
  }
}
