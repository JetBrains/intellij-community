// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle.json;

import com.google.gson.*;
import com.google.gson.annotations.Expose;
import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil;
import com.intellij.application.options.codeStyle.properties.GeneralCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.LanguageCodeStylePropertyMapper;
import com.intellij.lang.Language;
import com.intellij.openapi.options.SchemeExporter;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CodeStyleSchemeJsonExporter extends SchemeExporter<CodeStyleScheme> {

  public static final String CODE_STYLE_JSON_EXT = "codestyle.json";

  @Override
  public void exportScheme(@NotNull CodeStyleScheme scheme, @NotNull OutputStream outputStream) {
    exportScheme(scheme, outputStream, null);
  }

  public void exportScheme(@NotNull CodeStyleScheme scheme, @NotNull OutputStream outputStream, @Nullable List<String> languageNames) {
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
    String json = gson.toJson(getOptionDescriptors(scheme.getCodeStyleSettings(), languageNames));
    try (PrintWriter writer = new PrintWriter(outputStream)) {
      writer.write(json);
    }
  }

  @Override
  public String getExtension() {
    return CODE_STYLE_JSON_EXT;
  }


  private static List<LanguagePropertyMapperDescriptor> getOptionDescriptors(@NotNull CodeStyleSettings settings,
                                                                             @Nullable List<String> languageDomainIds) {
    List<LanguagePropertyMapperDescriptor> descriptors = ContainerUtil.newArrayList();
    CodeStylePropertiesUtil.collectMappers(settings, mapper -> {
      if (languageDomainIds == null || languageDomainIds.contains(mapper.getLanguageDomainId())) {
        descriptors.add(new LanguagePropertyMapperDescriptor(mapper.getLanguageDomainId(), mapper, getPriority(mapper)));
      }
    });
    Collections.sort(descriptors);
    return descriptors;
  }

  private static DisplayPriority getPriority(@NotNull AbstractCodeStylePropertyMapper mapper) {
    if (mapper instanceof GeneralCodeStylePropertyMapper) {
      return DisplayPriority.GENERAL_SETTINGS;
    }
    else if (mapper instanceof LanguageCodeStylePropertyMapper) {
      Language language = ((LanguageCodeStylePropertyMapper)mapper).getLanguage();
      LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(language);
      if (provider != null) {
        return provider.getDisplayPriority();
      }
    }
    return DisplayPriority.OTHER_SETTINGS;
  }

  private static class LanguagePropertyMapperDescriptor implements Comparable<LanguagePropertyMapperDescriptor> {
    final @NotNull String language;
    final @NotNull AbstractCodeStylePropertyMapper options;
    private final transient @NotNull DisplayPriority priority;

    private LanguagePropertyMapperDescriptor(@NotNull String language,
                                             @NotNull AbstractCodeStylePropertyMapper mapper,
                                             @NotNull DisplayPriority priority) {
      this.language = language;
      this.options = mapper;
      this.priority = priority;
    }

    @Override
    public int compareTo(@NotNull LanguagePropertyMapperDescriptor d) {
      int result = this.priority.compareTo(d.priority);
      if (result == 0) {
        result = this.language.compareTo(d.language);
      }
      return result;
    }
  }
}
