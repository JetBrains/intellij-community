// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.fileTemplates.ui;

import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;

/*
 * @author MYakovlev
 */
public final class ConfigureTemplatesDialog extends SingleConfigurableEditor{
  private static final String DIMENSION_KEY = "#com.intellij.ide.fileTemplates.ui.ConfigureTemplatesDialog";

  public ConfigureTemplatesDialog(Project project){
    this(project, new AllFileTemplatesConfigurable(project));
  }

  public ConfigureTemplatesDialog(Project project, AllFileTemplatesConfigurable fileTemplatesConfigurable){
    super(project, fileTemplatesConfigurable);
  }

  @Override
  protected String getDimensionServiceKey(){
    return DIMENSION_KEY;
  }
}
