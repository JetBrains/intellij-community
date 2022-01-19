// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MissingJavadocInspection extends LocalInspectionTool {

  public boolean ignoreDeprecated = false;
  public boolean ignoreAccessors = false;
  public Options packageOptions = new Options();
  public Options moduleOptions = new Options();
  public Options topLevelClassOptions = new Options();
  public Options innerClassOptions = new Options();
  public Options methodOptions = new Options("@return@param@throws or @exception");
  public Options fieldOptions = new Options();

  @Override
  public @Nullable JComponent createOptionsPanel() {
    return JavadocUIUtil.INSTANCE.missingJavadocOptions(this);
  }

  public static class Options {
    public String minimalVisibility = "public";
    public String requiredTags = "";
    public boolean isEnabled = false;

    public Options() {}

    public Options(String tags){
      requiredTags = tags;
    }

    public boolean isTagRequired(String tag) {
      return requiredTags.contains(tag);
    }

    public void setTagRequired(String tag, boolean value) {
      if (value) {
        if (!isTagRequired(tag)) requiredTags += tag;
      } else {
        requiredTags = requiredTags.replaceAll(tag, "");
      }
    }
  }
}
