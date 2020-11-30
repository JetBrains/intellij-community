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
import java.util.Collections;
import java.util.List;

@State(name = "GetterTemplates", storages = @Storage("getterTemplates.xml"))
public final class GetterTemplatesManager extends TemplatesManager {
  private static final String DEFAULT = "defaultGetter.vm";

  public static GetterTemplatesManager getInstance() {
    return ApplicationManager.getApplication().getService(GetterTemplatesManager.class);
  }

  @Override
  public @NotNull List<TemplateResource> getDefaultTemplates() {
    try {
      return Collections.singletonList(new TemplateResource("IntelliJ Default", readFile(DEFAULT), true));
    }
    catch (IOException e) {
      throw new TemplateResourceException("Error loading default templates", e);
    }
  }

  protected static String readFile(String resource) throws IOException {
    return readFile(resource, GetterTemplatesManager.class);
  }
}
