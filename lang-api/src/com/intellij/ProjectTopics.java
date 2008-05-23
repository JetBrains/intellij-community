/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij;

import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.util.LogicalRootsManager;
import com.intellij.util.messages.Topic;
import com.intellij.psi.util.PsiModificationTracker;

public class ProjectTopics {
  public static final Topic<ModuleRootListener> PROJECT_ROOTS = new Topic<ModuleRootListener>("project root changes", ModuleRootListener.class);
  public static final Topic<ModuleListener> MODULES = new Topic<ModuleListener>("modules added or removed from project", ModuleListener.class);
  public static final Topic<LogicalRootsManager.LogicalRootListener> LOGICAL_ROOTS = new Topic<LogicalRootsManager.LogicalRootListener>("logical root changes", LogicalRootsManager.LogicalRootListener.class);
  public static final Topic<PsiModificationTracker.Listener> MODIFICATION_TRACKER = new Topic<PsiModificationTracker.Listener>("modification tracker", PsiModificationTracker.Listener.class);

  private ProjectTopics() {
  }
}