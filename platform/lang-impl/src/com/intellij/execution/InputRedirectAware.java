// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public interface InputRedirectAware extends RunConfiguration {
  String REDIRECT_INPUT = "REDIRECT_INPUT";
  String INPUT_FILE = "INPUT_FILE";

  List<String> TYPES_WITH_REDIRECT_AWARE_UI = Arrays.asList("Application", "Java Scratch", "JUnit", "JarApplication");

  @NotNull
  InputRedirectOptions getInputRedirectOptions();

  @Nullable
  static InputRedirectOptions getInputRedirectOptions(@NotNull RunConfiguration runConfiguration) {
    return TYPES_WITH_REDIRECT_AWARE_UI.contains(runConfiguration.getType().getId())
           ? ((InputRedirectAware)runConfiguration).getInputRedirectOptions()
           : null;
  }

  @Nullable
  static File getInputFile(@NotNull RunConfiguration configuration) {
    InputRedirectOptions inputRedirectOptions = getInputRedirectOptions(configuration);

    if (inputRedirectOptions == null || !inputRedirectOptions.myRedirectInput) {
      return null;
    }

    String filePath = inputRedirectOptions.myInputFile;
    if (!StringUtil.isEmpty(filePath)) {
      filePath = FileUtil.toSystemDependentName(filePath);
      File file = new File(filePath);
      if (configuration instanceof CommonProgramRunConfigurationParameters && !FileUtil.isAbsolute(filePath)) {
        String directory = ((CommonProgramRunConfigurationParameters)configuration).getWorkingDirectory();
        if (directory != null) {
          file = new File(new File(directory), filePath);
        }
      }
      return file;
    }
    return null;
  }

  class InputRedirectOptions {
    @Nullable public String myInputFile = null;
    public boolean myRedirectInput = false;

    public void readExternal(@NotNull Element element) {
      myRedirectInput = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, REDIRECT_INPUT, "false"));
      myInputFile = JDOMExternalizerUtil.readField(element, INPUT_FILE);
    }

    public void writeExternal(Element element) {
      if (myRedirectInput) {
        JDOMExternalizerUtil.writeField(element, REDIRECT_INPUT, "true");
      }
      if (myInputFile != null) {
        JDOMExternalizerUtil.writeField(element, INPUT_FILE, myInputFile);
      }
    }

    public InputRedirectOptions copy() {
      InputRedirectOptions options = new InputRedirectOptions();
      options.myRedirectInput = myRedirectInput;
      options.myInputFile = myInputFile;
      return options;
    }
  }
}
