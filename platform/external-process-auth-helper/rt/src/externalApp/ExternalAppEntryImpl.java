// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package externalApp;

import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
class ExternalAppEntryImpl implements ExternalAppEntry {
  private final String[] args;
  private final Class<? extends ExternalApp> externalAppClass;

  ExternalAppEntryImpl(String[] args, Class<? extends ExternalApp> externalAppClass) {
    this.args = args;
    this.externalAppClass = externalAppClass;
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

  @Override
  public Path getExecutablePath() {
    try {
      return Paths.get(this.externalAppClass.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
    catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
