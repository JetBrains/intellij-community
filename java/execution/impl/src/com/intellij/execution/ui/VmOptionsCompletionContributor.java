// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.vmOptions.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VmOptionsCompletionContributor extends CompletionContributor implements DumbAware {
  private static final Pattern OPTION_SEPARATOR = Pattern.compile("\\s+");
  private static final Pattern OPTION_MATCHER = Pattern.compile("^-XX:[+\\-]?(\\w+)(=.+)?$");

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    Document document = parameters.getEditor().getDocument();
    JavaRunConfigurationBase settings = document.getUserData(VmOptionsEditor.SETTINGS_KEY);
    if (settings == null) return;
    String jrePath = getJrePath(settings);
    if (jrePath == null) return;
    CompletableFuture<JdkOptionsData> jdk = VMOptionsService.getInstance().getOrComputeOptionsForJdk(jrePath);
    JdkOptionsData data = ProgressIndicatorUtils.awaitWithCheckCanceled(jdk);
    if (data == null) return;
    int offset = parameters.getOffset();
    String currentText = document.getText();
    while (offset > 0 && Character.isAlphabetic(currentText.charAt(offset - 1))) {
      offset--;
    }
    if (addXxCompletion(result, data, offset, currentText) || addSimpleOptions(result, data, offset, currentText)) {
      result.stopHere();
    }
  }

  private static boolean addSimpleOptions(@NotNull CompletionResultSet result,
                                          @NotNull JdkOptionsData data,
                                          int offset,
                                          @NotNull String text) {
    if (hasOptionPrefix(text, offset, "--")) {
      String[] options = {"add-reads", "add-exports", "add-opens", "limit-modules", "patch-module"};
      for (String option : options) {
        result.addElement(
          TailTypeDecorator.withTail(LookupElementBuilder.create(option).withPresentableText("--" + option), TailType.SPACE));
      }
      return true;
    }
    if (hasOptionPrefix(text, offset, "-")) {
      String[] options = {"ea", "enableassertions", "da", "disableassertions", "esa", "dsa", "agentpath:", "agentlib:",
        "javaagent:", "XX:", "D"};
      for (String option : options) {
        result.addElement(LookupElementBuilder.create(option).withPresentableText("-" + option));
      }
      for (VMOption option : data.getOptions()) {
        if (option.getVariant() == VMOptionVariant.X) {
          String name = "X" + option.getOptionName();
          result.addElement(LookupElementBuilder.create(name).withPresentableText("-" + name));
        }
      }
      return true;
    }
    return false;
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
      Icon icon = switch (option.getKind()) {
        case Product -> AllIcons.Actions.ArrowExpand;
        case Diagnostic -> AllIcons.General.ShowInfos;
        case Experimental -> AllIcons.General.Warning;
      };
      if ("bool".equals(type)) {
        String lookupString = (booleanStart ? "" : Boolean.parseBoolean(option.getDefaultValue()) ? "-" : "+") + option.getOptionName();
        tailType = TailType.SPACE;
        e = LookupElementBuilder.create(lookupString);
      }
      else if (!booleanStart) {
        String tailText = " = " + option.getDefaultValue();
        tailType = TailType.EQUALS;
        e = LookupElementBuilder.create(option.getOptionName()).withTailText(tailText, true);
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
    result.addAllElements(elements);
    return true;
  }

  private static InsertHandler<LookupElement> getInsertHandler(VMOptionKind kind) {
    return switch (kind) {
      case Product -> null;
      case Diagnostic -> (context, item) -> unlock(context, "-XX:+UnlockDiagnosticVMOptions");
      case Experimental -> (context, item) -> unlock(context, "-XX:+UnlockExperimentalVMOptions");
    };
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
  private static String getJrePath(JavaRunConfigurationBase settings) {
    String jrePath = null;
    if (settings.isAlternativeJrePathEnabled()) {
      jrePath = settings.getAlternativeJrePath();
    }
    else {
      Module module = settings.getConfigurationModule().getModule();
      Sdk sdk;
      if (module != null) {
        sdk = JavaParameters.getJdkToRunModule(module, false);
      }
      else {
        sdk = PathUtilEx.getAnyJdk(settings.getProject());
      }
      if (sdk != null) {
        jrePath = sdk.getHomePath();
      }
    }
    return jrePath;
  }
}
