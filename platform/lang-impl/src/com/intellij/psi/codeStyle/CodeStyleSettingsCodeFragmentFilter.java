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
package com.intellij.psi.codeStyle;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;

import static com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.forLanguage;

public class CodeStyleSettingsCodeFragmentFilter {
  private static final Logger LOG = Logger.getInstance(CodeStyleSettingsCodeFragmentFilter.class);

  private final Project myProject;
  private final PsiFile myFile;
  private final Document myDocument;
  private final RangeMarker myTextRangeMarker;
  private final LanguageCodeStyleSettingsProvider myProvider;

  private CommonCodeStyleSettings myCommonSettings;

  public CodeStyleSettingsCodeFragmentFilter(@NotNull PsiFile file, @NotNull TextRange range) {
    myProvider = forLanguage(file.getLanguage());
    myProject = file.getProject();
    myFile =
      PsiFileFactory.getInstance(myProject).createFileFromText("copy" + file.getName(), file.getLanguage(), file.getText(), true, false);
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(myFile);
    LOG.assertTrue(myDocument != null);
    myTextRangeMarker = myDocument.createRangeMarker(range.getStartOffset(), range.getEndOffset());
  }

  @NotNull
  public CodeStyleSettingsToShow getFieldNamesAffectingCodeFragment(LanguageCodeStyleSettingsProvider.SettingsType... types) {
    CodeStyleSettingsManager codeStyleSettingsManager = CodeStyleSettingsManager.getInstance(myProject);
    CodeStyleSettings clonedSettings = codeStyleSettingsManager.getCurrentSettings().clone();
    myCommonSettings = clonedSettings.getCommonSettings(myProvider.getLanguage());

    try {
      codeStyleSettingsManager.setTemporarySettings(clonedSettings);

      String title = CodeInsightBundle.message("configure.code.style.on.fragment.dialog.title");
      SequentialModalProgressTask progressTask = new SequentialModalProgressTask(myProject, StringUtil.capitalizeWords(title, true));
      progressTask.setCancelText(CodeInsightBundle.message("configure.code.style.on.fragment.dialog.cancel"));
      CompositeSequentialTask compositeTask = new CompositeSequentialTask(progressTask);
      compositeTask.setProgressText(CodeInsightBundle.message("configure.code.style.on.fragment.dialog.progress.text"));
      compositeTask.setProgressText2(CodeInsightBundle.message("configure.code.style.on.fragment.dialog.progress.text.under"));

      final Map<LanguageCodeStyleSettingsProvider.SettingsType, FilterFieldsTask> typeToTask = ContainerUtil.newHashMap();
      for (LanguageCodeStyleSettingsProvider.SettingsType type : types) {
        Set<String> fields = myProvider.getSupportedFields(type);
        FilterFieldsTask task = new FilterFieldsTask(fields);
        compositeTask.addTask(task);
        typeToTask.put(type, task);
      }

      progressTask.setTask(compositeTask);
      progressTask.setMinIterationTime(10);
      ProgressManager.getInstance().run(progressTask);

      return new CodeStyleSettingsToShow() {
        @Override
        public List<String> getSettings(LanguageCodeStyleSettingsProvider.SettingsType type) {
          FilterFieldsTask task = typeToTask.get(type);
          return task.getAffectedFields();
        }
      };
    }
    finally {
      codeStyleSettingsManager.dropTemporarySettings();
    }
  }

