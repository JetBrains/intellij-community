// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A list of command-line parameters featuring the following:
 * <ul>
 *   <li>special handling for Java properties ({@code -D<name>=<value>})</li>
 *   <li>macro substitution upon addition for plain parameters, and property values</li>
 *   <li>named groups for parameters</li>
 *   <li>parameter strings with quoted parameters</li>
 * </ul>
 *
 * @see ParametersList#defineProperty(String, String)
 * @see ParametersList#expandMacros(String)
 * @see ParametersList#addParamsGroup(String)
 * @see ParametersList#addParametersString(String)
 * @see ParamsGroup
 */
public final class ParametersList implements Cloneable {
  private static final Pattern PROPERTY_PATTERN = Pattern.compile("-D(\\S+?)(?:=(.+))?");
  private static final Pattern MACRO_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

  private final List<String> myParameters = new ArrayList<>();
  private final List<ParamsGroup> myGroups = new SmartList<>();
  private final NotNullLazyValue<Map<String, String>> myMacroMap = NotNullLazyValue.createValue(ParametersList::computeMacroMap);

  public boolean hasParameter(@NotNull String parameter) {
    return myParameters.contains(parameter);
  }

  public boolean hasProperty(@NotNull String propertyName) {
    return getPropertyValue(propertyName) != null;
  }

  @Nullable
  public String getPropertyValue(@NotNull String propertyName) {
    String exact = "-D" + propertyName;
    String prefix = "-D" + propertyName + "=";
    int index = indexOfParameter(o -> o.equals(exact) || o.startsWith(prefix));
    if (index < 0) return null;
    String str = myParameters.get(index);
    return str.length() == exact.length() ? "" : str.substring(prefix.length());
  }

