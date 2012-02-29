/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParametersList implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.ParametersList");

  private static final Pattern PROPERTY_PATTERN = Pattern.compile("-D(\\S+?)=(.+)");

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
  public String getPropertyValue(@NotNull @NonNls final String name) {
    final String prefix = "-D" + name + "=";
    for (String parameter : myParameters) {
      if (parameter.startsWith(prefix)) {
        return parameter.substring(prefix.length());
      }
    }
    return null;
  }

  @NotNull
  public Map<String, String> getProperties() {
    Map<String, String> result = new THashMap<String, String>();
    for (String parameter : myParameters) {
      Matcher matcher = PROPERTY_PATTERN.matcher(parameter);
      if (matcher.matches()) {
        result.put(matcher.group(1), matcher.group(2));
      }
    }
    return result;
  }

  @NotNull
  public String getParametersString() {
    return join(getList());
  }

  @NotNull
  public String[] getArray() {
    return ArrayUtil.toStringArray(getList());
  }

  @NotNull
  public List<String> getList() {
    if (myGroups.isEmpty()) {
      return Collections.unmodifiableList(myParameters);
    }

    final List<String> params = new ArrayList<String>();
    params.addAll(myParameters);
    for (ParamsGroup group : myGroups) {
      params.addAll(group.getParameters());
    }
    return Collections.unmodifiableList(params);
  }

  public void clearAll() {
    myParameters.clear();
    myGroups.clear();
  }

  public void prepend(@NonNls final String parameter) {
    addAt(0, parameter);
  }

  public void prependAll(@NonNls final String... parameter) {
    for (int i = parameter.length - 1; i >= 0; i--) {
      addAt(0, parameter[i]);
    }
  }

  public void addParametersString(final String parameters) {
    if (parameters != null) {
      final String[] split = parse(parameters);
      for (String param : split) {
        add(param);
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

  public ParamsGroup addParamsGroupAt(final int index, @NotNull final ParamsGroup group) {
    myGroups.add(index, group);
    return group;
  }

  public ParamsGroup addParamsGroupAt(final int index, @NotNull final String groupId) {
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
    addProperty(propertyName, propertyValue);
  }

  public void addProperty(@NonNls final String propertyName, @NonNls final String propertyValue) {
    //noinspection HardCodedStringLiteral
    myParameters.add("-D" + propertyName + "=" + propertyValue);
  }

  public void replaceOrAppend(final @NonNls String parameterPrefix, final @NonNls String replacement) {
    replaceOrAdd(parameterPrefix, replacement, myParameters.size());
  }

  private void replaceOrAdd(final @NonNls String parameterPrefix, final @NonNls String replacement, final int position) {
    for (ListIterator<String> iterator = myParameters.listIterator(); iterator.hasNext(); ) {
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
    if (!"".equals(replacement)) {
      myParameters.add(position, replacement);
    }
  }

  public void replaceOrPrepend(final @NonNls String parameter, final @NonNls String replacement) {
    replaceOrAdd(parameter, replacement, 0);
  }
  
  public void set(int ind, final @NonNls String value) {
    myParameters.set(ind, value);
  }

  public String get(int ind) {
    return myParameters.get(ind);
  }

  public void add(@NonNls final String name, @NonNls final String value) {
    add(name);
    add(value);
  }

  public void addAll(final String... parameters) {
    ContainerUtil.addAll(myParameters, parameters);
  }

  public void addAll(final List<String> parameters) {
    myParameters.addAll(parameters);
  }

  @Override
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

  /**
   * <p>Joins list of parameters into single string, which may be then parsed back into list by {@link #parse(String)}.</p>
   * <p/>
   * <p>
   * <strong>Conversion rules:</strong>
   * <ul>
   * <li>double quotes are escaped by backslash (<code>&#92;</code>);</li>
   * <li>empty parameters parameters and parameters with spaces inside are surrounded with double quotes (<code>"</code>);</li>
   * <li>parameters are separated by single whitespace.</li>
   * </ul>
   * </p>
   * <p/>
   * <p><strong>Examples:</strong></p>
   * <p>
   * <code>['a', 'b'] => 'a  b'</code><br/>
   * <code>['a="1 2"', 'b'] => '"a &#92;"1 2&#92;"" b'</code>
   * </p>
   *
   * @param parameters a list of parameters to join.
   * @return a string with parameters.
   */
  @NotNull
  public static String join(@NotNull final List<String> parameters) {
    return ParametersTokenizer.encode(parameters);
  }

  @NotNull
  public static String join(final String... parameters) {
    return ParametersTokenizer.encode(Arrays.asList(parameters));
  }

  /**
   * <p>Converts single parameter string (as created by {@link #join(java.util.List)}) into list of parameters.</p>
   * <p/>
   * <p>
   * <strong>Conversion rules:</strong>
   * <ul>
   * <li>starting/whitespaces are trimmed;</li>
   * <li>parameters are split by whitespaces, whitespaces itself are dropped</li>
   * <li>parameters inside double quotes (<code>"a b"</code>) are kept as single one;</li>
   * <li>double quotes are dropped, escaped double quotes (<code>&#92;"</code>) are un-escaped.</li>
   * </ul>
   * </p>
   * <p/>
   * <p><strong>Examples:</strong></p>
   * <p>
   * <code>' a  b ' => ['a', 'b']</code><br/>
   * <code>'a="1 2" b' => ['a=1 2', 'b']</code><br/>
   * <code>'a " " b' => ['a', ' ', 'b']</code><br/>
   * <code>'"a &#92;"1 2&#92;"" b' => ['a="1 2"', 'b']</code>
   * </p>
   *
   * @param string parameter string to split.
   * @return array of parameters.
   */
  @NotNull
  public static String[] parse(@NotNull final String string) {
    final List<String> params = ParametersTokenizer.decode(string);
    return ArrayUtil.toStringArray(params);
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
        final PathMacros pathMacros = PathMacros.getInstance();
        final Set<String> names = pathMacros.getAllMacroNames();
        for (String name : names) {
          myMacroMap.put("${" + name + "}", pathMacros.getValue(name));
        }
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

  @Override
  public String toString() {
    return myParameters.toString();
  }

  private static class ParametersTokenizer {
    private ParametersTokenizer() {
    }

    @NotNull
    public static String encode(@NotNull final List<String> parameters) {
      final StringBuilder buffer = new StringBuilder();
      for (final String parameter : parameters) {
        if (buffer.length() > 0) {
          buffer.append(' ');
        }
        buffer.append(encode(parameter));
      }
      return buffer.toString();
    }

    @NotNull
    public static String encode(@NotNull String parameter) {
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        builder.append(parameter);
        StringUtil.escapeQuotes(builder);
        if (builder.length() == 0 || StringUtil.indexOf(builder, ' ') >= 0 || StringUtil.indexOf(builder, '|') >= 0) {
          StringUtil.quote(builder);
        }
        return builder.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }

    @NotNull
    public static List<String> decode(@NotNull String parameterString) {
      parameterString = parameterString.trim();

      final ArrayList<String> params = CollectionFactory.arrayList();
      final StringBuilder token = new StringBuilder(128);
      boolean inQuotes = false;
      boolean escapedQuote = false;
      boolean nonEmpty = false;

      for (int i = 0; i < parameterString.length(); i++) {
        final char ch = parameterString.charAt(i);

        if (ch == '\"') {
          if (!escapedQuote) {
            inQuotes = !inQuotes;
            nonEmpty = true;
            continue;
          }
          escapedQuote = false;
        }
        else if (Character.isWhitespace(ch)) {
          if (!inQuotes) {
            if (token.length() > 0 || nonEmpty) {
              params.add(token.toString());
              token.setLength(0);
              nonEmpty = false;
            }
            continue;
          }
        }
        else if (ch == '\\') {
          if (i < parameterString.length() - 1 && parameterString.charAt(i + 1) == '"') {
            escapedQuote = true;
            continue;
          }
        }

        token.append(ch);
      }

      if (token.length() > 0 || nonEmpty) {
        params.add(token.toString());
      }

      return params;
    }
  }
}
