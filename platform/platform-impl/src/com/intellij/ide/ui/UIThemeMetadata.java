// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class UIThemeMetadata {

  private String name;
  private String pluginId;
  private boolean fixed;

  private List<UIKeyMetadata> ui;

  static UIThemeMetadata loadFromJson(InputStream stream, PluginId pluginId) throws IOException {
    UIThemeMetadata metadata = new ObjectMapper().readValue(stream, UIThemeMetadata.class);
    metadata.pluginId = pluginId.getIdString();
    return metadata;
  }

  public static class UIKeyMetadata {

    private String key;
    private String description;
    private String source;

    private boolean deprecated;

    private String since;

    public boolean isDeprecated() {
      return deprecated;
    }

    public String getKey() {
      return key;
    }

    @Nullable
    public String getDescription() {
      return description;
    }

    @Nullable
    public String getSource() {
      return source;
    }

    @Nullable
    public String getSince() {
      return since;
    }

    @SuppressWarnings("unused")
    private void setKey(String key) {
      this.key = key;
    }

    @SuppressWarnings("unused")
    private void setDescription(String description) {
      this.description = description;
    }

    @SuppressWarnings("unused")
    private void setDeprecated(boolean deprecated) {
      this.deprecated = deprecated;
    }

    @SuppressWarnings("unused")
    private void setSource(String source) {
      this.source = source;
    }

    @SuppressWarnings("unused")
    public void setSince(String since) {
      this.since = since;
    }

    @Override
    public String toString() {
      return "UIKeyMetadata{" +
             "key='" + key + '\'' +
             ", description='" + description + '\'' +
             ", source='" + source + '\'' +
             ", deprecated=" + deprecated +
             ", since='" + since + '\'' +
             '}';
    }
  }

  public String getName() {
    return name;
  }

  public String getPluginId() {
    return pluginId;
  }

  public boolean isFixed() {
    return fixed;
  }

  public List<UIKeyMetadata> getUiKeyMetadata() {
    return ui;
  }

  @SuppressWarnings("unused")
  private void setName(String name) {
    this.name = name;
  }

  @SuppressWarnings("unused")
  private void setFixed(boolean fixed) {
    this.fixed = fixed;
  }

  @SuppressWarnings("unused")
  private void setUi(List<UIKeyMetadata> ui) {
    this.ui = ui;
  }
}
