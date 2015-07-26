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

package com.intellij.ide.util.gotoByName;

import com.intellij.ide.actions.ChooseByNameFactory;
import com.intellij.ide.actions.ChooseByNameItemProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;

/**
 * User: anna
 * Date: Jan 26, 2005
 */
public class ChooseByNameFactoryImpl extends ChooseByNameFactory {
  private final Project myProject;

  public ChooseByNameFactoryImpl(final Project project) {
    myProject = project;
  }

  @Override
  public ChooseByNamePopup createChooseByName(ChooseByNameModel model,
                                              ChooseByNameItemProvider itemProvider,
                                              boolean mayRequestOpenInCurrentWindow,
                                              Pair<String, Integer> start) {
    return ChooseByNamePopup.createPopup(myProject, model, itemProvider, start.first, mayRequestOpenInCurrentWindow, start.second);
  }
}
