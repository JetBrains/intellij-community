/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.jsonSchema.schemaFile;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.JsonSchemaHeavyAbstractTest;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
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

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDoCompletion = false;
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
          new UserDefinedJsonSchemaConfiguration("test", JsonSchemaVersion.SCHEMA_4, path, false, Collections.emptyList());
        JsonSchemaFileResolveTest.this.addSchema(info);
      }
    });
  }
}
