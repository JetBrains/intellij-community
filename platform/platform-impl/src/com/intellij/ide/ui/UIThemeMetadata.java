// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class UIThemeMetadata {
  private static final NotNullLazyValue<ObjectReader> themeReader = NotNullLazyValue.lazy(() -> {
    return new ObjectMapper().readerFor(UIThemeMetadata.class);
  });

  private String name;
  private String pluginId;
  private boolean fixed;

  private List<UIKeyMetadata> ui;

  static UIThemeMetadata loadFromJson(InputStream stream, PluginId pluginId) throws IOException {
    UIThemeMetadata metadata = themeReader.getValue().readValue(stream);
    metadata.pluginId = pluginId.getIdString();
    return metadata;
  }

  public static final class UIKeyMetadata {
    private String key;
    private String description;
    private String source;

    private boolean deprecated;

    private String since;

    public boolean isDeprecated() {
      return deprecated;
    }

    public @NlsSafe String getKey() {
      return key;
    }

    @Nullable
    public @NlsSafe String getDescription() {
      return description;
    }

    @Nullable
    public @NlsSafe String getSource() {
      return source;
    }

    @Nullable
    public @NlsSafe String getSince() {
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

  public @NlsSafe String getName() {
    return name;
  }

  public @NlsSafe String getPluginId() {
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
