/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

/**
 * Thrown on accessing indices in dumb mode. Possible fixes:
 * <li> if {@link com.intellij.openapi.actionSystem.AnAction#actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent)} is in stack trace,
 * consider making the action not implement {@link com.intellij.openapi.project.DumbAware}.
 * <li> if this access is performed from some invokeLater activity, consider replacing it with
 * {@link com.intellij.openapi.project.DumbService#smartInvokeLater(Runnable)}
 * <li> otherwise, add {@link DumbService#isDumb()} checks where necessary
 *
 * @see com.intellij.openapi.project.DumbService
 * @see com.intellij.openapi.project.DumbAware
 * @author peter
 */
public class IndexNotReadyException extends RuntimeException{
}
