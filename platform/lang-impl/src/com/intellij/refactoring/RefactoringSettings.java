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
package com.intellij.refactoring;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name = "BaseRefactoringSettings",
  storages = {
    @Storage("baseRefactoring.xml"),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class RefactoringSettings implements PersistentStateComponent<RefactoringSettings> {
  public static RefactoringSettings getInstance() {
    return ServiceManager.getService(RefactoringSettings.class);
  }

  public boolean SAFE_DELETE_WHEN_DELETE = true;
  public boolean SAFE_DELETE_SEARCH_IN_COMMENTS = true;
  public boolean SAFE_DELETE_SEARCH_IN_NON_JAVA = true;

  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_FILE = true;
  public boolean RENAME_SEARCH_FOR_TEXT_FOR_FILE = true;

  public boolean RENAME_SEARCH_FOR_REFERENCES_FOR_FILE = true;
  public boolean RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY = true;

  public boolean MOVE_SEARCH_FOR_REFERENCES_FOR_FILE = true;

  @Override
  public RefactoringSettings getState() {
    return this;
  }

  @Override
  public void loadState(final RefactoringSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
