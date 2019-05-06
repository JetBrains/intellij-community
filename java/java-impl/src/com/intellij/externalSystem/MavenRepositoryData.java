// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalSystem;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;

import java.util.Objects;

public class MavenRepositoryData extends AbstractExternalEntityData {
  // Maven repository info may be required to process libraries
  public static final Key<MavenRepositoryData> KEY = Key.create(MavenRepositoryData.class,
                                                                ExternalSystemConstants.BUILTIN_LIBRARY_DATA_SERVICE_ORDER - 1);

  private final String myName;
  private final String myUrl;

  public MavenRepositoryData(ProjectSystemId owner, String name, String url) {
    super(owner);
    myName = name;
    myUrl = url;
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
    if (!(o instanceof MavenRepositoryData)) return false;
    if (!super.equals(o)) return false;

    MavenRepositoryData data = (MavenRepositoryData)o;

    if (!Objects.equals(myName, data.myName)) return false;
    if (!Objects.equals(myUrl, data.myUrl)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myName != null ? myName.hashCode() : 0);
    result = 31 * result + (myUrl != null ? myUrl.hashCode() : 0);
    return result;
  }
}
