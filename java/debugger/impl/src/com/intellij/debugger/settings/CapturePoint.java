/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.settings;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.Objects;

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
    return Objects.hash(myEnabled,
                        myClassName, myMethodName, myCaptureKeyExpression,
                        myInsertClassName, myInsertMethodName, myInsertKeyExpression);
  }

  @Override
  public CapturePoint clone() throws CloneNotSupportedException {
    return (CapturePoint)super.clone();
  }
}
