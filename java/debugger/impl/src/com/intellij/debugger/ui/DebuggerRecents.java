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
package com.intellij.debugger.ui;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;

import java.util.LinkedList;
import java.util.Map;

/**
 * @author Lex
 */
public class DebuggerRecents {  
  private final Map<Object, LinkedList<TextWithImports>> myRecentExpressions = new HashMap<Object, LinkedList<TextWithImports>>();

  public static DebuggerRecents getInstance(Project project) {
    return ServiceManager.getService(project, DebuggerRecents.class);
  }

  public LinkedList<TextWithImports> getRecents(Object id) {
    LinkedList<TextWithImports> result = myRecentExpressions.get(id);
    if(result == null){
      result = new LinkedList<TextWithImports>();
      myRecentExpressions.put(id, result);
    }
    return result;
  }

  public void addRecent(Object id, TextWithImports recent) {
    LinkedList<TextWithImports> recents = getRecents(id);
    if(recents.size() >= DebuggerExpressionComboBox.MAX_ROWS) {
      recents.removeLast();
    }
    recents.remove(recent);
    recents.addFirst(recent);
  }
}
