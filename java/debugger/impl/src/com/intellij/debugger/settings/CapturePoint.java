/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.settings;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author egor
 */
@Tag("capture-point")
public class CapturePoint {
  @Attribute("enabled")
  public boolean myEnabled = true;

  @Attribute("class-name")
  public String myClassName;

  @Attribute("method-name")
  public String myMethodName;

  @Attribute("param-idx")
  public int myParamNo = 0;

  @Attribute("insert-class-name")
  public String myInsertClassName;

  @Attribute("insert-method-name")
  public String myInsertMethodName;

  @Attribute("insert-key-expression")
  public String myInsertKeyExpression;
}
