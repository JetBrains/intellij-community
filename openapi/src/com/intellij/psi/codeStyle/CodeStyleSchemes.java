/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.codeStyle;

import com.intellij.openapi.application.ApplicationManager;

/**
 * @author MYakovlev
 * Date: Jul 19, 2002
 */
public abstract class CodeStyleSchemes {

  public static CodeStyleSchemes getInstance(){
    return ApplicationManager.getApplication().getComponent(CodeStyleSchemes.class);
  }

  public abstract CodeStyleScheme[] getSchemes();

  public abstract CodeStyleScheme getCurrentScheme();

  public abstract void setCurrentScheme(CodeStyleScheme scheme);

  public abstract CodeStyleScheme createNewScheme(String preferredName, CodeStyleScheme parentScheme);

  public abstract void deleteScheme(CodeStyleScheme scheme);

  public abstract CodeStyleScheme findSchemeByName(String name);

  public abstract CodeStyleScheme getDefaultScheme();

  public abstract void addScheme(CodeStyleScheme currentScheme);
}

