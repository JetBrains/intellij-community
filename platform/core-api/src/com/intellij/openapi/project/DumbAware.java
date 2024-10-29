// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

/**
 * A marker interface for the things that are allowed to run in dumb mode (when indices are in background update).
 * <p/>
 * Implementors must take care of handling and/or not calling non-{@code DumbAware} parts of the system.
 * <p/>
 * Some known implementors are:
 * <li> {@link com.intellij.openapi.actionSystem.AnAction} (see {@link com.intellij.openapi.project.DumbAwareAction})
 * <li> {@link com.intellij.openapi.fileEditor.FileEditorProvider}
 * <li> Console {@link com.intellij.execution.filters.Filter}
 * <li> {@link com.intellij.ide.SelectInTarget}
 * <li> {@link com.intellij.ide.IconProvider}
 * <li> {@link com.intellij.codeInsight.completion.CompletionContributor}
 * <li> {@link com.intellij.lang.annotation.Annotator}
 * <li> {@link com.intellij.codeHighlighting.TextEditorHighlightingPass}
 * <li> {@link com.intellij.openapi.wm.ToolWindowFactory}
 * <li> {@link com.intellij.lang.injection.MultiHostInjector}
 *
 * @see DumbService
 * @see DumbAwareRunnable
 * @see PossiblyDumbAware
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/indexing-and-psi-stubs.html#DumbAwareAPI">DumbAware API (IntelliJ Platform Docs)</a>
 */
public interface DumbAware {
}
