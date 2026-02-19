// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.InjectedDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

final class PlatformDataValidators extends DataValidators {
  @Override
  protected void collectValidators(@NotNull ValidatorRegistry registry) {
    Validator<Object> uiOnlyValidator = uiOnlyDataKeyValidator();
    registry.register(PlatformCoreDataKeys.SELECTED_ITEM, uiOnlyValidator);
    registry.register(PlatformCoreDataKeys.SELECTED_ITEMS, uiOnlyValidator);

    Validator<VirtualFile> fileValidator = (data, dataId, source) -> data.isValid();
    registry.register(CommonDataKeys.VIRTUAL_FILE, fileValidator);
    registry.register(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayValidator(fileValidator));

    Validator<Project> projectValidator = (data, dataId, source) -> !data.isDisposed();
    registry.register(CommonDataKeys.PROJECT, projectValidator);

    Validator<Editor> editorValidator = (data, dataId, source) -> !data.isDisposed();
    registry.register(CommonDataKeys.EDITOR, editorValidator);
    registry.register(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE, editorValidator);
    registry.register(CommonDataKeys.HOST_EDITOR, editorValidator);
    registry.register(InjectedDataKeys.EDITOR, editorValidator);

    Validator<Object> objectValidator = (data, dataId, source) -> true;
    registry.register(CommonDataKeys.NAVIGATABLE_ARRAY, arrayValidator(objectValidator));
    registry.register(PlatformCoreDataKeys.SELECTED_ITEMS, arrayValidator(objectValidator));
    registry.register(PlatformDataKeys.LAST_ACTIVE_TOOL_WINDOWS, arrayValidator(objectValidator));

    Validator<EditorWindow> editorWindowValidator = (data, dataId, source) -> data.isValid();
    registry.register(EditorWindow.DATA_KEY, editorWindowValidator);
  }
}
