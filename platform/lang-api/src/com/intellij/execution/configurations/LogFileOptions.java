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

package com.intellij.execution.configurations;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SmartHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The information about a single log file displayed in the console when the configuration
 * is run.
 *
 * @since 5.1
 */
public class LogFileOptions implements JDOMExternalizable {
  @NonNls private static final String PATH = "path";
  @NonNls private static final String CHECKED = "checked";
  @NonNls private static final String ALIAS = "alias";
  @NonNls private static final String SKIPPED = "skipped";
  @NonNls private static final String SHOW_ALL = "show_all";
  @NonNls private static final String CHARSET = "charset";

  private String myName;
  private String myPathPattern;
  private boolean myEnabled;
  private boolean mySkipContent;
  private boolean myShowAll;
  @NotNull
  private Charset myCharset;

  //read external
  public LogFileOptions() {
  }

  public LogFileOptions(String name, String path, boolean enabled, boolean skipContent, final boolean showAll) {
    this(name, path, null, enabled, skipContent, showAll);
  }

  public LogFileOptions(String name, String path, @Nullable final Charset charset, boolean enabled, boolean skipContent, final boolean showAll) {
    myName = name;
    myPathPattern = path;
    myEnabled = enabled;
    mySkipContent = skipContent;
    myShowAll = showAll;
    myCharset = charset != null ? charset : Charset.defaultCharset();
  }

  public String getName() {
    return myName;
  }

  public String getPathPattern() {
    return myPathPattern;
  }

  @NotNull
  public Set<String> getPaths(){
    File logFile = new File(myPathPattern);
    if (logFile.exists()){
      return Collections.singleton(myPathPattern);
    }

    int dirIndex = myPathPattern.lastIndexOf(File.separator);
    if (dirIndex == -1) {
      return Collections.emptySet();
    }

    List<File> files = new SmartList<>();
    collectMatchedFiles(new File(myPathPattern.substring(0, dirIndex)), Pattern.compile(FileUtil.convertAntToRegexp(myPathPattern.substring(dirIndex + File.separator.length()))), files);
    if (files.isEmpty()) {
      return Collections.emptySet();
    }

    if (myShowAll) {
      SmartHashSet<String> result = new SmartHashSet<>();
      result.ensureCapacity(files.size());
      for (File file : files) {
        result.add(file.getPath());
      }
      return result;
    }
    else {
      File lastFile = null;
      for (File file : files) {
        if (lastFile != null) {
          if (file.lastModified() > lastFile.lastModified()) {
            lastFile = file;
          }
        }
        else {
          lastFile = file;
        }
      }
      assert lastFile != null;
      return Collections.singleton(lastFile.getPath());
    }
  }

  public static void collectMatchedFiles(@NotNull File root, @NotNull Pattern pattern, @NotNull List<File> files) {
    final File[] dirs = root.listFiles();
    if (dirs == null) return;
    for (File dir : dirs) {
      if (pattern.matcher(dir.getName()).matches() && dir.isFile()) {
        files.add(dir);
      }
    }
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public boolean isSkipContent() {
    return mySkipContent;
  }

  public void setEnable(boolean enable) {
    myEnabled = enable;
  }


  public void setName(final String name) {
    myName = name;
  }

  public void setPathPattern(final String pathPattern) {
    myPathPattern = pathPattern;
  }

  public void setSkipContent(final boolean skipContent) {
    mySkipContent = skipContent;
  }

  public void setShowAll(final boolean showAll) {
    myShowAll = showAll;
  }

  public void setLast(final boolean last) {
    myShowAll = !last;
  }

  public boolean isShowAll() {
    return myShowAll;
  }

  public Charset getCharset() {
    return myCharset;
  }

  public void setCharset(Charset charset) {
    myCharset = charset;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    String file = element.getAttributeValue(PATH);
    if (file != null){
      file = FileUtil.toSystemDependentName(file);
    }
    setPathPattern(file);

    Boolean checked = Boolean.valueOf(element.getAttributeValue(CHECKED));
    setEnable(checked.booleanValue());

    final String skipped = element.getAttributeValue(SKIPPED);
    Boolean skip = skipped != null ? Boolean.valueOf(skipped) : Boolean.TRUE;
    setSkipContent(skip.booleanValue());

    final String all = element.getAttributeValue(SHOW_ALL);
    Boolean showAll = skipped != null ? Boolean.valueOf(all) : Boolean.TRUE;
    setShowAll(showAll.booleanValue());

    setName(element.getAttributeValue(ALIAS));

    final String charsetStr = element.getAttributeValue(CHARSET);
    try {
      setCharset(Charset.forName(charsetStr));
    }
    catch (Exception ignored) {
      setCharset(Charset.defaultCharset());
    }
  }

  @Override
  public void writeExternal(Element element) {
    element.setAttribute(PATH, FileUtil.toSystemIndependentName(getPathPattern()));
    element.setAttribute(CHECKED, String.valueOf(isEnabled()));
    element.setAttribute(SKIPPED, String.valueOf(isSkipContent()));
    element.setAttribute(SHOW_ALL, String.valueOf(isShowAll()));
    element.setAttribute(ALIAS, getName());
    if (!getCharset().equals(Charset.defaultCharset())) {
      element.setAttribute(CHARSET, getCharset().name());
    }
  }

  public static boolean areEqual(@Nullable LogFileOptions options1, @Nullable LogFileOptions options2) {
    if (options1 == null || options2 == null) {
      return options1 == options2;
    }

    return options1.myName.equals(options2.myName) &&
           options1.myPathPattern.equals(options2.myPathPattern) &&
           !options1.myShowAll == !options2.myShowAll &&
           options1.myEnabled == options2.myEnabled &&
           options1.mySkipContent == options2.mySkipContent;

  }
}
