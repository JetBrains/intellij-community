// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.Nls;

import javax.tools.*;
import java.util.Locale;

public class PlainMessageDiagnostic implements Diagnostic<JavaFileObject>{
  private final Kind myKind;
  @Nls
  private final String myMessage;

  public PlainMessageDiagnostic(Kind kind, @Nls String message) {
    myKind = kind;
    myMessage = message;
  }

  @Override
  public Kind getKind() {
    return myKind;
  }

  @Override
  public JavaFileObject getSource() {
    return null;
  }

  @Override
  public long getPosition() {
    return 0;
  }

  @Override
  public long getStartPosition() {
    return 0;
  }

  @Override
  public long getEndPosition() {
    return 0;
  }

  @Override
  public long getLineNumber() {
    return 0;
  }

  @Override
  public long getColumnNumber() {
    return 0;
  }

  @Override
  public String getCode() {
    return null;
  }

  @Override
  public String getMessage(Locale locale) {
    return myMessage;
  }
}
