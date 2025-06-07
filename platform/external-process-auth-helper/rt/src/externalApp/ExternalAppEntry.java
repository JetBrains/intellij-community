// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package externalApp;

import java.io.PrintStream;
import java.util.Map;

public interface ExternalAppEntry {
  String[] getArgs();
  Map<String, String> getEnvironment();
  String getWorkingDirectory();
  PrintStream getStderr();
  PrintStream getStdout();

  static ExternalAppEntry fromMain(String[] args) {
    return new ExternalAppEntryImpl(args);
  }
}