  @NotNull
  public Map<String, String> getProperties() {
    Map<String, String> result = new LinkedHashMap<>();
    JBIterable<Matcher> matchers = JBIterable.from(myParameters).map(PROPERTY_PATTERN::matcher).filter(Matcher::matches);
    for (Matcher matcher : matchers) {
      result.put(matcher.group(1), StringUtil.notNullize(matcher.group(2), ""));
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

    List<String> params = new ArrayList<>(myParameters);
    for (ParamsGroup group : myGroups) {
      params.addAll(group.getParameters());
    }
    return Collections.unmodifiableList(params);
  }

  public void clearAll() {
    myParameters.clear();
    myGroups.clear();
  }

  public void prepend(@NotNull String parameter) {
    addAt(0, parameter);
  }

  public void prependAll(@NotNull String... parameter) {
    addAll(parameter);
    Collections.rotate(myParameters, parameter.length);
  }

  public void addParametersString(@Nullable String parameters) {
    if (StringUtil.isEmptyOrSpaces(parameters)) return;
    for (String param : parse(parameters)) {
      add(param);
    }
  }

  public void add(@Nullable String parameter) {
    if (parameter == null) return;
    myParameters.add(expandMacros(parameter));
  }

  @NotNull
  public ParamsGroup addParamsGroup(@NotNull String groupId) {
    return addParamsGroup(new ParamsGroup(groupId));
  }

  @NotNull
  public ParamsGroup addParamsGroup(@NotNull ParamsGroup group) {
    myGroups.add(group);
    return group;
  }

  @NotNull
  public ParamsGroup addParamsGroupAt(int index, @NotNull ParamsGroup group) {
    myGroups.add(index, group);
    return group;
  }

  @NotNull
  public ParamsGroup addParamsGroupAt(int index, @NotNull String groupId) {
    ParamsGroup group = new ParamsGroup(groupId);
    myGroups.add(index, group);
    return group;
  }

  public int getParamsGroupsCount() {
    return myGroups.size();
  }

  @NotNull
  public List<String> getParameters() {
    return Collections.unmodifiableList(myParameters);
  }

  @NotNull
  public List<ParamsGroup> getParamsGroups() {
    return Collections.unmodifiableList(myGroups);
  }

  @NotNull
  public ParamsGroup getParamsGroupAt(int index) {
    return myGroups.get(index);
  }

  @Nullable
  public ParamsGroup getParamsGroup(@NotNull String name) {
    for (ParamsGroup group : myGroups) {
      if (name.equals(group.getId())) return group;
    }
    return null;
  }

  @Nullable
  public ParamsGroup removeParamsGroup(int index) {
    return myGroups.remove(index);
  }

  public void addAt(int index, @NotNull String parameter) {
    myParameters.add(index, expandMacros(parameter));
  }

  /**
   * Keeps the {@code <propertyName>} property if defined; or defines it with {@code System.getProperty()} as a value if present.
   */
  public void defineSystemProperty(@NotNull String propertyName) {
    defineProperty(propertyName, System.getProperty(propertyName));
  }

  /**
   * Keeps the {@code <propertyName>} property if defined; otherwise appends the new one ignoring null values.
   */
  public void defineProperty(@NotNull String propertyName, @Nullable String propertyValue) {
    if (propertyValue == null) return;
    String exact = "-D" + propertyName;
    String prefix = "-D" + propertyName + "=";
    int index = indexOfParameter(o -> o.equals(exact) || o.startsWith(prefix));
    if (index > -1) return;
    String value = propertyValue.isEmpty() ? exact : prefix + expandMacros(propertyValue);
    myParameters.add(value);
  }

  /**
   * Adds {@code -D<propertyName>} to the list; replaces the value of the last property if defined.
   */
  public void addProperty(@NotNull String propertyName) {
    String exact = "-D" + propertyName;
    String prefix = "-D" + propertyName + "=";
    replaceOrAddAt(exact, myParameters.size(), o -> o.equals(exact) || o.startsWith(prefix));
  }

  /**
   * Adds {@code -D<propertyName>=<propertyValue>} to the list ignoring null values;
   * replaces the value of the last property if defined.
   */
  public void addProperty(@NotNull String propertyName, @Nullable String propertyValue) {
    if (propertyValue == null) return;
    String exact = "-D" + propertyName;
    String prefix = "-D" + propertyName + "=";
    String value = propertyValue.isEmpty() ? exact : prefix + expandMacros(propertyValue);
    replaceOrAddAt(value, myParameters.size(), o -> o.equals(exact) || o.startsWith(prefix));
  }

  /**
   * Adds {@code -D<propertyName>=<propertyValue>} to the list ignoring null, empty and spaces-only values;
   * replaces the value of the last property if defined.
   */
  public void addNotEmptyProperty(@NotNull String propertyName, @Nullable String propertyValue) {
    if (StringUtil.isEmptyOrSpaces(propertyValue)) return;
    addProperty(propertyName, propertyValue);
  }

  /**
   * Replaces the last parameter that starts with the {@code <parameterPrefix>} with {@code <replacement>};
   * otherwise appends {@code <replacement>} to the list.
   */
  public void replaceOrAppend(@NotNull String parameterPrefix, @NotNull String replacement) {
    replaceOrAddAt(expandMacros(replacement), myParameters.size(), o -> o.startsWith(parameterPrefix));
  }

  /**
   * Replaces the last parameter that starts with the {@code <parameterPrefix>} with {@code <replacement>};
   * otherwise prepends this list with {@code <replacement>}.
   */
  public void replaceOrPrepend(@NotNull String parameterPrefix, @NotNull String replacement) {
    replaceOrAddAt(expandMacros(replacement), 0, o -> o.startsWith(parameterPrefix));
  }

  private void replaceOrAddAt(@NotNull String replacement,
                              int position,
                              @NotNull Condition<? super String> existingCondition) {
    int index = indexOfParameter(existingCondition);
    boolean setNewValue = StringUtil.isNotEmpty(replacement);
    if (index > -1 && setNewValue) {
      myParameters.set(index, replacement);
    }
    else if (index > -1) {
      myParameters.remove(index);
    }
    else if (setNewValue) {
      myParameters.add(position, replacement);
    }
  }

  private int indexOfParameter(@NotNull Condition<? super String> condition) {
    return ContainerUtil.lastIndexOf(myParameters, condition);
  }

  public void set(int ind, @NotNull String value) {
    myParameters.set(ind, value);
  }

  public String get(int ind) {
    return myParameters.get(ind);
  }

  @Nullable
  public String getLast() {
    return myParameters.size() > 0 ? myParameters.get(myParameters.size() - 1) : null;
  }

  public void add(@NotNull String name, @NotNull String value) {
    myParameters.add(name); // do not expand macros in parameter name
    add(value);
  }

  public void addAll(@NotNull String... parameters) {
    addAll(Arrays.asList(parameters));
  }

  public void addAll(@NotNull List<String> parameters) {
    // Don't use myParameters.addAll(parameters) , it does not call expandMacros(parameter)
    for (String parameter : parameters) {
      add(parameter);
    }
  }

  /** @noinspection MethodDoesntCallSuperMethod*/
  @Override
  public ParametersList clone() {
    ParametersList clone = new ParametersList();
    clone.myParameters.addAll(myParameters);
    for (ParamsGroup group : myGroups) {
      clone.myGroups.add(group.clone());
    }
    return clone;
  }

  /**
   * @see ParametersListUtil#join(List)
   */
  @NotNull
  public static String join(@NotNull List<String> parameters) {
    return ParametersListUtil.join(parameters);
  }

  /**
   * @see ParametersListUtil#join(List)
   */
  @NotNull
  public static String join(@NotNull String... parameters) {
    return ParametersListUtil.join(parameters);
  }

  /**
   * @see ParametersListUtil#parseToArray(String)
   */
  @NotNull
  public static String[] parse(@NotNull String string) {
    return ParametersListUtil.parseToArray(string);
  }

  @NotNull
  public String expandMacros(@NotNull String text) {
    int start = text.indexOf("${");
    if (start < 0) return text;
    Map<String, String> macroMap = myMacroMap.getValue();
    Matcher matcher = MACRO_PATTERN.matcher(text);
    StringBuilder sb = null;
    while (matcher.find(start)) {
      String value = macroMap.get(matcher.group(1));
      if (value != null) {
        if (sb == null) sb = new StringBuilder(2 * text.length()).append(text, 0, matcher.start());
        else sb.append(text, start, matcher.start());
        sb.append(value);
        start = matcher.end();
      }
      else {
        if (sb != null) sb.append(text, start, matcher.start() + 2);
        start = matcher.start() + 2;
      }
    }
    return sb == null ? text : sb.append(text, start, text.length()).toString();
  }

  private static Map<String, String> ourTestMacros;

  @TestOnly
  public static void setTestMacros(@Nullable Map<String, String> testMacros) {
    ourTestMacros = testMacros;
  }

  @NotNull
  private static Map<String, String> computeMacroMap() {
    // ApplicationManager.getApplication() will return null if executed in ParameterListTest
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode() && ourTestMacros != null) {
      return ObjectUtils.notNull(ourTestMacros, Collections.emptyMap());
    }
    Map<String, String> map = ContainerUtil.newTroveMap(CaseInsensitiveStringHashingStrategy.INSTANCE);
    PathMacros pathMacros = PathMacros.getInstance();
    if (pathMacros != null) {
      for (String name : pathMacros.getUserMacroNames()) {
        ContainerUtil.putIfNotNull(name, pathMacros.getValue(name), map);
      }
    }
    Map<String, String> env = EnvironmentUtil.getEnvironmentMap();
    for (String name : env.keySet()) {
      ContainerUtil.putIfAbsent(name, env.get(name), map);
    }
    return map;
  }

  @Override
  public String toString() {
    return myParameters + (myGroups.isEmpty() ? "" : " and " + myGroups);
  }
}