/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;

public class EnvironmentUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.EnvironmentUtil");
  private static Map<String, String> ourEnviromentProperties;

  private EnvironmentUtil() {

  }

  public static Map<String, String> getEnviromentProperties() {
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
      String lineSep = System.getProperty("line.separator");
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
