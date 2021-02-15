// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.KeyedLazyInstanceEP;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public abstract class DataValidator<T> {
  private static boolean ourExtensionsLoaded;

  public static final ExtensionPointName<KeyedLazyInstanceEP<DataValidator>> EP_NAME =
    ExtensionPointName.create("com.intellij.dataValidator");

  private static final Logger LOG = Logger.getInstance(DataValidator.class);

  private static final Map<String, DataValidator<?>> ourValidators = new HashMap<>();
  private static final DataValidator<VirtualFile> VIRTUAL_FILE_VALIDATOR = new DataValidator<>() {
    @Override
    public VirtualFile findInvalid(@NotNull final String dataId, @NotNull VirtualFile file, @NotNull final Object dataSource) {
      return file.isValid() ? null : file;
    }
  };
  private static final DataValidator<Project> PROJECT_VALIDATOR = new DataValidator<>() {
    @Override
    public Project findInvalid(@NotNull final String dataId, @NotNull final Project project, @NotNull final Object dataSource) {
      return project.isDisposed() ? project : null;
    }
  };
  private static final DataValidator<Editor> EDITOR_VALIDATOR = new DataValidator<>() {
    @Override
    public Editor findInvalid(@NotNull final String dataId, @NotNull final Editor editor, @NotNull final Object dataSource) {
      return editor.isDisposed() ? editor : null;
    }
  };

  @Nullable
  public abstract T findInvalid(@NotNull String dataId, @NotNull T data, @NotNull Object dataSource);

  private static <T> DataValidator<T> getValidator(@NotNull String dataId) {
    if (!ourExtensionsLoaded) {
      ourExtensionsLoaded = true;
      for (KeyedLazyInstanceEP<DataValidator> ep : EP_NAME.getExtensionList()) {
        ourValidators.put(ep.key, ep.getInstance());
      }
    }
    return (DataValidator<T>)ourValidators.get(dataId);
  }

  static <T> T findInvalidData(@NotNull String dataId, @NotNull Object data, @NotNull Object dataSource) {
    DataValidator<T> validator = getValidator(dataId);
    if (validator != null) {
      try {
        return validator.findInvalid(dataId, (T)data, dataSource);
      }
      catch (ClassCastException e) {
        throw new AssertionError("Object of incorrect type returned for key '" + dataId + "' by " + dataSource, e);
      }
    }
    return null;
  }

  static {
    ourValidators.put(CommonDataKeys.VIRTUAL_FILE.getName(), VIRTUAL_FILE_VALIDATOR);
    ourValidators.put(CommonDataKeys.VIRTUAL_FILE_ARRAY.getName(), new ArrayValidator<>(VIRTUAL_FILE_VALIDATOR));
    ourValidators.put(CommonDataKeys.PROJECT.getName(), PROJECT_VALIDATOR);
    ourValidators.put(CommonDataKeys.EDITOR.getName(), EDITOR_VALIDATOR);
    ourValidators.put(AnActionEvent.injectedId(CommonDataKeys.EDITOR.getName()), EDITOR_VALIDATOR);
    ourValidators.put(CommonDataKeys.HOST_EDITOR.getName(), EDITOR_VALIDATOR);
  }

  static class ArrayValidator<T> extends DataValidator<T[]> {
    private final DataValidator<T> myElementValidator;

    ArrayValidator(@NotNull DataValidator<T> elementValidator) {
      myElementValidator = elementValidator;
    }

    @Override
    public T[] findInvalid(@NotNull final String dataId, T @NotNull [] array, @NotNull final Object dataSource) {
      for (T element : array) {
        if (element == null) {
          LOG.error("Data isn't valid. " + dataId + "=null Provided by: " + dataSource.getClass().getName() + " (" + dataSource.toString() + ")");
          return null;
        }
        T invalid = myElementValidator.findInvalid(dataId, element, dataSource);
        if (invalid != null) {
          Class<T> type = ArrayUtil.getComponentType(array);
          return ArrayUtil.toObjectArray(type, invalid);
        }
      }
      return null;
    }
  }

}
