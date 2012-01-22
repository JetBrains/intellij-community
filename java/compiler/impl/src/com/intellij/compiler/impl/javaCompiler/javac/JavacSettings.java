/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.TestOnly;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

public class JavacSettings {
  public boolean DEBUGGING_INFO = true;
  public boolean GENERATE_NO_WARNINGS = false;
  public boolean DEPRECATION = true;
  public String ADDITIONAL_OPTIONS_STRING = "";
  public int MAXIMUM_HEAP_SIZE = 128;

  private boolean myTestsUseExternalCompiler = false;

  public Collection<String> getOptions(Project project) {
    List<String> options = new ArrayList<String>();
    if (DEBUGGING_INFO) {
      options.add("-g");
    }
    if (DEPRECATION) {
      options.add("-deprecation");
    }
    if (GENERATE_NO_WARNINGS) {
      options.add("-nowarn");
    }
    boolean isEncodingSet = false;
    final StringTokenizer tokenizer = new StringTokenizer(ADDITIONAL_OPTIONS_STRING, " \t\r\n");
    while(tokenizer.hasMoreTokens()) {
      final String token = tokenizer.nextToken();
      if(!acceptUserOption(token)) {
        continue;
      }
      options.add(token);
      if ("-encoding".equals(token)) {
        isEncodingSet = true;
      }
    }
    if (!isEncodingSet && acceptEncoding()) {
      final Charset ideCharset = EncodingProjectManager.getInstance(project).getDefaultCharset();
      if (ideCharset != null && !Comparing.equal(CharsetToolkit.getDefaultSystemCharset(), ideCharset)) {
        options.add("-encoding");
        options.add(ideCharset.name());
      }
    }
    return options;
  }

  protected boolean acceptUserOption(String token) {
    return !("-g".equals(token) || "-deprecation".equals(token) || "-nowarn".equals(token));
  }

  protected boolean acceptEncoding() {
    return true;
  }

  public String getOptionsString(final Project project) {
    final StringBuilder options = new StringBuilder();
    for (String option : getOptions(project)) {
      if (options.length() > 0) {
        options.append(" ");
      }
      options.append(option);
    }
   return options.toString();
  }

  public static JavacSettings getInstance(Project project) {
    return ServiceManager.getService(project, JavacConfiguration.class).getSettings();
  }


  @TestOnly
  public boolean isTestsUseExternalCompiler() {
    return myTestsUseExternalCompiler;
  }

  @TestOnly
  public void setTestsUseExternalCompiler(boolean testsUseExternalCompiler) {
    myTestsUseExternalCompiler = testsUseExternalCompiler;
  }
}