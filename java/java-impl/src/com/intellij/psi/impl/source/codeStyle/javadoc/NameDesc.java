/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle.javadoc;

/**
*
* @author Dmitry Skavish
*/
public class NameDesc {

  public String name;
  public String desc;
  private String type;

  public NameDesc(String name, String desc) {
    this.name = name;
    this.desc = desc;
  }

  public NameDesc(String name, String desc, String type) {
    this.name = name;
    this.desc = desc;
    this.type = type;
  }

  public String toString() {
    if (type == null) return name;
    return name + ": " + type;
  }
}