  private boolean formattingChangedFragment() {
    final int rangeStart = myTextRangeMarker.getStartOffset();
    final int rangeEnd = myTextRangeMarker.getEndOffset();
    CharSequence textBefore = myDocument.getCharsSequence();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CodeStyleManager.getInstance(myProject).reformatText(myFile, rangeStart, rangeEnd);
      }
    });

    if (rangeStart != myTextRangeMarker.getStartOffset() || rangeEnd != myTextRangeMarker.getEndOffset()) {
      return true;
    }
    else {
      CharSequence fragmentBefore = textBefore.subSequence(rangeStart, rangeEnd);
      CharSequence fragmentAfter = myDocument.getCharsSequence().subSequence(rangeStart, rangeEnd);
      return !StringUtil.equals(fragmentBefore, fragmentAfter);
    }
  }

  private class FilterFieldsTask implements SequentialTaskWithFixedIterationsNumber {
    private final Iterator<String> myIterator;
    private final int myTotalFieldsNumber;
    private final Collection<String> myAllFields;

    private List<String> myAffectingFields = ContainerUtil.newArrayList();

    public FilterFieldsTask(@NotNull Collection<String> fields) {
      myAllFields = fields;
      myIterator = fields.iterator();
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
      if (!isDone()) myAffectingFields = ContainerUtil.newArrayList(myAllFields);
    }

    @Override
    public boolean isDone() {
      return !myIterator.hasNext();
    }

    @Override
    public boolean iteration() {
      if (!myIterator.hasNext()) return true;

      String field = myIterator.next();
      try {
        Field classField = CommonCodeStyleSettings.class.getField(field);

        if (classField.getType() == Integer.TYPE) {
          int oldValue = classField.getInt(myCommonSettings);
          int newValue = getNewIntValue(classField, oldValue);
          if (newValue == oldValue) {
            return true;
          }
          classField.set(myCommonSettings, newValue);
        }
        else if (classField.getType() == Boolean.TYPE) {
          boolean value = classField.getBoolean(myCommonSettings);
          classField.set(myCommonSettings, !value);
        }
        else {
          return true;
        }

        if (formattingChangedFragment()) {
          myAffectingFields.add(field);
        }
      }
      catch (Exception ignored) {
      }

      return true;
    }

    private int getNewIntValue(Field classField, int oldValue) throws IllegalAccessException {
      int newValue = oldValue;

      String fieldName = classField.getName();
      if (fieldName.contains("WRAP")) {
        newValue = oldValue == CommonCodeStyleSettings.WRAP_ALWAYS
                   ? CommonCodeStyleSettings.DO_NOT_WRAP
                   : CommonCodeStyleSettings.WRAP_ALWAYS;
      }
      else if (fieldName.contains("BRACE")) {
        newValue = oldValue == CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
                   ? CommonCodeStyleSettings.DO_NOT_FORCE
                   : CommonCodeStyleSettings.WRAP_ALWAYS;
      }

      return newValue;
    }

    @Override
    public void prepare() {
    }
  }

  public interface CodeStyleSettingsToShow {
    List<String> getSettings(LanguageCodeStyleSettingsProvider.SettingsType type);
  }
}

interface SequentialTaskWithFixedIterationsNumber extends SequentialTask {
  int getTotalIterationsNumber();
}

class CompositeSequentialTask implements SequentialTask {
  private List<SequentialTaskWithFixedIterationsNumber> myUnfinishedTasks = ContainerUtil.newArrayList();
  private SequentialTask myCurrentTask = null;

  private int myIterationsFinished;
  private int myTotalIterations = 0;

  private final SequentialModalProgressTask myProgressTask;
  private String myProgressText;
  private String myProgressText2;

  public CompositeSequentialTask(@NotNull SequentialModalProgressTask progressTask) {
    myProgressTask = progressTask;
  }

  public void addTask(@NotNull SequentialTaskWithFixedIterationsNumber task) {
    myUnfinishedTasks.add(task);
    myTotalIterations += task.getTotalIterationsNumber();
  }

  public void setProgressText(@NotNull String progressText) {
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
  public void prepare() {
  }

  @Override
  public void stop() {
    if (myCurrentTask != null) myCurrentTask.stop();
    for (SequentialTaskWithFixedIterationsNumber task : myUnfinishedTasks) {
      task.stop();
    }
  }

  public void setProgressText2(String progressText2) {
    myProgressText2 = progressText2;
  }
}
