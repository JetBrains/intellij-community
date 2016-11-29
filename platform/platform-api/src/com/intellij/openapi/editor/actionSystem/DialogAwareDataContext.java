/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.actionSystem.CommonDataKeys.*;
import static com.intellij.openapi.actionSystem.PlatformDataKeys.PROJECT_FILE_DIRECTORY;

/**
* @author Konstantin Bulenkov
*/
final class DialogAwareDataContext implements DataContext {
  private static final DataKey[] keys = {PROJECT, PROJECT_FILE_DIRECTORY, EDITOR, VIRTUAL_FILE, PSI_FILE};
  private final Map<String, Object> values = new HashMap<>();

  DialogAwareDataContext(DataContext context) {
    for (DataKey key : keys) {
      values.put(key.getName(), key.getData(context));
    }
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (values.keySet().contains(dataId)) {
      return values.get(dataId);
    }
    final Editor editor = (Editor)values.get(EDITOR.getName());
    if (editor != null) {
      return DataManager.getInstance().getDataContext(editor.getContentComponent()).getData(dataId);
    }
    return null;
  }
}
