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
package com.intellij.openapi.vcs;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
@State(
  name = "IssueNavigationConfiguration",
  storages = {
    @Storage(
      id ="other",
      file = "$PROJECT_FILE$"
    )
    ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/vcs.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class IssueNavigationConfiguration implements PersistentStateComponent<IssueNavigationConfiguration> {
  public static IssueNavigationConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, IssueNavigationConfiguration.class);
  }

  private List<IssueNavigationLink> myLinks = new ArrayList<IssueNavigationLink>();

  public List<IssueNavigationLink> getLinks() {
    return myLinks;
  }

  public void setLinks(final List<IssueNavigationLink> links) {
    myLinks = links;
  }

  public IssueNavigationConfiguration getState() {
    return this;
  }

  public void loadState(IssueNavigationConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}