/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.compiler;

public abstract class CompilerElement {
  public static final CompilerElement[] EMPTY_ARRAY = new CompilerElement[0];

  public abstract CompilerElement override(String overriderName);

  public static class CompilerMethod extends CompilerElement {
    private final String myJavacClassName;
    private final String myJavacMethodName;
    private final int myJavacParameterCount;

    CompilerMethod(String javacClassName, String javacMethodName, int javacParameterCount) {
      myJavacClassName = javacClassName;
      myJavacMethodName = javacMethodName;
      myJavacParameterCount = javacParameterCount;
    }

    public String getJavacClassName() {
      return myJavacClassName;
    }

    public String getJavacMethodName() {
      return myJavacMethodName;
    }

    public int getJavacParameterCount() {
      return myJavacParameterCount;
    }

    @Override
    public CompilerElement override(String overriderName) {
      return new CompilerMethod(overriderName, myJavacMethodName, myJavacParameterCount);
    }
  }

  public static class CompilerClass extends CompilerElement {
    private final String myJavacName;

    CompilerClass(String javacName) {
      myJavacName = javacName;
    }

    public String getJavacName() {
      return myJavacName;
    }

    @Override
    public CompilerElement override(String overriderName) {
      return new CompilerClass(myJavacName);
    }
  }

  public static class CompilerField extends CompilerElement {
    private final String myJavacClassName;
    private final String myJavacName;

    CompilerField(String javacClassName, String javacName) {
      myJavacClassName = javacClassName;
      myJavacName = javacName;
    }

    public String getJavacClassName() {
      return myJavacClassName;
    }

    public String getJavacName() {
      return myJavacName;
    }

    @Override
    public CompilerElement override(String overriderName) {
      return new CompilerField(overriderName, myJavacName);
    }
  }

  public static class CompilerFunExpr extends CompilerElement {
    private final String myJavacClassName;

    CompilerFunExpr(String javacClassName) {
      myJavacClassName = javacClassName;
    }

    public String getJavacClassName() {
      return myJavacClassName;
    }

    @Override
    public CompilerElement override(String overriderName) {
      throw new UnsupportedOperationException();
    }
  }
}
