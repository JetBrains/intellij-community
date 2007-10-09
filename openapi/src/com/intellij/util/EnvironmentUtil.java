/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;

import org.jetbrains.annotations.NonNls;

public class EnvironmentUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.EnvironmentUtil");
  private static Map<String, String> ourEnviromentProperties;

  private EnvironmentUtil() {

  }

  public static @NonNls Map<String, String> getEnviromentProperties() {
    if (ourEnviromentProperties == null) {
      List vars = getProcEnvironment();
      ourEnviromentProperties = new HashMap<String, String>();
      if (vars != null) {
        for (Iterator i = vars.iterator(); i.hasNext();) {
          String entry = (String)i.next();
          int pos = entry.indexOf('=');
          if (pos != -1) {
            String prop = entry.substring(0, pos);
            String val = entry.substring(pos + 1);
            //if (SystemInfo.isWindows) prop = prop.toLowerCase();
            ourEnviromentProperties.put(prop, val);
          }
        }
      }
    }

    return ourEnviromentProperties;
  }

  public static String[] getFlattenEnvironmentProperties() {
    ArrayList<String> result = new ArrayList<String>();
    Map<String, String> enviromentProperties = getEnviromentProperties();
    if (enviromentProperties != null) {
      for (Iterator iterator = enviromentProperties.keySet().iterator(); iterator.hasNext();) {
        String envName = (String)iterator.next();
        result.add(envName + "=" + enviromentProperties.get(envName));
      }
    }
    return result.toArray(new String[result.size()]);
  }

  private static synchronized List getProcEnvironment() {
    List procEnvironment = new ArrayList();

    try {
      String[] procEnvCommand = getProcEnvCommand();
      Process process = Runtime.getRuntime().exec(procEnvCommand);
      if (process == null) return null;
      InputStream processIn = process.getInputStream();

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      while (true) {
        int b = processIn.read();
        if (b < 0) break;
        out.write(b);
      }
      int exitCode = process.waitFor();
      if (exitCode < 0) return null;
//      process.destroy();

      BufferedReader in = new BufferedReader(new StringReader(out.toString()));

      String var = null;
      String line;
      String lineSep = SystemProperties.getLineSeparator();
      while ((line = in.readLine()) != null) {
        if (line.indexOf('=') == -1) {
          if (var == null) {
            var = lineSep + line;
          }
          else {
            var += lineSep + line;
          }
        }
        else {
          if (var != null) {
            procEnvironment.add(var);
          }
          var = line;
        }
      }
      if (var != null) {
        procEnvironment.add(var);
      }
    }
    catch (Throwable exc) {
      LOG.debug(exc);
    }

    return procEnvironment;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String[] getProcEnvCommand() {
    if (SystemInfo.isOS2) {
      String[] cmd = {"cmd", "/c", "set"};
      return cmd;
    }
    else if (SystemInfo.isWindows) {
      if (!SystemInfo.isWindows9x) {
        String[] cmd = {"cmd", "/c", "set"};
        return cmd;
      }
      else {
        String[] cmd = {"command.com", "/c", "set"};
        return cmd;
      }
    }
    else if (SystemInfo.isUnix) {
      String[] cmd = {"/usr/bin/env"};
      return cmd;
    }

    return null;
  }

  public static String[] getEnvironment() {
    Map enviroment = getEnviromentProperties();
    String[] envp = new String[enviroment.size()];
    int i = 0;
    for (Iterator iterator = enviroment.keySet().iterator(); iterator.hasNext();) {
      String name = (String)iterator.next();
      String value = (String)enviroment.get(name);
      envp[i++] = name + "=" + value;
    }
    return envp;
  }
}
