/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.profile.ApplicationProfileManager;
import com.intellij.profile.Profile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public interface InspectionProfileManager extends SeverityProvider, ApplicationProfileManager {
  static InspectionProfileManager getInstance() {
    return ServiceManager.getService(InspectionProfileManager.class);
  }

  Profile loadProfile(@NotNull String path) throws IOException, JDOMException;
}
