/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle.extractor.processor;

import com.intellij.lang.Language;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.extractor.FUtils;
import com.intellij.psi.codeStyle.extractor.differ.FLangCodeStyleExtractor;
import com.intellij.psi.codeStyle.extractor.values.FClassSerializer;
import com.intellij.psi.codeStyle.extractor.values.FValue;
import com.intellij.psi.codeStyle.extractor.values.FValuesExtractionResult;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @author Roman.Shein
 * @since 04.08.2015.
 */
public abstract class FCodeStyleDeriveProcessor {

  protected final FLangCodeStyleExtractor myLangExtractor;

  protected FCodeStyleDeriveProcessor(FLangCodeStyleExtractor langExtractor) {
    myLangExtractor = langExtractor;
  }

  public abstract FValuesExtractionResult runWithProgress(Project project, CodeStyleSettings settings, PsiFile file,
                                    ProgressIndicator indicator);

  public Map<FValue, Object> backupValues(CodeStyleSettings settings, Language language) {
    List<FValue> baseValues = getFormattingValues(settings, language);
    Map<FValue, Object> res = ContainerUtil.newHashMap();
    for (FValue baseValue: baseValues) {
      res.put(baseValue, baseValue.value);
    }
    return res;
  }

  @NotNull
  private Collection<FValue.VAR_KIND> getVarKinds() {
    List<FValue.VAR_KIND> varKinds = new LinkedList<FValue.VAR_KIND>();
    varKinds.addAll(myLangExtractor.getCustomVarKinds());
    varKinds.addAll(Arrays.asList(FValue.VAR_KIND.defaultKinds));
    return varKinds;
  }

  @NotNull
  private FValue.VAR_KIND getVarKind(@NotNull String name,  @NotNull Object value) {
    for (FValue.VAR_KIND varKind : getVarKinds()) {
      if (varKind.accepts(name, value)) {
        return varKind;
      }
    }
    return FValue.VAR_KIND.NOTHING;
  }

  @NotNull
  private List<FValue> readAll(@NotNull String instanceName, @NotNull Object instance) {
    Class<?> cls = instance.getClass();
    List<FValue> ret = new ArrayList<FValue>();
    FClassSerializer serializer = new FClassSerializer(instanceName, instance);
    for (Field field : cls.getDeclaredFields()) {
      field = FClassSerializer.getPreparedField(field);
      if (field == null || field.getName().endsWith("_FORCE")) continue;
      try {
        ret.add(buildFValue(field, instance, serializer));
      }
      catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    }
    return ret;
  }

  @NotNull
  private FValue buildFValue(@NotNull Field field,
                             @NotNull Object instance,
                             @NotNull FClassSerializer serializer) throws IllegalAccessException {
    String name = field.getName();
    Object value = field.get(instance);
    FValue.VAR_KIND varKind = getVarKind(name, value);
    return new FValue(name, value, serializer, varKind);
  }

  @NotNull
  protected List<FValue> getFormattingValues(CodeStyleSettings settings, Language language) {

    final CommonCodeStyleSettings commonSettings = settings.getCommonSettings(language);
    CommonCodeStyleSettings.IndentOptions indentOptions = commonSettings.getIndentOptions();
    if (indentOptions == null) {
      FUtils.logError("IndentOptions from common settings are null; using indent options from settings.");
      indentOptions = settings.getIndentOptions();
    }
    final Object languageSettings = FUtils.getLanguageSettings(settings, language);

    List<FValue> values = readAll("commonSettings", commonSettings);
    if (languageSettings != null) {
      values.addAll(readAll("ocSettings", languageSettings));
    }
    final LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(language);
    if (provider != null) {
      final Set<String> supportedFields = provider.getSupportedFields();
      List<FValue> cvalues = new ArrayList<FValue>(values.size());
      for (FValue value : values) {
        if (supportedFields.contains(value.name)) {
          cvalues.add(value);
        }
      }
      values = cvalues;
    }

    if (indentOptions != null) {
      List<FValue> valuesOrder = readAll("indentOptions", indentOptions);
      valuesOrder.addAll(values);
      return valuesOrder;
    } else {
      FUtils.logError("Not indent options detected.");
      return values;
    }
  }

}
