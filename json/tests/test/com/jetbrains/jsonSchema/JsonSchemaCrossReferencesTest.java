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
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.completion.CompletionTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Assert;

import java.util.Collections;

/**
 * @author Irina.Chernushina on 3/28/2016.
 */
public class JsonSchemaCrossReferencesTest extends CompletionTestCase {
  private final static String BASE_PATH = "/tests/testData/jsonSchema/crossReferences";

  @Override
  protected String getTestDataPath() {
    PathManagerEx.TestDataLookupStrategy strategy = PathManagerEx.guessTestDataLookupStrategy();
    if (strategy.equals(PathManagerEx.TestDataLookupStrategy.COMMUNITY)) {
      return PathManager.getHomePath() + "/plugins/json";
    }
    return PathManager.getHomePath() + "/community/json";
  }

  public void testJsonSchemaCrossReferenceCompletion() throws Exception {
    configureByFiles(null, BASE_PATH + "/completion.json", BASE_PATH + "/base.json",
                          BASE_PATH + "/inherited.json");

    String moduleDir = null;
    VirtualFile[] children = getProject().getBaseDir().getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        moduleDir = child.getName();
        break;
      }
    }
    Assert.assertNotNull(moduleDir);

    final JsonSchemaMappingsProjectConfiguration instance = JsonSchemaMappingsProjectConfiguration.getInstance(getProject());
    final JsonSchemaMappingsConfigurationBase.SchemaInfo base =
      new JsonSchemaMappingsConfigurationBase.SchemaInfo("base", "/" + moduleDir + "/base.json", false, Collections.emptyList());
    instance.addSchema(base);

    final JsonSchemaMappingsConfigurationBase.SchemaInfo inherited
      = new JsonSchemaMappingsConfigurationBase.SchemaInfo("inherited", "/" + moduleDir + "/inherited.json", false,
                                                           Collections.singletonList(new JsonSchemaMappingsConfigurationBase.Item("*.json", true, false)));

    instance.addSchema(inherited);

    complete();
    assertStringItems("\"one\"", "\"two\"");

    instance.removeSchema(inherited);
    instance.removeSchema(base);
  }
}
