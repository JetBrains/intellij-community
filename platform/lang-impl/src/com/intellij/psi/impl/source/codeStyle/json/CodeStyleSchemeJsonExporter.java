// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle.json;

import com.google.gson.*;
import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.GeneralCodeStylePropertyMapper;
import com.intellij.openapi.options.SchemeExporter;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.*;

public class CodeStyleSchemeJsonExporter extends SchemeExporter<CodeStyleScheme> {

  public static final String CODE_STYLE_JSON_EXT = "codestyle.json";
  public static final String COMMON_OPTIONS = "All";

  private static final Set<String> myLanguagesToExport = ContainerUtil.newHashSet();

  @Override
  public void exportScheme(@NotNull CodeStyleScheme scheme, @NotNull OutputStream outputStream) {
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    builder.registerTypeHierarchyAdapter(AbstractCodeStylePropertyMapper.class, new JsonSerializer<AbstractCodeStylePropertyMapper>() {

      @Override
      public JsonElement serialize(AbstractCodeStylePropertyMapper src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject o = new JsonObject();
        for (String name : src.enumProperties()) {
          String value = src.getProperty(name);
          o.addProperty(name, value);
        }
        return o;
      }
    });
    Gson gson = builder.create();
    String json = gson.toJson(getOptions(scheme));
    try (PrintWriter writer = new PrintWriter(outputStream)) {
      writer.write(json);
    }
  }

  @TestOnly
  public CodeStyleSchemeJsonExporter filter(@NotNull String... languages) {
    myLanguagesToExport.addAll(Arrays.asList(languages));
    return this;
  }

  private static List<LanguageOptions> getOptions(@NotNull CodeStyleScheme scheme) {
    List<LanguageOptions> options = ContainerUtil.newArrayList();
    final CodeStyleSettings codeStyleSettings = scheme.getCodeStyleSettings();
    for (LanguageCodeStyleSettingsProvider provider : LanguageCodeStyleSettingsProvider.EP_NAME.getExtensionList()) {
      final String languageName = provider.getLanguage().getDisplayName();
      if (myLanguagesToExport.isEmpty() || myLanguagesToExport.contains(languageName)) {
        options.add(new LanguageOptions(ObjectUtils.notNull(provider.getLanguageName(), languageName),
                                        provider.getPropertyMapper(codeStyleSettings)));
      }
    }
    Collections.sort(options, Comparator.comparing(o -> o.language));
    options.add(0, new LanguageOptions(COMMON_OPTIONS, new GeneralCodeStylePropertyMapper(codeStyleSettings)));
    return options;
  }

  @Override
  public String getExtension() {
    return CODE_STYLE_JSON_EXT;
  }

  private static class LanguageOptions {
    final @NotNull String language;
    final @NotNull AbstractCodeStylePropertyMapper options;

    private LanguageOptions(@NotNull String language, @NotNull AbstractCodeStylePropertyMapper mapper) {
      this.language = language;
      this.options = mapper;
    }
  }
}
