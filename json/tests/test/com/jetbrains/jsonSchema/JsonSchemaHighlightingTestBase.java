// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.Predicate;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public abstract class JsonSchemaHighlightingTestBase extends BasePlatformTestCase {
  protected abstract String getTestFileName();
  protected abstract InspectionProfileEntry getInspectionProfile();
  protected abstract Predicate<VirtualFile> getAvailabilityPredicate();

  protected void doTest(@Language("JSON") @NotNull String schema,
                        @NotNull String text) {
    configureInitially(schema, text, "json");
    myFixture.checkHighlighting(true, false, false);
  }

  @NotNull
  protected PsiFile configureInitially(@NotNull String schema,
                                       @NotNull String text,
                                       @NotNull String schemaExt) {
    myFixture.enableInspections(getInspectionProfile());
    registerProvider(schema, schemaExt);
    return myFixture.configureByText(getTestFileName(), text);
  }

  protected void registerProvider(@NotNull String schema, @NotNull String schemaExt) {
    String path = "json_schema_test/schema." + schemaExt;
    VirtualFile tempFile = myFixture.findFileInTempDir(path);
    if (tempFile != null) return; // further doTest invocations
    PsiFile file = myFixture.addFileToProject(path, schema);
    JsonSchemaTestServiceImpl.setProvider(new JsonSchemaTestProvider(file.getVirtualFile(), getAvailabilityPredicate()));
    Disposer.register(getTestRootDisposable(), () -> JsonSchemaTestServiceImpl.setProvider(null));
    ServiceContainerUtil.replaceService(getProject(), JsonSchemaService.class, new JsonSchemaTestServiceImpl(getProject()), getTestRootDisposable());
  }
}
