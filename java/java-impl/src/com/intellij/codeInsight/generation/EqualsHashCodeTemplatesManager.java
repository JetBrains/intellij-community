/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.java.generate.exception.TemplateResourceException;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;

import java.io.IOException;

@State(
  name = "EqualsHashCodeTemplates",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/equalsHashCodeTemplates.xml"
    )}
)
public class EqualsHashCodeTemplatesManager extends TemplatesManager {
  private static final String DEFAULT_EQUALS = "com/intellij/codeInsight/generation/defaultEquals.vm";
  private static final String DEFAULT_HASH_CODE = "com/intellij/codeInsight/generation/defaultHashCode.vm";


  public static TemplatesManager getInstance() {
    return ServiceManager.getService(EqualsHashCodeTemplatesManager.class);
  }

  public EqualsHashCodeTemplatesManager() {
    super(getDefaultTemplates());
  }

  private static TemplateResource[] getDefaultTemplates() {
    try {
      return new TemplateResource[] {
        new TemplateResource("Default equals", readFile(DEFAULT_EQUALS), true),
        new TemplateResource("Default hashCode", readFile(DEFAULT_HASH_CODE), true),
      };
    }
    catch (IOException e) {
      throw new TemplateResourceException("Error loading default templates", e);
    }
  }

  private static String readFile(String resourceName) throws IOException {
    return readFile(resourceName, EqualsHashCodeTemplatesManager.class);
  }
}
