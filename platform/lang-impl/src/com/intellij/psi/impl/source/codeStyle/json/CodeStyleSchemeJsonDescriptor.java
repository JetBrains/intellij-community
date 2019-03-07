// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle.json;

import com.intellij.application.options.codeStyle.properties.AbstractCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil;
import com.intellij.application.options.codeStyle.properties.GeneralCodeStylePropertyMapper;
import com.intellij.application.options.codeStyle.properties.LanguageCodeStylePropertyMapper;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class CodeStyleSchemeJsonDescriptor {

  public final static String VERSION = "1.0";

  private transient final CodeStyleScheme myScheme;
  private transient final List<String> myLangDomainIds;

  public final String schemeName;
  public final String version = VERSION;
  public List<LanguagePropertyMapperDescriptor> codeStyle;

  CodeStyleSchemeJsonDescriptor(CodeStyleScheme scheme, List<String> ids) {
    myScheme = scheme;
    schemeName = scheme.getName();
    myLangDomainIds = ids;
    this.codeStyle = getOptionDescriptors();
  }

  private List<LanguagePropertyMapperDescriptor> getOptionDescriptors() {
    List<LanguagePropertyMapperDescriptor> descriptors = ContainerUtil.newArrayList();
    CodeStylePropertiesUtil.collectMappers(myScheme.getCodeStyleSettings(), mapper -> {
      if (myLangDomainIds == null || myLangDomainIds.contains(mapper.getLanguageDomainId())) {
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
