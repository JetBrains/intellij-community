// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package externalApp;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
class ExternalAppEntryImpl implements ExternalAppEntry {
  private final String[] args;

  ExternalAppEntryImpl(String[] args) {
    this.args = args;
  }

  @Override
  public String[] getArgs() {
    return args;
  }

  @Override
  public Map<String, String> getEnvironment() {
    return System.getenv();
  }

  @Override
  public String getWorkingDirectory() {
    return System.getProperty("user.dir");
  }

  @Override
  public PrintStream getStderr() {
    return System.err;
  }

  @Override
  public PrintStream getStdout() {
    return System.out;
  }

  @Override
  public InputStream getStdin() {
    return System.in;
  }
}
