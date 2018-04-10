/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.jarRepository;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class RemoteRepositoryDescription {
  public static final RemoteRepositoryDescription MAVEN_CENTRAL = new RemoteRepositoryDescription(
    "central",
    "Maven Central repository",
    "https://repo1.maven.org/maven2"
  );
  public static final RemoteRepositoryDescription JBOSS_COMMUNITY = new RemoteRepositoryDescription(
    "jboss.community",
    "JBoss Community repository",
    "https://repository.jboss.org/nexus/content/repositories/public/"
  );
  public static final List<RemoteRepositoryDescription> DEFAULT_REPOSITORIES = Arrays.asList(
    MAVEN_CENTRAL, JBOSS_COMMUNITY
  );

  private final String myId;
  private final String myName;
  private final String myUrl;

  public RemoteRepositoryDescription(@NotNull String id, @NotNull String name, @NotNull String url) {
    myId = id;
    myName = name;
    myUrl = url;
  }

  public String getId() {
    return myId;
  }

  public String getName() {
    return myName;
  }

  public String getUrl() {
    return myUrl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RemoteRepositoryDescription that = (RemoteRepositoryDescription)o;

    if (!myId.equals(that.myId)) return false;
    if (!myName.equals(that.myName)) return false;
    if (!myUrl.equals(that.myUrl)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myId.hashCode();
    result = 31 * result + myName.hashCode();
    result = 31 * result + myUrl.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return myId + ":" + myName + ":" + myUrl;
  }
}
