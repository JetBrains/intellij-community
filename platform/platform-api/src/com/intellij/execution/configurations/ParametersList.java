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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ParametersList implements Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.ParametersList");
  private List<String> myParameters = new ArrayList<String>();
  private Map<String, String> myMacroMap = null;
  private List<ParamsGroup> myGroups = new ArrayList<ParamsGroup>();

  public boolean hasParameter(@NonNls final String param) {
    return myParameters.contains(param);
  }

  public boolean hasProperty(@NonNls final String name) {
    for (@NonNls String parameter : myParameters) {
      if (StringUtil.startsWithConcatenationOf(parameter, "-D" + name, "=")) return true;
    }
    return false;
  }

  @Nullable
  public String getPropertyValue(@NonNls final String name) {
    for (String parameter : myParameters) {
      @NonNls String prefix = "-D" + name + "=";
      if (parameter.startsWith(prefix)) {
        return parameter.substring(prefix.length());
      }
    }
    return null;
  }

  public String getParametersString() {
    final StringBuilder buffer = new StringBuilder();
    final String separator = " ";
    for (final String param : myParameters) {
      buffer.append(separator);
      buffer.append(GeneralCommandLine.quote(param));
    }
    for (ParamsGroup paramsGroup : myGroups) {
      // params group parameters string already contains a separator
      buffer.append(paramsGroup.getParametersList().getParametersString());
    }
    return buffer.toString();
  }

  public String[] getArray() {
    return ArrayUtil.toStringArray(getList());
  }

  public void addParametersString(final String parameters) {
    if (parameters != null) {
      final String[] parms = parse(parameters);
      for (String parm : parms) {
        add(parm);
      }
    }
  }

  public void add(@NonNls final String parameter) {
    myParameters.add(expandMacros(parameter));
  }

  public ParamsGroup addParamsGroup(@NotNull final String groupId) {
    return addParamsGroup(new ParamsGroup(groupId));
  }

  public ParamsGroup addParamsGroup(@NotNull final ParamsGroup group) {
    myGroups.add(group);
    return group;
  }

  public ParamsGroup addParamsGroupAt(final int index,
                                      @NotNull final String groupId) {
    final ParamsGroup group = new ParamsGroup(groupId);
    myGroups.add(index, group);
    return group;
  }

  public int getParamsGroupsCount() {
    return myGroups.size();
  }

  public List<String> getParameters() {
    return Collections.unmodifiableList(myParameters);
  }

  public List<ParamsGroup> getParamsGroups() {
    return Collections.unmodifiableList(myGroups);
  }

  public ParamsGroup getParamsGroupAt(final int index) {
    return myGroups.get(index);
  }

  @Nullable
  public ParamsGroup getParamsGroup(@NotNull final String name) {
    for (ParamsGroup group : myGroups) {
      if (name.equals(group.getId())) return group;
    }
    return null;
  }

  public ParamsGroup removeParamsGroup(final int index) {
    return myGroups.remove(index);
  }

  public void addAt(final int index, @NotNull final String parameter) {
    myParameters.add(index, expandMacros(parameter));
  }

  public void defineProperty(@NonNls final String propertyName, @NonNls final String propertyValue) {
    //noinspection HardCodedStringLiteral
    myParameters.add("-D" + propertyName + "=" + propertyValue);
  }

  public void replaceOrAppend(final @NonNls String parameterPrefix, final @NonNls String replacement) {
    replaceOrAdd(parameterPrefix, replacement, myParameters.size());
  }

  private void replaceOrAdd(final @NonNls String parameterPrefix, final @NonNls String replacement, final int position) {
    for (ListIterator<String> iterator = myParameters.listIterator(); iterator.hasNext();) {
      final String param = iterator.next();
      if (param.startsWith(parameterPrefix)) {
        if ("".equals(replacement)) {
          iterator.remove();
        }
        else {
          iterator.set(replacement);
        }
        return;
      }
    }
    if(!"".equals(replacement)) {
      myParameters.add(position, replacement);
    }
  }

  public void replaceOrPrepend(final @NonNls String parameter, final @NonNls String replacement) {
    replaceOrAdd(parameter, replacement, 0);
  }

  public List<String> getList() {
    if (myGroups.isEmpty()) {
      return Collections.unmodifiableList(myParameters);
    }

    final List<String> params = new ArrayList<String>();

    // params
    params.addAll(myParameters);

    // recursively add groups
    for (ParamsGroup group : myGroups) {
      params.addAll(group.getParameters());
    }
    return Collections.unmodifiableList(params);
  }

  public void prepend(@NonNls final String parameter) {
    addAt(0, parameter);
  }

  public void add(@NonNls final String name,@NonNls  final String value) {
    add(name);
    add(value);
  }

  public void addAll(final String[] parameters) {
    ContainerUtil.addAll(myParameters, parameters);
  }

  public void addAll(final List<String> parameters) {
    myParameters.addAll(parameters);
  }

  public ParametersList clone() {
    try {
      final ParametersList clone = (ParametersList)super.clone();
      clone.myParameters = new ArrayList<String>(myParameters);
      clone.myGroups = new ArrayList<ParamsGroup>(myGroups.size() + 1);
      for (ParamsGroup group : myGroups) {
        clone.myGroups.add(group.clone());
      }
      return clone;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  public static String[] parse(final String string){
    return new ParametersTokenizer(string).execute();
  }


  public String expandMacros(String text) {
    final Map<String, String> macroMap = getMacroMap();
      final Set<String> set = macroMap.keySet();
      for (final String from : set) {
          final String to = macroMap.get(from);
          text = StringUtil.replace(text, from, to, true);
      }
    return text;
  }

  private Map<String, String> getMacroMap() {
    if (myMacroMap == null) {
      // the insertion order is important for later iterations, so LinkedHashMap is used
      myMacroMap = new LinkedHashMap<String, String>();

      // ApplicationManager.getApplication() will return null if executed in ParameterListTest
      final Application application = ApplicationManager.getApplication();
      if (application != null) {
        application.runReadAction(new Runnable() {
          public void run() {
            final PathMacros pathMacros = PathMacros.getInstance();
            final Set<String> names = pathMacros.getAllMacroNames();
              for (String name : names) {
                  myMacroMap.put("${" + name + "}", pathMacros.getValue(name));
              }
          }
        });
        final Map<String, String> env = EnvironmentUtil.getEnviromentProperties();
        for (String name : env.keySet()) {
          final String key = "${" + name + "}";
          if (!myMacroMap.containsKey(key)) {
            myMacroMap.put(key, env.get(name));
          }
        }
      }
    }
    return myMacroMap;
  }

  private static class ParametersTokenizer {
    private final String myParamsString;
    private final List<String> myArray = new ArrayList<String>();
    private final StringBuffer myBuffer = new StringBuffer(128);
    private boolean myTokenStarted = false;
    private boolean myUnquotedSlash = false;

    public ParametersTokenizer(final String parmsString) {
      myParamsString = parmsString;
    }

    public String[] execute() {
      boolean inQuotes = false;

      // \" sequence is turned to " inside ""
      boolean wasEscaped = false;

      for (int i = 0; i < myParamsString.length(); i++) {
        final char c = myParamsString.charAt(i);

        if (inQuotes) {
          LOG.assertTrue(!myUnquotedSlash);
          if (wasEscaped) {
            //if (c != '"') append('\\');
            append(c);
            wasEscaped = false;
          }
          else if (c == '"') {
            inQuotes = false;
          }
          else if (c == '\\') {
            myTokenStarted = true;
            append(c);
            wasEscaped = true;
          }
          else {
            append(c);
          }
        }
        else {
          inQuotes = processNotQuoted(c);
        }
      }
      tokenFinished();
      return ArrayUtil.toStringArray(myArray);
    }

    private boolean processNotQuoted(final char c) {
      if (c == '"') {
        if (myUnquotedSlash) {
          append(c);
          myUnquotedSlash = false;
          return false;
        }
        myTokenStarted = true;
        return true;
      }
      else if (c == ' ') {
        tokenFinished();
      }
      else if (c == '\\') {
        myUnquotedSlash = true;
        append(c);
        return false;
      }
      else {
        append(c);
      }
      myUnquotedSlash = false;
      return false;
    }

    private void append(final char nextChar) {
      myBuffer.append(nextChar);
      myTokenStarted = true;
    }

    private void tokenFinished() {
      if (myTokenStarted) {
        final String token = myBuffer.length() == 0 ? "\"\"" : myBuffer.toString();
        myArray.add(token);
      }
      myBuffer.setLength(0);
      myTokenStarted = false;
    }
  }
}
