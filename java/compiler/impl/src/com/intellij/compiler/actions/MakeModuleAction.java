// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.actions;

import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.task.ProjectTaskManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public class MakeModuleAction extends CompileActionBase {
  private static final Logger LOG = Logger.getInstance(MakeModuleAction.class);

  @Override
  protected void doAction(@NotNull DataContext dataContext, Project project) {
    try {
      final Module[] modules = dataContext.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
      if (modules != null) {
        ProjectTaskManager.getInstance(project).build(modules);
      }
      else {
        final Module module = dataContext.getData(PlatformCoreDataKeys.MODULE);
        if (module != null) {
          ProjectTaskManager.getInstance(project).build(module);
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    if (!presentation.isEnabled()) {
      return;
    }
    boolean isEnabled = false;
    String presentationText;
    final Module[] modules = event.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
    if (modules != null) {
      isEnabled = true;
      if (ArrayUtil.contains(null, modules)) {
        final DataContext dataContext = event.getDataContext();
        LOG.error("Unexpected null module slipped through validator; dataContext = " + dataContext + "; class = "+dataContext.getClass().getName());
      }
      if (modules.length == 1) {
        presentationText = JavaCompilerBundle.message("action.make.single.module.text", modules[0].getName());
      }
      else {
        String moduleNames = Stream.of(modules).map(m -> "'"+m.getName()+"'").collect(NlsMessages.joiningNarrowAnd());
        presentationText = moduleNames.length() > 20 ?
                           JavaCompilerBundle.message("action.make.selected.modules.text") :
                           JavaCompilerBundle.message("action.make.few.modules.text", moduleNames);
      }
    }
    else if (!event.isFromContextMenu()) {
      final Module module = event.getData(PlatformCoreDataKeys.MODULE);
      if (module != null) {
        isEnabled = true;
        presentationText = JavaCompilerBundle.message("action.make.single.module.text", module.getName());
      }
      else {
        presentationText = getTemplatePresentation().getTextWithMnemonic();
      }
    }
    else {
      presentationText = getTemplatePresentation().getTextWithMnemonic();
    }
    presentation.setText(presentationText);
    presentation.setEnabled(isEnabled);
    presentation.setVisible(isEnabled || !ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()));
  }
}