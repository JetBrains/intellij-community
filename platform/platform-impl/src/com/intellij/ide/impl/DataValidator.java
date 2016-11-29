/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.impl;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.KeyedLazyInstanceEP;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.Map;

public abstract class DataValidator<T> {
  private static boolean ourExtensionsLoaded;

  public static final ExtensionPointName<KeyedLazyInstanceEP<DataValidator>> EP_NAME =
    ExtensionPointName.create("com.intellij.dataValidator");

  Logger LOG = Logger.getInstance("#com.intellij.ide.impl.DataValidator");

  private static final Map<String, DataValidator> ourValidators = new HashMap<>();
  private static final DataValidator<VirtualFile> VIRTUAL_FILE_VALIDATOR = new DataValidator<VirtualFile>() {
    public VirtualFile findInvalid(final String dataId, VirtualFile file, final Object dataSource) {
      return file.isValid() ? null : file;
    }
  };
  private static final DataValidator<Project> PROJECT_VALIDATOR = new DataValidator<Project>() {
    @Override
    public Project findInvalid(final String dataId, final Project project, final Object dataSource) {
      return project.isDisposed() ? project : null;
    }
  };

  @Nullable
  public abstract T findInvalid(final String dataId, T data, final Object dataSource);

  private static <T> DataValidator<T> getValidator(String dataId) {
    if (!ourExtensionsLoaded) {
      ourExtensionsLoaded = true;
      for (KeyedLazyInstanceEP<DataValidator> ep : Extensions.getExtensions(EP_NAME)) {
        ourValidators.put(ep.key, ep.getInstance());
      }
    }
    return ourValidators.get(dataId);
  }

  public static <T> T findInvalidData(String dataId, Object data, final Object dataSource) {
    if (data == null) return null;
    DataValidator<T> validator = getValidator(dataId);
    if (validator != null) return validator.findInvalid(dataId, (T)data, dataSource);
    return null;
  }

  static {
    ourValidators.put(CommonDataKeys.VIRTUAL_FILE.getName(), VIRTUAL_FILE_VALIDATOR);
    ourValidators.put(CommonDataKeys.VIRTUAL_FILE_ARRAY.getName(), new ArrayValidator<>(VIRTUAL_FILE_VALIDATOR));
    ourValidators.put(CommonDataKeys.PROJECT.getName(), PROJECT_VALIDATOR);
  }

  public static class ArrayValidator<T> extends DataValidator<T[]> {
    private final DataValidator<T> myElementValidator;

    public ArrayValidator(DataValidator<T> elementValidator) {
      myElementValidator = elementValidator;
    }

    public T[] findInvalid(final String dataId, T[] array, final Object dataSource) {
      for (T element : array) {
        if (element == null) {
          LOG.error(
            "Data isn't valid. " + dataId + "=null Provided by: " + dataSource.getClass().getName() + " (" + dataSource.toString() + ")");
        }
        T invalid = myElementValidator.findInvalid(dataId, element, dataSource);
        if (invalid != null) {
          T[] result = (T[])Array.newInstance(array.getClass().getComponentType(), 1);
          result[0] = invalid;
          return result;
        }
      }
      return null;
    }
  }

}
