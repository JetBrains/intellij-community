// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.vmOptions.*;
import com.intellij.ide.actions.EditCustomVmOptionsAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class VmOptionsCompletionContributor extends CompletionContributor implements DumbAware {
  private static final Pattern OPTION_SEPARATOR = Pattern.compile("\\s+");
  private static final Pattern OPTION_MATCHER = Pattern.compile("^-XX:[+\\-]?(\\w+)(=.+)?$");
  private static final char OPTION_VALUE_SEPRATOR = '=';

  private static final VMOption[] STANDARD_OPTIONS = {
    opt("ea", "enable assertions with specified granularity"),
    opt("enableassertions", "enable assertions with specified granularity"),
    opt("da", "disable assertions with specified granularity"),
    opt("disableassertions", "disable assertions with specified granularity"),
    opt("esa", "enable system assertions"),
    opt("dsa", "disable system assertions"),
    opt("agentpath:", "load native agent library by full pathname"),
    opt("agentlib:", "load native agent library <libname>, e.g. -agentlib:jdwp"),
    opt("javaagent:", "load Java programming language agent"),
    opt("D", "set a system property in format <name>=<value>"),
    opt("XX:", "specify non-standard JVM-specific option")
  };

  private static VMOption opt(@NotNull String name, @NotNull String doc) {
    return new VMOption(name, null, null, VMOptionKind.Standard, doc, VMOptionVariant.DASH, null);
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    Document document = parameters.getEditor().getDocument();
    String jrePath = getJrePathForVmOptionsDocument(document);
    if (jrePath == null) return;
    CompletableFuture<JdkOptionsData> jdk = VMOptionsService.getInstance().getOrComputeOptionsForJdk(jrePath);
    JdkOptionsData data = ProgressIndicatorUtils.awaitWithCheckCanceled(jdk);
    if (data == null) return;
    int offset = parameters.getOffset();
    String currentText = document.getText();
    while (offset > 0 && Character.isAlphabetic(currentText.charAt(offset - 1))) {
      offset--;
    }
    JavaRunConfigurationBase settings = document.getUserData(VmOptionsEditor.SETTINGS_KEY);
    if (addXxCompletion(result, data, offset, currentText) ||
        addSimpleOptions(result, settings, data, parameters.getOffset(), currentText)) {
      result.stopHere();
    }
  }

  private static boolean addSimpleOptions(@NotNull CompletionResultSet result,
                                          @Nullable JavaRunConfigurationBase settings,
                                          @NotNull JdkOptionsData data,
                                          int offset,
                                          @NotNull String text) {
    int optionStart = offset;
    while (optionStart > 0 && !Character.isWhitespace(text.charAt(optionStart - 1))) {
      if (text.charAt(optionStart - 1) == OPTION_VALUE_SEPRATOR) {
        return false;
      }
      optionStart--;
    }
    String optionText = text.substring(optionStart, offset);
    result = result.withPrefixMatcher(result.getPrefixMatcher().cloneWithPrefix(optionText));
    addDashOptions(result, settings, data);
    return true;
  }

  private static void addDashOptions(@NotNull CompletionResultSet result,
                                     @Nullable JavaRunConfigurationBase settings,
                                     @NotNull JdkOptionsData data) {
    Stream.of(
      data.getOptions().stream().filter(option1 -> option1.getVariant() != VMOptionVariant.XX),
      Stream.of(STANDARD_OPTIONS),
      settings == null ? null : settings.getKnownVMOptions().stream())
      .flatMap(Function.identity())
      .forEach(option -> {
      String fullLookup = option.getVariant().prefix() + option.getOptionName();
      LookupElementBuilder builder = LookupElementBuilder.create(option.createPointer(), fullLookup)
        .withTypeText(option.getType())
        .withPresentableText(fullLookup);
      VMOptionLookupElementDecorator decorator = option.getDecorator();
      if (decorator != null) {
        result.addElement(decorator.tune(builder));
      }
      else {
        Character suffix = option.getVariant().suffix();
        TailType tailType = suffix == null ? null : TailTypes.charType(suffix);
        result.addElement(TailTypeDecorator.withTail(builder, tailType));
      }
    });
  }

  private static boolean addXxCompletion(@NotNull CompletionResultSet result,
                                         @NotNull JdkOptionsData data,
                                         int offset,
                                         @NotNull String text) {
    boolean booleanStart = false;
    if (offset > 0 && (text.charAt(offset - 1) == '+' || text.charAt(offset - 1) == '-')) {
      offset--;
      booleanStart = true;
    }
    if (!hasOptionPrefix(text, offset, "-XX:")) return false;
    List<LookupElement> elements = new ArrayList<>();
    Set<String> existingOptions = OPTION_SEPARATOR.splitAsStream(text).map(OPTION_MATCHER::matcher)
      .filter(Matcher::matches).map(matcher -> matcher.group(1)).collect(Collectors.toSet());
    for (VMOption option : data.getOptions()) {
      if (option.getVariant() != VMOptionVariant.XX) continue;
      if (existingOptions.contains(option.getOptionName())) continue;
      String type = option.getType();
      LookupElementBuilder e = null;
      TailType tailType = null;
      Icon icon = option.getKind().icon();
      if ("bool".equals(type)) {
        String lookupString = (booleanStart ? "" : Boolean.parseBoolean(option.getDefaultValue()) ? "-" : "+") + option.getOptionName();
        tailType = TailTypes.spaceType();
        e = LookupElementBuilder.create(option.createPointer(), lookupString);
      }
      else if (!booleanStart) {
        String tailText = " = " + option.getDefaultValue();
        tailType = TailTypes.equalsType();
        e = LookupElementBuilder.create(option.createPointer(), option.getOptionName()).withTailText(tailText, true);
      }
      if (e != null) {
        LookupElement element = TailTypeDecorator.withTail(e.withTypeText(type).withIcon(icon), tailType);
        InsertHandler<LookupElement> handler = getInsertHandler(option.getKind());
        if (handler != null) {
          element = LookupElementDecorator.withDelegateInsertHandler(element, handler);
        }
        elements.add(element);
      }
    }
    result = result.withPrefixMatcher(new CamelHumpMatcher(result.getPrefixMatcher().getPrefix(), false));
    result.addAllElements(elements);
    return true;
  }

  private static InsertHandler<LookupElement> getInsertHandler(VMOptionKind kind) {
    String unlockOption = kind.unlockOption();
    return unlockOption == null ? null : (context, item) -> unlock(context, unlockOption);
  }

  private static void unlock(InsertionContext context, String option) {
    Document document = context.getDocument();
    if (document.getCharsSequence().toString().contains(option)) return;
    document.insertString(0, option + (context.getEditor().isOneLineMode() ? " " : "\n"));
  }

  private static boolean hasOptionPrefix(@NotNull CharSequence sequence, int offset, @NotNull String xxPrefix) {
    return offset >= xxPrefix.length() && sequence.subSequence(offset - xxPrefix.length(), offset).toString().equals(xxPrefix) &&
           (offset == xxPrefix.length() || Character.isWhitespace(sequence.charAt(offset - xxPrefix.length() - 1)));
  }

  @Nullable
  private static String getJrePathForVmOptionsDocument(Document document) {
    String path = document.getUserData(EditCustomVmOptionsAction.JRE_PATH_KEY);
    if (path != null) {
      return path;
    }
    JavaRunConfigurationBase settings = document.getUserData(VmOptionsEditor.SETTINGS_KEY);
    if (settings == null) return null;
    return getJrePath(settings);
  }

  @Nullable
  private static String getJrePath(JavaRunConfigurationBase settings) {
    String jrePath = null;
    Sdk sdk;
    if (settings.isAlternativeJrePathEnabled()) {
      jrePath = settings.getAlternativeJrePath();
      sdk = jrePath == null ? null : ProjectJdkTable.getInstance().findJdk(jrePath);
    }
    else {
      Module module = settings.getConfigurationModule().getModule();
      if (module != null) {
        sdk = JavaParameters.getJdkToRunModule(module, false);
      }
      else {
        sdk = PathUtilEx.getAnyJdk(settings.getProject());
      }
    }
    if (sdk != null) {
      jrePath = sdk.getHomePath();
    }
    return jrePath;
  }
}
