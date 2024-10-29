// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;

import static com.intellij.psi.codeStyle.CodeStyleSettingsProvider.EXTENSION_POINT_NAME;

@ApiStatus.Internal
public final class CodeStyleSettingsCodeFragmentFilter {
  private static final Logger LOG = Logger.getInstance(CodeStyleSettingsCodeFragmentFilter.class);

  private final Project myProject;
  private final PsiFile myFile;
  private final Document myDocument;
  private final RangeMarker myTextRangeMarker;
  private final LanguageCodeStyleSettingsProvider myProvider;

  public CodeStyleSettingsCodeFragmentFilter(@NotNull PsiFile file,
                                             @NotNull TextRange range,
                                             @NotNull LanguageCodeStyleSettingsProvider settingsProvider) {
    myProvider = settingsProvider;
    myProject = file.getProject();
    myFile =
      PsiFileFactory.getInstance(myProject).createFileFromText("copy" + file.getName(), file.getLanguage(), file.getText(), true, false);
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(myFile);
    LOG.assertTrue(myDocument != null);
    myTextRangeMarker = myDocument.createRangeMarker(range.getStartOffset(), range.getEndOffset());
  }

  public @NotNull CodeStyleSettingsToShow getFieldNamesAffectingCodeFragment(LanguageCodeStyleSettingsProvider.SettingsType... types) {
    Ref<CodeStyleSettingsToShow> settingsToShow = new Ref<>();
    CodeStyle.runWithLocalSettings(myProject,
                                   CodeStyle.getSettings(myFile),
                                   tempSettings -> settingsToShow.set(computeFieldsWithTempSettings(tempSettings, types)));
    return settingsToShow.get();
  }

  private @NotNull CodeStyleSettingsToShow computeFieldsWithTempSettings(@NotNull CodeStyleSettings tempSettings, LanguageCodeStyleSettingsProvider.SettingsType @NotNull [] types) {
    CommonCodeStyleSettings commonSettings = tempSettings.getCommonSettings(myProvider.getLanguage());
    CustomCodeStyleSettings customSettings = getCustomSettings(myProvider, tempSettings);

    String title = CodeInsightBundle.message("configure.code.style.on.fragment.dialog.title");
    SequentialModalProgressTask progressTask = new SequentialModalProgressTask(myProject, StringUtil.capitalizeWords(title, true));
    progressTask.setCancelText(CodeInsightBundle.message("configure.code.style.on.fragment.dialog.cancel"));
    CompositeSequentialTask compositeTask = new CompositeSequentialTask(progressTask);
    compositeTask.setProgressText(CodeInsightBundle.message("configure.code.style.on.fragment.dialog.progress.text"));
    compositeTask.setProgressText2(CodeInsightBundle.message("configure.code.style.on.fragment.dialog.progress.text.under"));

    final Map<LanguageCodeStyleSettingsProvider.SettingsType, FilterFieldsTask> typeToTask = new HashMap<>();
    for (LanguageCodeStyleSettingsProvider.SettingsType type : types) {
      Set<String> fields = myProvider.getSupportedFields(type);
      FilterFieldsTask task = new FilterFieldsTask(commonSettings, customSettings, fields);
      compositeTask.addTask(task);
      typeToTask.put(type, task);
    }

    Set<String> otherFields = myProvider.getSupportedFields();
    final FilterFieldsTask otherFieldsTask = new FilterFieldsTask(commonSettings, customSettings, otherFields);
    if (!otherFields.isEmpty()) {
      compositeTask.addTask(otherFieldsTask);
    }

    progressTask.setTask(compositeTask);
    progressTask.setMinIterationTime(10);
    ProgressManager.getInstance().run(progressTask);

    return new CodeStyleSettingsToShow() {
      @Override
      public List<String> getSettings(LanguageCodeStyleSettingsProvider.SettingsType type) {
        return typeToTask.get(type).getAffectedFields();
      }

      @Override
      public List<String> getOtherSetting() {
        return new ArrayList<>(otherFieldsTask.getAffectedFields());
      }
    };
  }

