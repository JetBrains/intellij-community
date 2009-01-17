package com.intellij.util.xml.actions.generate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.xml.DomElement;

/**
 * User: Sergey.Vasiliev
 */
public abstract class DomTemplateRunner {

  public static DomTemplateRunner getInstance(Project project) {
    return ServiceManager.getService(project, DomTemplateRunner.class);
  }
  
  public abstract <T extends DomElement> void  runTemplate(final T t, final String mappingId, final Editor editor);


}
