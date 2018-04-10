/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.jsonSchema.schemaFile;

import com.intellij.openapi.project.Project;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;

/**
 * @author Irina.Chernushina on 4/1/2016.
 */
public class TestJsonSchemaMappingsProjectConfiguration extends JsonSchemaMappingsProjectConfiguration {
  public TestJsonSchemaMappingsProjectConfiguration(Project project) {
    super(project);
  }
}