  private static @Nullable CustomCodeStyleSettings getCustomSettings(@NotNull LanguageCodeStyleSettingsProvider languageProvider,
                                                                     @NotNull CodeStyleSettings tempSettings) {
    CustomCodeStyleSettings fromLanguageProvider = getCustomSettingsFromProvider(languageProvider, tempSettings);
    if (fromLanguageProvider != null) {
      return fromLanguageProvider;
    }
    for (CodeStyleSettingsProvider codeStyleSettingsProvider : EXTENSION_POINT_NAME.getExtensionList()) {
      if (languageProvider.getLanguage().equals(codeStyleSettingsProvider.getLanguage())) {
        CustomCodeStyleSettings settings = getCustomSettingsFromProvider(codeStyleSettingsProvider, tempSettings);
        if (settings != null) {
          return settings;
        }
      }
    }
    return null;
  }

  private static @Nullable CustomCodeStyleSettings getCustomSettingsFromProvider(@NotNull CodeStyleSettingsProvider languageProvider,
                                                                                 @NotNull CodeStyleSettings tempSettings) {
    CustomCodeStyleSettings modelSettings = languageProvider.createCustomSettings(tempSettings);
    return modelSettings != null ? tempSettings.getCustomSettings(modelSettings.getClass()) : null;
  }

  private boolean formattingChangedFragment() {
    final int rangeStart = myTextRangeMarker.getStartOffset();
    final int rangeEnd = myTextRangeMarker.getEndOffset();
    CharSequence textBefore = myDocument.getCharsSequence();

    ApplicationManager.getApplication().runWriteAction(() -> CodeStyleManager.getInstance(myProject).reformatText(myFile, rangeStart, rangeEnd));

    if (rangeStart != myTextRangeMarker.getStartOffset() || rangeEnd != myTextRangeMarker.getEndOffset()) {
      return true;
    }
    else {
      CharSequence fragmentBefore = textBefore.subSequence(rangeStart, rangeEnd);
      CharSequence fragmentAfter = myDocument.getCharsSequence().subSequence(rangeStart, rangeEnd);
      return !StringUtil.equals(fragmentBefore, fragmentAfter);
    }
  }

  private final class FilterFieldsTask implements SequentialTaskWithFixedIterationsNumber {
    private final Iterator<String> myIterator;
    private final int myTotalFieldsNumber;
    private final Collection<String> myAllFields;

    private List<String> myAffectingFields = new ArrayList<>();
    private final Object myCommonSettings;
    private final @Nullable CustomCodeStyleSettings myCustomSettings;

    FilterFieldsTask(@NotNull CommonCodeStyleSettings commonSettings,
                     @Nullable CustomCodeStyleSettings customSettings,
                     @NotNull Collection<String> fields) {
      myCustomSettings = customSettings;
      myAllFields = fields;
      myIterator = fields.iterator();
      myCommonSettings = commonSettings;
      myTotalFieldsNumber = fields.size();
    }

    public List<String> getAffectedFields() {
      return myAffectingFields;
    }

    @Override
    public int getTotalIterationsNumber() {
      return myTotalFieldsNumber;
    }

    @Override
    public void stop() {
      if (!isDone()) myAffectingFields = new ArrayList<>(myAllFields);
    }

    @Override
    public boolean isDone() {
      return !myIterator.hasNext();
    }

    @Override
    public boolean iteration() {
      if (!myIterator.hasNext()) return true;

      String field = myIterator.next();
      if (myCustomSettings != null) {
        checkFieldAffectsSettings(myCustomSettings, field);
      }
      checkFieldAffectsSettings(myCommonSettings, field);

      return true;
    }

