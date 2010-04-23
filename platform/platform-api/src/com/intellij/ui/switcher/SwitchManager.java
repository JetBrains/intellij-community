/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.switcher;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

public class SwitchManager implements ProjectComponent {

  private SwitchingSession mySession;

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "ViewSwitchManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static SwitchManager getInstance(Project project) {
    return project.getComponent(SwitchManager.class);
  }

  public SwitchingSession getSession() {
    return mySession;
  }

  public void initSession(SwitchingSession session) {
    disposeSession(mySession);
    mySession = session;
  }

  private void disposeSession(SwitchingSession session) {
    if (mySession != null) {
      Disposer.dispose(session);
      mySession = null;
    }
  }
}
