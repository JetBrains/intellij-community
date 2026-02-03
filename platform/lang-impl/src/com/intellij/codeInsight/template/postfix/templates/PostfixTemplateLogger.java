// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

final class PostfixTemplateLogger extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("completion.postfix", 59);
  private static final @NonNls String CUSTOM = "custom";
  private static final @NonNls String NO_PROVIDER = "no.provider";
  private static final EventField<Language> LANGUAGE = EventFields.Language;
  private static final EventField<String> TEMPLATE = EventFields.StringValidatedByCustomRule("template", PostfixTemplateValidator.class);
  private static final EventField<String> PROVIDER = EventFields.StringValidatedByCustomRule("provider", PostfixTemplateValidator.class);
  private static final EventField<PluginInfo> PLUGIN_INFO = EventFields.PluginInfo;
  private static final VarargEventId EXPANDED = GROUP.registerVarargEvent("expanded", LANGUAGE, TEMPLATE, PROVIDER, PLUGIN_INFO);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  static void log(final @NotNull PostfixTemplate template, final @NotNull PsiElement context) {
    final ArrayList<EventPair<?>> events = new ArrayList<>(4);
    events.add(LANGUAGE.with(context.getLanguage()));
    if (template.isBuiltin()) {
      final PostfixTemplateProvider provider = template.getProvider();
      final String providerId = provider != null ? provider.getId() : NO_PROVIDER;
      events.add(TEMPLATE.with(template.getId()));
      events.add(PROVIDER.with(providerId));
    }
    else {
      events.add(TEMPLATE.with(CUSTOM));
      events.add(PROVIDER.with(CUSTOM));
    }
    events.add(PLUGIN_INFO.with(PluginInfoDetectorKt.getPluginInfo(template.getClass())));
    EXPANDED.log(context.getProject(), events);
  }

  static final class PostfixTemplateValidator extends CustomValidationRule {
    @Override
    public @NotNull String getRuleId() {
      return "completion_template";
    }

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return getRuleId().equals(ruleId) || "completion_provider_template".equals(ruleId);
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (StringUtil.equals(data, CUSTOM) || StringUtil.equals(data, NO_PROVIDER)) return ValidationResultType.ACCEPTED;

      final Language lang = getLanguage(context);
      if (lang == null) return ValidationResultType.REJECTED;

      final String provider = getEventDataField(context, PROVIDER.getName());
      final String template = getEventDataField(context, TEMPLATE.getName());
      if (provider == null || template == null || !isProviderOrTemplate(data, provider, template)) {
        return ValidationResultType.REJECTED;
      }

      final Pair<PostfixTemplate, PostfixTemplateProvider> result = findPostfixTemplate(lang, provider, template);
      if (result.getFirst() != null && result.getSecond() != null) {
        final PluginInfo templateInfo = PluginInfoDetectorKt.getPluginInfo(result.getFirst().getClass());
        final PluginInfo providerInfo = PluginInfoDetectorKt.getPluginInfo(result.getSecond().getClass());
        return templateInfo.isDevelopedByJetBrains() && providerInfo.isDevelopedByJetBrains() ?
               ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
      }
      return ValidationResultType.REJECTED;
    }

    private static boolean isProviderOrTemplate(@NotNull String data, @NotNull String provider, @NotNull String template) {
      return StringUtil.equals(data, provider) || StringUtil.equals(data, template);
    }

    private static @NotNull Pair<PostfixTemplate, PostfixTemplateProvider> findPostfixTemplate(@NotNull Language lang,
                                                                                               @NotNull String providerId,
                                                                                               @NotNull String templateId) {
      if (!StringUtil.equals(providerId, NO_PROVIDER)) {
        final PostfixTemplateProvider provider = findProviderById(providerId, lang);
        final PostfixTemplate template = provider != null ? findTemplateById(provider, templateId) : null;
        return provider != null && template != null ? Pair.create(template, provider) : Pair.empty();
      }
      else {
        for (PostfixTemplateProvider provider : LanguagePostfixTemplate.LANG_EP.allForLanguage(lang)) {
          final PostfixTemplate template = findTemplateById(provider, templateId);
          if (template != null) {
            return Pair.create(template, provider);
          }
        }
      }
      return Pair.empty();
    }

    private static @Nullable PostfixTemplateProvider findProviderById(@NotNull String id, @NotNull Language lang) {
      for (PostfixTemplateProvider provider : LanguagePostfixTemplate.LANG_EP.allForLanguage(lang)) {
        if (StringUtil.equals(provider.getId(), id)) {
          return provider;
        }
      }
      return null;
    }

    private static @Nullable PostfixTemplate findTemplateById(@NotNull PostfixTemplateProvider provider, @NotNull String id) {
      for (PostfixTemplate template : provider.getTemplates()) {
        if (StringUtil.equals(template.getId(), id)) {
          return template;
        }
      }
      return null;
    }
  }
}
