// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.startup.StartupActivity;

/**
 * A marker interface for the things that are allowed to run in dumb mode (when indices are in background update).
 * Implementors must take care of handling and/or not calling non-DumbAware parts of system.
 * <p/>
 * Some known implementors are:
 * <li> {@link com.intellij.openapi.actionSystem.AnAction}s (see {@link com.intellij.openapi.project.DumbAwareAction})
 * <li> {@link com.intellij.openapi.fileEditor.FileEditorProvider}s
 * <li> post-startup activities ({@link StartupActivity})
 * <li> Stacktrace {@link com.intellij.execution.filters.Filter}s
 * <li> {@link com.intellij.ide.SelectInTarget}s
 * <li> {@link com.intellij.ide.IconProvider}s
 * <li> {@link com.intellij.codeInsight.completion.CompletionContributor}s
 * <li> {@link com.intellij.codeHighlighting.TextEditorHighlightingPass}es
 * <li> {@link com.intellij.openapi.wm.ToolWindowFactory}s
 * <li> {@link com.intellij.lang.injection.MultiHostInjector}s
 *
 * @see DumbService
 * @see DumbAwareRunnable
 * @see PossiblyDumbAware
 */
public interface DumbAware {
}
