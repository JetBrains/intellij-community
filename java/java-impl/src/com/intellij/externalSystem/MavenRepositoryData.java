// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalSystem;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.serialization.PropertyMapping;

import java.util.Objects;

public final class MavenRepositoryData extends AbstractExternalEntityData {
  // Maven repository info may be required to process libraries
  public static final Key<MavenRepositoryData> KEY = Key.create(MavenRepositoryData.class,
                                                                ExternalSystemConstants.BUILTIN_LIBRARY_DATA_SERVICE_ORDER - 1);

  private final String name;
  private final String url;

  @PropertyMapping({"owner", "name", "url"})
  public MavenRepositoryData(ProjectSystemId owner, String name, String url) {
    super(owner);

    this.name = name;
    this.url = url;
  }

  public String getName() {
    return name;
  }

  public String getUrl() {
    return url;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MavenRepositoryData)) return false;
    if (!super.equals(o)) return false;

    MavenRepositoryData data = (MavenRepositoryData)o;

    if (!Objects.equals(name, data.name)) return false;
    if (!Objects.equals(url, data.url)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (url != null ? url.hashCode() : 0);
    return result;
  }
}
