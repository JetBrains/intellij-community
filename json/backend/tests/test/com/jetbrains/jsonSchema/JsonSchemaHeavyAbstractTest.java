// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class JsonSchemaHeavyAbstractTest extends BasePlatformTestCase {
  private Map<String, UserDefinedJsonSchemaConfiguration> mySchemas;
  protected LookupElement[] myItems;
  protected boolean myDoCompletion = true;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySchemas = new HashMap<>();
    myDoCompletion = true;
  }

  @Override
  public void tearDown() throws Exception {
    myItems = null;
    try {
      final JsonSchemaMappingsProjectConfiguration instance = JsonSchemaMappingsProjectConfiguration.getInstance(getProject());
      instance.setState(Collections.emptyMap());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  @Override
  public String getTestDataPath() {
    PathManagerEx.TestDataLookupStrategy strategy = PathManagerEx.guessTestDataLookupStrategy();
    if (strategy.equals(PathManagerEx.TestDataLookupStrategy.COMMUNITY)) {
      return PathManager.getHomePath() + "/json/backend" + getBasePath() + "/";
    }
    return PathManager.getHomePath() + "/community/json/backend" + getBasePath() + "/";
  }

  public static @NotNull String getJsonSchemaTestDataFilePath(@NotNull String jsonSchemaRelativePath) {
    PathManagerEx.TestDataLookupStrategy strategy = PathManagerEx.guessTestDataLookupStrategy();
    String prefix;
    if (strategy.equals(PathManagerEx.TestDataLookupStrategy.COMMUNITY)) {
      prefix = PathManager.getHomePath() + "/json/";
    }
    else {
      prefix = PathManager.getHomePath() + "/community/json/";
    }
    return prefix + "backend/tests/testData/jsonSchema/" + jsonSchemaRelativePath;
  }

  @Override
  protected abstract String getBasePath();

  protected void skeleton(@NotNull final Callback callback)  throws Exception {
    callback.configureFiles();
    callback.registerSchemes();
    JsonSchemaMappingsProjectConfiguration.getInstance(getProject()).setState(mySchemas);
    JsonSchemaService.Impl.get(getProject()).reset();
    getPsiManager().dropPsiCaches();
    myFixture.doHighlighting();
    if (myDoCompletion) {
      complete();
    }
    callback.doCheck();
  }

  protected void complete() {
    myItems = myFixture.complete(CompletionType.BASIC);
  }

  protected interface Callback {
    void registerSchemes();
    void configureFiles() throws Exception;
    void doCheck() throws Exception;
  }

  protected void addSchema(@NotNull final UserDefinedJsonSchemaConfiguration schema) {
    mySchemas.put(schema.getName(), schema);
  }

  protected void assertStringItems(String... strings) {
    assertNotNull(myItems);
    List<String> actual = ContainerUtil.map(myItems, element -> element.getLookupString());
    assertOrderedEquals(actual, strings);
  }

  protected void selectItem(LookupElement item, char ch) {
    final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(getProject()).getActiveLookup();
    assert lookup != null;
    lookup.setCurrentItem(item);
    lookup.finishLookup(ch);
  }

  protected void checkResultByFile(String testDataFile) throws Exception {
    String path = getTestDataPath();
    path = StringUtil.trimEnd(path, "/");
    path = StringUtil.trimEnd(path, "\\");
    myFixture.checkResult(PlatformTestUtil.loadFileText(path + File.separator + StringUtil.trimStart(testDataFile, "/")), false);
  }

  protected String getUrlUnderTestRoot(String path) {
    return JsonFileResolver.TEMP_URL + "src/" + StringUtil.trimStart(path, "/");
  }

  @NotNull
  protected VirtualFile locateFileUnderTestRoot(String path) {
    final VirtualFile schemaFile = TempFileSystem.getInstance().findFileByPath("/src/" + path);
    Assert.assertNotNull(schemaFile);
    return schemaFile;
  }
}
