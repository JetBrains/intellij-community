package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Jan 26, 2005
 */
public abstract class ChooseByNameFactory implements ProjectComponent{
  public static ChooseByNameFactory getInstance(Project project){
    return project.getComponent(ChooseByNameFactory.class);
  }

  public abstract ChooseByNamePopupComponent createChooseByNamePopupComponent(final ChooseByNameModel model);
}
