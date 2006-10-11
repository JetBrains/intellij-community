package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ProfilingUtil;

public class MakeModuleAction extends CompileActionBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.actions.MakeModuleAction");

  protected void doAction(DataContext dataContext, Project project) {
    Module[] modules = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);
    Module module;
    if (modules == null) {
      module = (Module)dataContext.getData(DataConstants.MODULE);
      if (module == null) {
        return;
      }
      modules = new Module[]{module};
    }
    try {
      ProfilingUtil.operationStarted("make");

      CompilerManager.getInstance(project).make(modules[0].getProject(), modules, null);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final Module module = (Module)dataContext.getData(DataConstants.MODULE);
    Module[] modules = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);
    final boolean isEnabled = module != null || modules != null;
    presentation.setEnabled(isEnabled);
    final String actionName = getTemplatePresentation().getTextWithMnemonic();

    String presentationText;
    if (modules != null) {
      String text = actionName;
      for (int i = 0; i < modules.length; i++) {
        if (text.length() > 30) {
          text = CompilerBundle.message("action.make.selected.modules.text");
          break;
        }
        Module toMake = modules[i];
        if (i!=0) {
          text += ",";
        }
        text += " '" + toMake.getName() + "'";
      }
      presentationText = text;
    }
    else if (module != null) {
      presentationText = actionName + " '" + module.getName() + "'";
    }
    else {
      presentationText = actionName;
    }
    presentation.setText(presentationText);
    presentation.setVisible(isEnabled || !ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()));
  }
}