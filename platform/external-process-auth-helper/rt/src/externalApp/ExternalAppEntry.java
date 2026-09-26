// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package externalApp;

import org.jetbrains.annotations.ApiStatus;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;

@ApiStatus.Experimental
public interface ExternalAppEntry {
  String[] getArgs();
  Map<String, String> getEnvironment();
  String getWorkingDirectory();
  PrintStream getStderr();
  PrintStream getStdout();
  InputStream getStdin();
  Path getExecutablePath();

  static ExternalAppEntry fromMain(String[] args) {
    return new ExternalAppEntryImpl(args, ExternalApp.class);
  }

  static ExternalAppEntry fromMain(String[] args, Class<? extends ExternalApp> thisClass) {
    return new ExternalAppEntryImpl(args, thisClass);
  }
}
