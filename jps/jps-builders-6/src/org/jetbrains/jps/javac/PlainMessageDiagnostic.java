/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.Nls;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Locale;

/**
 * @author Eugene Zhuravlev
 */
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
