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
package com.intellij.platform.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 *         Date: 10/1/12
 */
public class GithubBasedProjectTemplate extends AbstractGithubTagDownloadedProjectGenerator {
  private String myDisplayName;
  private String myGithubUserName;
  private String myGithubRepositoryName;
  private String myHomepageUrl;
  private String myDescription;

  public GithubBasedProjectTemplate(String displayName,
                                    String githubUserName,
                                    String githubRepositoryName,
                                    String homepageUrl,
                                    String description) {
    myDisplayName = displayName;
    myGithubUserName = githubUserName;
    myGithubRepositoryName = githubRepositoryName;
    myHomepageUrl = homepageUrl;
    myDescription = description;
  }

  @NotNull
  @Override
  protected String getDisplayName() {
    return myDisplayName;
  }

  @Override
  protected String getGithubUserName() {
    return myGithubUserName;
  }

  @NotNull
  @Override
  protected String getGithubRepositoryName() {
    return myGithubRepositoryName;
  }

  @Override
  public String getHomepageUrl() {
    return myHomepageUrl;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }
}
