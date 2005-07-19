/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.compiler;

/**
 * A set of constants describing possible message categories
 */
public interface CompilerMessageCategory {
  CompilerMessageCategory ERROR = new CompilerMessageCategory() {
    public String toString() {
      return "ERROR";
    }

    public String getPresentableText() {
      return toString();
    }
  };
  CompilerMessageCategory WARNING = new CompilerMessageCategory() {
    public String toString() {
      return "WARNING";
    }
    public String getPresentableText() {
      return toString();
    }
  };
  CompilerMessageCategory INFORMATION = new CompilerMessageCategory() {
    public String toString() {
      return "INFORMATION";
    }
    public String getPresentableText() {
      return toString();
    }
  };
  CompilerMessageCategory STATISTICS = new CompilerMessageCategory() {
    public String toString() {
      return "STATISTICS";
    }
    public String getPresentableText() {
      return toString();
    }
  };

  public String getPresentableText();
}
