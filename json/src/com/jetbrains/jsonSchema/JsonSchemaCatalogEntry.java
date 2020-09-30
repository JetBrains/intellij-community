// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import org.jetbrains.annotations.Nls;

import java.util.Collection;

public class JsonSchemaCatalogEntry {
  private final Collection<String> fileMasks;
  private final String url;
  private final @Nls String name;
  private final @Nls String description;

  public JsonSchemaCatalogEntry(Collection<String> fileMasks, String url, @Nls String name, @Nls String description) {
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

  public @Nls String getName() {
    return name;
  }

  public @Nls String getDescription() {
    return description;
  }
}