    private void checkFieldAffectsSettings(@NotNull Object settings, String field) {
      try {
        Field classField = settings.getClass().getField(field);
        if (classField.getType() == Integer.TYPE) {
          int oldValue = classField.getInt(settings);
          int newValue = getNewIntValue(classField, oldValue);
          if (newValue == oldValue) {
            return;
          }
          classField.set(settings, newValue);
        }
        else if (classField.getType() == Boolean.TYPE) {
          boolean value = classField.getBoolean(settings);
          classField.set(settings, !value);
        }
        else {
          return;
        }

        if (formattingChangedFragment()) {
          myAffectingFields.add(field);
        }
      }
      catch (Exception ignored) {
      }
    }

    private static int getNewIntValue(Field classField, int oldValue) {
      String fieldName = classField.getName();
      if (fieldName.contains("WRAP")) {
        return oldValue == CommonCodeStyleSettings.WRAP_ALWAYS
                   ? CommonCodeStyleSettings.DO_NOT_WRAP
                   : CommonCodeStyleSettings.WRAP_ALWAYS;
      }
      else if (fieldName.contains("BRACE_FORCE")) {
        return oldValue == CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
                   ? CommonCodeStyleSettings.DO_NOT_FORCE
                   : CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
      }
      else if (fieldName.contains("BRACE_STYLE")) {
        return oldValue == CommonCodeStyleSettings.END_OF_LINE
               ? CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
               : CommonCodeStyleSettings.END_OF_LINE;
      }

      return oldValue;
    }
  }

  public interface CodeStyleSettingsToShow {
    List<String> getSettings(LanguageCodeStyleSettingsProvider.SettingsType type);

    List<String> getOtherSetting();
  }
}

interface SequentialTaskWithFixedIterationsNumber extends SequentialTask {
  int getTotalIterationsNumber();
}

final class CompositeSequentialTask implements SequentialTask {
  private final List<SequentialTaskWithFixedIterationsNumber> myUnfinishedTasks = new ArrayList<>();
  private SequentialTask myCurrentTask = null;

  private int myIterationsFinished;
  private int myTotalIterations = 0;

  private final SequentialModalProgressTask myProgressTask;
  private @NlsContexts.ProgressText String myProgressText;
  private @NlsContexts.ProgressDetails String myProgressText2;

  CompositeSequentialTask(@NotNull SequentialModalProgressTask progressTask) {
    myProgressTask = progressTask;
  }

  public void addTask(@NotNull SequentialTaskWithFixedIterationsNumber task) {
    myUnfinishedTasks.add(task);
    myTotalIterations += task.getTotalIterationsNumber();
  }

  public void setProgressText(@NotNull @NlsContexts.ProgressText String progressText) {
    myProgressText = progressText;
  }

  @Override
  public boolean isDone() {
    return myCurrentTask == null && myUnfinishedTasks.size() == 0;
  }

  @Override
  public boolean iteration() {
    popUntilCurrentTaskUnfinishedOrNull();

    if (myCurrentTask != null) {
      ProgressIndicator indicator = myProgressTask.getIndicator();
      if (indicator != null) {
        if (myProgressText != null) indicator.setText(myProgressText);
        if (myProgressText2 != null) indicator.setText2(myProgressText2);
        indicator.setFraction((double)myIterationsFinished++ / myTotalIterations);
      }
      myCurrentTask.iteration();
    }

    return true;
  }

  private void popUntilCurrentTaskUnfinishedOrNull() {
    if (myCurrentTask != null) {
      if (!myCurrentTask.isDone()) {
        return;
      }
      myCurrentTask = null;
      popUntilCurrentTaskUnfinishedOrNull();
    }
    else {
      if (myUnfinishedTasks.size() > 0) {
        myCurrentTask = myUnfinishedTasks.get(0);
        myUnfinishedTasks.remove(0);
        popUntilCurrentTaskUnfinishedOrNull();
      }
    }
  }

  @Override
  public void stop() {
    if (myCurrentTask != null) myCurrentTask.stop();
    for (SequentialTaskWithFixedIterationsNumber task : myUnfinishedTasks) {
      task.stop();
    }
  }

  public void setProgressText2(@NlsContexts.ProgressDetails String progressText2) {
    myProgressText2 = progressText2;
  }
}
