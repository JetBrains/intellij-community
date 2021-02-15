// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
public final class RemoteRepositoryDescription {
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
  public static final List<RemoteRepositoryDescription> DEFAULT_REPOSITORIES = List.of(
    MAVEN_CENTRAL, JBOSS_COMMUNITY
  );

  private final String myId;
  private final String myName;
  private final @NlsSafe String myUrl;
  private final boolean myAllowSnapshots;

  public RemoteRepositoryDescription(@NonNls @NotNull String id, @NotNull String name, @NotNull String url) {
    this(id, name, url, true);
  }

  public RemoteRepositoryDescription(@NotNull String id,
                                     @NotNull String name,
                                     @NotNull String url,
                                     boolean allowSnapshots) {
    myId = id;
    myName = name;
    myUrl = url;
    myAllowSnapshots = allowSnapshots;
  }

  public String getId() {
    return myId;
  }

  public String getName() {
    return myName;
  }

  public @NlsSafe String getUrl() {
    return myUrl;
  }

  public boolean isAllowSnapshots() {
    return myAllowSnapshots;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RemoteRepositoryDescription that = (RemoteRepositoryDescription)o;
    return myAllowSnapshots == that.myAllowSnapshots &&
           myId.equals(that.myId) &&
           myName.equals(that.myName) &&
           myUrl.equals(that.myUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myId, myName, myUrl, myAllowSnapshots);
  }

  @Override
  public String toString() {
    return myId + ":" + myName + ":" + myUrl + " (snapshots=" + myAllowSnapshots + ")";
  }
}
