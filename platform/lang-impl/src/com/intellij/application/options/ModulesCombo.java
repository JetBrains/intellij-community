// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;

import java.util.Collection;

public interface ModulesCombo {
  Module getSelectedModule();
  void setSelectedModule(Module module);
  void setModules(Collection<? extends Module> modules);
  void allowEmptySelection(@NlsContexts.ListItem String noModuleText);

  String getSelectedModuleName();
  void setSelectedModule(Project project, String name);
}
