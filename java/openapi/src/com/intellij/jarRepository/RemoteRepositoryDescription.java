// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

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
  public static final List<RemoteRepositoryDescription> DEFAULT_REPOSITORIES = ContainerUtil.immutableList(
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
