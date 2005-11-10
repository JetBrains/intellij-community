package com.intellij.ide.actionMacro;

import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 22, 2003
 * Time: 3:30:56 PM
 * To change this template use Options | File Templates.
 */
public class EditMacrosDialog extends SingleConfigurableEditor {
  public EditMacrosDialog(Project project) {
    super(project, new ActionMacroConfigurable());
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.ide.actionMacro.EditMacrosDialog";
  }
}
