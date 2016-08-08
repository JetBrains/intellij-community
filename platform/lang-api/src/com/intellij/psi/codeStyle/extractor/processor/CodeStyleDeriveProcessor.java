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
import com.intellij.psi.codeStyle.extractor.Utils;
import com.intellij.psi.codeStyle.extractor.differ.LangCodeStyleExtractor;
import com.intellij.psi.codeStyle.extractor.values.ClassSerializer;
import com.intellij.psi.codeStyle.extractor.values.Value;
import com.intellij.psi.codeStyle.extractor.values.ValuesExtractionResult;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @author Roman.Shein
 * @since 04.08.2015.
 */
public abstract class CodeStyleDeriveProcessor {

  protected final LangCodeStyleExtractor myLangExtractor;

  protected CodeStyleDeriveProcessor(LangCodeStyleExtractor langExtractor) {
    myLangExtractor = langExtractor;
  }

  public abstract ValuesExtractionResult runWithProgress(Project project, CodeStyleSettings settings, PsiFile file,
                                    ProgressIndicator indicator);

  public Map<Value, Object> backupValues(CodeStyleSettings settings, Language language) {
    List<Value> baseValues = getFormattingValues(settings, language);
    Map<Value, Object> res = ContainerUtil.newHashMap();
    for (Value baseValue: baseValues) {
      res.put(baseValue, baseValue.value);
    }
    return res;
  }

  @NotNull
  private Collection<Value.VAR_KIND> getVarKinds() {
    List<Value.VAR_KIND> varKinds = new LinkedList<>();
    varKinds.addAll(myLangExtractor.getCustomVarKinds());
    varKinds.addAll(Arrays.asList(Value.VAR_KIND.defaultKinds));
    return varKinds;
  }

  @NotNull
  private Value.VAR_KIND getVarKind(@NotNull String name,  @NotNull Object value) {
    for (Value.VAR_KIND varKind : getVarKinds()) {
      if (varKind.accepts(name, value)) {
        return varKind;
      }
    }
    return Value.VAR_KIND.NOTHING;
  }

  @NotNull
  private List<Value> readAll(@NotNull String instanceName, @NotNull Object instance) {
    Class<?> cls = instance.getClass();
    List<Value> ret = new ArrayList<>();
    ClassSerializer serializer = new ClassSerializer(instanceName, instance);
    for (Field field : cls.getDeclaredFields()) {
      field = ClassSerializer.getPreparedField(field);
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
  private Value buildFValue(@NotNull Field field,
                             @NotNull Object instance,
                             @NotNull ClassSerializer serializer) throws IllegalAccessException {
    String name = field.getName();
    Object value = field.get(instance);
    Value.VAR_KIND varKind = getVarKind(name, value);
    return new Value(name, value, serializer, varKind);
  }

  @NotNull
  protected List<Value> getFormattingValues(CodeStyleSettings settings, Language language) {

    final CommonCodeStyleSettings commonSettings = settings.getCommonSettings(language);
    CommonCodeStyleSettings.IndentOptions indentOptions = commonSettings.getIndentOptions();
    if (indentOptions == null) {
      Utils.logError("IndentOptions from common settings are null; using indent options from settings.");
      indentOptions = settings.getIndentOptions();
    }
    final Object languageSettings = Utils.getLanguageSettings(settings, language);

    List<Value> values = readAll("commonSettings", commonSettings);
    if (languageSettings != null) {
      values.addAll(readAll("ocSettings", languageSettings));
    }
    final LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(language);
    if (provider != null) {
      final Set<String> supportedFields = provider.getSupportedFields();
      List<Value> cvalues = new ArrayList<>(values.size());
      for (Value value : values) {
        if (supportedFields.contains(value.name)) {
          cvalues.add(value);
        }
      }
      values = cvalues;
    }

    if (indentOptions != null) {
      List<Value> valuesOrder = readAll("indentOptions", indentOptions);
      valuesOrder.addAll(values);
      return valuesOrder;
    } else {
      Utils.logError("Not indent options detected.");
      return values;
    }
  }

}
