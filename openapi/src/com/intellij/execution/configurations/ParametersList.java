/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.util.EnvironmentUtil;

import java.util.*;

public class ParametersList implements Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.ParametersList");
  private List<String> myParameters = new ArrayList<String>();
  private Map<String, String> myMacroMap = null;

  public ParametersList() {

  }

  public boolean hasParameter(final String param) {
    return myParameters.contains(param);
  }

  public String getParametersString() {
    final ArrayList<String> quoted = quoteParameters(myParameters);
    final StringBuffer buffer = new StringBuffer();
    final String separator = " ";
    for (Iterator<String> iterator = quoted.iterator(); iterator.hasNext();) {
      final String param = iterator.next();
      buffer.append(separator);
      buffer.append(param);
    }
    return buffer.toString();
  }

  private static ArrayList<String> quoteParameters(final List<String> parameters) {
    final ArrayList<String> result = new ArrayList<String>(parameters.size());
    for (Iterator iterator = parameters.iterator(); iterator.hasNext();) {
      String parameter = (String)iterator.next();
      if(hasWhitespace(parameter) && !(StringUtil.startsWithChar(parameter, '\"') && StringUtil.endsWithChar(parameter, '\"'))) {
        parameter = "\"" + parameter + "\"";
      }
      result.add(parameter);
    }
    return result;
  }

  private static boolean hasWhitespace(final String string) {
    final int length = string.length();
    for (int i = 0; i < length; i++) {
      if (Character.isWhitespace(string.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  public String[] getArray() {
    return myParameters.toArray(new String[myParameters.size()]);
  }

  public void addParametersString(final String parameters) {
    if (parameters != null) {
      final String[] parms = parse(parameters);
      for (int i = 0; i < parms.length; i++) {
        add(parms[i]);
      }
    }
  }

  public void add(final String parameter) {
    myParameters.add(expandMacros(parameter));
  }

  public void addAt(final int index, final String parameter) {
    LOG.assertTrue(parameter != null);
    myParameters.add(index, expandMacros(parameter));
  }

  public void defineProperty(final String propertyName, final String propertyValue) {
    //noinspection HardCodedStringLiteral
    myParameters.add("-D" + propertyName + "=" + propertyValue);
  }
  
  public void replaceOrAppend(final String parameterPrefix, final String replacement) {
    replaceOrAdd(parameterPrefix, replacement, myParameters.size());
  }

  private void replaceOrAdd(final String parameterPrefix, final String replacement, final int position) {
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

  public void replaceOrPrepend(final String parameter, final String replacement) {
    replaceOrAdd(parameter, replacement, 0);
  }

  public List<String> getList() {
    return Collections.unmodifiableList(myParameters);
  }

  public void prepend(final String parameter) {
    addAt(0, parameter);
  }

  public void add(final String name, final String value) {
    add(name);
    add(value);
  }

  public void addAll(final String[] parameters) {
    myParameters.addAll(Arrays.asList(parameters));
  }

  public void addAll(final List<String> parameters) {
    myParameters.addAll(parameters);
  }

  public ParametersList clone() {
    try {
      final ParametersList clone = (ParametersList)super.clone();
      clone.myParameters = new ArrayList<String>(myParameters);
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
    final Set set = macroMap.keySet();
    for (Iterator i = set.iterator(); i.hasNext();) {
      final String from = (String)i.next();
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
            for (Iterator it = names.iterator(); it.hasNext();) {
              final String name = (String)it.next();
              myMacroMap.put("${" + name + "}", pathMacros.getValue(name));
            }
          }
        });
        final Map<String, String> env = EnvironmentUtil.getEnviromentProperties();
        for (Iterator it = env.keySet().iterator(); it.hasNext();) {
          final String name = (String)it.next();
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
      return myArray.toArray(new String[myArray.size()]);
    }

    private boolean processNotQuoted(final char c) {
      if (c == '"') {
        if (myUnquotedSlash) {
          append(c);
          myUnquotedSlash = false;
          return false;
        }
        else {
          myTokenStarted = true;
        }
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
