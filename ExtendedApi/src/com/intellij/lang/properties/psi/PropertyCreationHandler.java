/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.lang.properties.psi;

import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.PsiExpression;

import java.util.Collection;

/**
 * @author nik
 */
public interface PropertyCreationHandler {

  void createProperty(Project project, Collection<PropertiesFile> propertiesFiles, String key, String value, final PsiExpression[] parameters) throws
                                                                                                             IncorrectOperationException;

}
