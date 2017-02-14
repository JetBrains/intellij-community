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
public class CapturePoint implements Cloneable {
  @Attribute("enabled")
  public boolean myEnabled = true;

  @Attribute("class-name")
  public String myClassName;

  @Attribute("method-name")
  public String myMethodName;

  @Attribute("capture-key-expression")
  public String myCaptureKeyExpression;

  @Attribute("insert-class-name")
  public String myInsertClassName;

  @Attribute("insert-method-name")
  public String myInsertMethodName;

  @Attribute("insert-key-expression")
  public String myInsertKeyExpression;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CapturePoint that = (CapturePoint)o;

    if (myEnabled != that.myEnabled) return false;
    if (myClassName != null ? !myClassName.equals(that.myClassName) : that.myClassName != null) return false;
    if (myMethodName != null ? !myMethodName.equals(that.myMethodName) : that.myMethodName != null) return false;
    if (myCaptureKeyExpression != null
        ? !myCaptureKeyExpression.equals(that.myCaptureKeyExpression)
        : that.myCaptureKeyExpression != null) {
      return false;
    }
    if (myInsertClassName != null ? !myInsertClassName.equals(that.myInsertClassName) : that.myInsertClassName != null) return false;
    if (myInsertMethodName != null ? !myInsertMethodName.equals(that.myInsertMethodName) : that.myInsertMethodName != null) return false;
    if (myInsertKeyExpression != null ? !myInsertKeyExpression.equals(that.myInsertKeyExpression) : that.myInsertKeyExpression != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myEnabled ? 1 : 0);
    result = 31 * result + (myClassName != null ? myClassName.hashCode() : 0);
    result = 31 * result + (myMethodName != null ? myMethodName.hashCode() : 0);
    result = 31 * result + (myCaptureKeyExpression != null ? myCaptureKeyExpression.hashCode() : 0);
    result = 31 * result + (myInsertClassName != null ? myInsertClassName.hashCode() : 0);
    result = 31 * result + (myInsertMethodName != null ? myInsertMethodName.hashCode() : 0);
    result = 31 * result + (myInsertKeyExpression != null ? myInsertKeyExpression.hashCode() : 0);
    return result;
  }

  @Override
  public CapturePoint clone() throws CloneNotSupportedException {
    return (CapturePoint)super.clone();
  }
}
