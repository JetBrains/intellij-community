/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

/**
 * A marker interface for the things that are allowed to run in dumb mode (when indices are in background update). Known implementors are:
 * <li> {@link com.intellij.openapi.actionSystem.AnAction}s
 * <li> {@link com.intellij.openapi.fileEditor.FileEditorProvider}s
 * <li> post-startup activities ({@link com.intellij.openapi.startup.StartupManager#registerPostStartupActivity(Runnable)})
 * <li> Stacktrace {@link com.intellij.execution.filters.Filter}s
 * <li> {@link com.intellij.ide.SelectInTarget}s
 * <li> {@link com.intellij.ide.IconProvider}s
 *
 * @see com.intellij.openapi.project.DumbService
 * @see com.intellij.openapi.project.DumbAwareRunnable
 * @author peter
 */
public interface DumbAware {
}
