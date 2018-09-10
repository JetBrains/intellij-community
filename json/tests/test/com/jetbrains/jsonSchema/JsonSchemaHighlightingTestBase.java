// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.AreaPicoContainer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.Predicate;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public abstract class JsonSchemaHighlightingTestBase extends DaemonAnalyzerTestCase {

  protected abstract String getTestFileName();
  protected abstract InspectionProfileEntry getInspectionProfile();
  protected abstract Predicate<VirtualFile> getAvailabilityPredicate();

  protected void doTest(@Language("JSON") @NotNull final String schema, @NotNull final String text) throws Exception {
    final PsiFile file = configureInitially(schema, text);
    doTest(file.getVirtualFile(), true, false);
  }

  @NotNull
  protected PsiFile configureInitially(@NotNull @Language("JSON") String schema,
                                       @NotNull String text) throws Exception {
    enableInspectionTool(getInspectionProfile());

    final PsiFile file = doCreateFile(text);

    registerProvider(getProject(), schema);
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        JsonSchemaTestServiceImpl.setProvider(null);
      }
    });
    configureByFile(file.getVirtualFile());
    return file;
  }

  @NotNull
  protected PsiFile doCreateFile(@NotNull String text) throws Exception {
    return createFile(myModule, getTestFileName(), text);
  }

  private void registerProvider(Project project, @NotNull String schema) throws IOException {
    File dir = createTempDir("json_schema_test", true);
    File child = new File(dir, "schema.json");
    //noinspection ResultOfMethodCallIgnored
    child.createNewFile();
    FileUtil.writeToFile(child, schema);
    VirtualFile schemaFile = getVirtualFile(child);
    JsonSchemaTestServiceImpl.setProvider(new JsonSchemaTestProvider(schemaFile, getAvailabilityPredicate()));
    AreaPicoContainer container = Extensions.getArea(project).getPicoContainer();
    String key = JsonSchemaService.class.getName();
    container.unregisterComponent(key);
    container.registerComponentImplementation(key, JsonSchemaTestServiceImpl.class);
  }
}
