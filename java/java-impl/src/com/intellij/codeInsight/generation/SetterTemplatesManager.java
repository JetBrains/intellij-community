// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.exception.TemplateResourceException;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@State(name = "SetterTemplates", storages = @Storage("setterTemplates.xml"))
public final class SetterTemplatesManager extends TemplatesManager {
  private static final String DEFAULT = "defaultSetter.vm";
  private static final String BUILDER = "builderSetter.vm";

  public static TemplatesManager getInstance() {
    return ApplicationManager.getApplication().getService(SetterTemplatesManager.class);
  }

  @Override
  public @NotNull List<TemplateResource> getDefaultTemplates() {
    try {
      return Arrays.asList(new TemplateResource("IntelliJ Default", readFile(DEFAULT), true),
                           new TemplateResource("Builder", readFile(BUILDER), true));
    }
    catch (IOException e) {
      throw new TemplateResourceException("Error loading default templates", e);
    }
  }

  protected static String readFile(String resource) throws IOException {
    return readFile(resource, SetterTemplatesManager.class);
  }
}
