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
package org.jetbrains.jps.backwardRefs;

import com.sun.tools.javac.util.Convert;
import org.jetbrains.annotations.NotNull;

public abstract class CompilerElement {
  public static final CompilerElement[] EMPTY_ARRAY = new CompilerElement[0];

  @NotNull
  public abstract LightUsage asUsage(ByteArrayEnumerator byteArrayEnumerator);

  public static class CompilerMethod extends CompilerElement {
    private final byte[] myJavacClassName;
    private final byte[] myJavacMethodName;
    private final int myJavacParameterCount;

    public CompilerMethod(String javacClassName, String javacMethodName, int count) {
      this(bytes(javacClassName), bytes(javacMethodName), count);
    }

    CompilerMethod(byte[] javacClassName, byte[] javacMethodName, int javacParameterCount) {
      myJavacClassName = javacClassName;
      myJavacMethodName = javacMethodName;
      myJavacParameterCount = javacParameterCount;
    }

    @NotNull
    @Override
    public LightUsage asUsage(ByteArrayEnumerator byteArrayEnumerator) {
      return new LightUsage.LightMethodUsage(byteArrayEnumerator.enumerate(myJavacClassName),
                                             byteArrayEnumerator.enumerate(myJavacMethodName),
                                             myJavacParameterCount);
    }
  }

  public static class CompilerClass extends CompilerElement {
    private final byte[] myJavacName;

    public CompilerClass(String name) {
      this(bytes(name));
    }

    CompilerClass(byte[] javacName) {
      myJavacName = javacName;
    }

    @NotNull
    @Override
    public LightUsage asUsage(ByteArrayEnumerator byteArrayEnumerator) {
      return new LightUsage.LightClassUsage(byteArrayEnumerator.enumerate(myJavacName));
    }
  }

  public static class CompilerField extends CompilerElement {
    private final byte[] myJavacClassName;
    private final byte[] myJavacName;

    public CompilerField(String javacClassName, String name) {
      this(bytes(javacClassName), bytes(name));
    }

    CompilerField(byte[] javacClassName, byte[] javacName) {
      myJavacClassName = javacClassName;
      myJavacName = javacName;
    }

    @NotNull
    @Override
    public LightUsage asUsage(ByteArrayEnumerator byteArrayEnumerator) {
      return new LightUsage.LightFieldUsage(byteArrayEnumerator.enumerate(myJavacClassName),
                                            byteArrayEnumerator.enumerate(myJavacName));
    }
  }

  static byte[] bytes(String str) {
    //TODO
    return Convert.string2utf(str);
  }
}
