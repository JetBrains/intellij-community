/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.tools;

import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores main keys from DataContext.
 *
 * Normally nobody needs this. This handles specific case when after action is invoked a dialog can appear, but
 * we need the DataContext from the action.
 *
 * @author Konstantin Bulenkov
 */
class HackyDataContext implements DataContext {
  private static final DataKey[] keys = {
    PlatformDataKeys.PROJECT,
    PlatformDataKeys.PROJECT_FILE_DIRECTORY,
    PlatformDataKeys.EDITOR,
    PlatformDataKeys.VIRTUAL_FILE,
    LangDataKeys.MODULE,
    LangDataKeys.PSI_FILE
  };


  private final Map<String, Object> values = new HashMap<String, Object>();
  private final AnActionEvent myActionEvent;

  public HackyDataContext(DataContext context, AnActionEvent e) {
    myActionEvent = e;
    for (DataKey key : keys) {
      values.put(key.getName(), key.getData(context));
    }
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (values.keySet().contains(dataId)) {
      return values.get(dataId);
    }
    //noinspection UseOfSystemOutOrSystemErr
    System.out.println("Please add " + dataId + " key in " + getClass().getName());
    return null;
  }

  AnActionEvent getActionEvent() {
    return myActionEvent;
  }
}
