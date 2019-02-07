// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import java.util.Collection;

public class JsonSchemaCatalogEntry {
  private final Collection<String> fileMasks;
  private final String url;
  private final String name;
  private final String description;

  public JsonSchemaCatalogEntry(Collection<String> fileMasks, String url, String name, String description) {
    this.fileMasks = fileMasks;
    this.url = url;
    this.name = name;
    this.description = description;
  }

  public Collection<String> getFileMasks() {
    return fileMasks;
  }

  public String getUrl() {
    return url;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }
}
