/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashSet;
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
  private String myName;
  private String myPathPattern;
  private boolean myEnabled;
  private boolean mySkipContent;
  private boolean myLast;

  //read external
  public LogFileOptions() {
  }

  public LogFileOptions(String name, String path, boolean enabled, boolean skipContent, final boolean showAll) {
    myName = name;
    myPathPattern = path;
    myEnabled = enabled;
    mySkipContent = skipContent;
    myLast = !showAll;
  }

  public String getName() {
    return myName;
  }

  public String getPathPattern() {
    return myPathPattern;
  }

  public Set<String> getPaths(){
    Set<String> result = new HashSet<String>();
    final File logFile = new File(myPathPattern);
    if (logFile.exists()){
      result.add(myPathPattern);
      return result;
    }
    try {
      if (logFile.createNewFile()){
        result.add(myPathPattern);
        return result;
      }
    }
    catch (IOException e) {
      //do nothing
    }
    final int dirIndex = myPathPattern.lastIndexOf(File.separator);
    if (dirIndex != -1){
      final File dir = new File(myPathPattern.substring(0, dirIndex));
      if (dir.isDirectory()){
        final Pattern pattern = Pattern.compile(myPathPattern.substring(dirIndex + File.separator.length()).replace("*", ".*"));
        final File[] files = dir.listFiles(new FileFilter() {
          public boolean accept(File pathname) {
            try {
              if (!FileUtil.isAncestor(dir, pathname, true)) return false;
            }
            catch (IOException e) {
              return false;
            }
            return pattern.matcher(FileUtil.getRelativePath(dir, pathname)).matches();
          }
        });
        if (files != null && files.length > 0){
          if (myLast) {
            File lastFile = null;
            for (File file : files) {
              if (lastFile != null){
                if (file.lastModified() > lastFile.lastModified()){
                  lastFile = file;
                }
              } else {
                lastFile = file;
              }
            }
            assert lastFile != null;
            result.add(lastFile.getPath());
          } else {
            for (File file : files) {
              result.add(file.getPath());
            }
          }
        }
      }
    }
    return result;
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

  public void setLast(final boolean last) {
    myLast = last;
  }

  public boolean isShowAll() {
    return !myLast;
  }

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
    setLast(!showAll.booleanValue());

    setName(element.getAttributeValue(ALIAS));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(PATH, FileUtil.toSystemIndependentName(getPathPattern()));
    element.setAttribute(CHECKED, String.valueOf(isEnabled()));
    element.setAttribute(SKIPPED, String.valueOf(isSkipContent()));
    element.setAttribute(SHOW_ALL, String.valueOf(isShowAll()));
    element.setAttribute(ALIAS, getName());
  }

  public static boolean areEqual(@Nullable LogFileOptions options1, @Nullable LogFileOptions options2) {
    if (options1 == null || options2 == null) {
      return options1 == options2;
    }

    return options1.myName.equals(options2.myName) &&
           options1.myPathPattern.equals(options2.myPathPattern) &&
           options1.myLast == options2.myLast &&
           options1.myEnabled == options2.myEnabled &&
           options1.mySkipContent == options2.mySkipContent;

  }
}
