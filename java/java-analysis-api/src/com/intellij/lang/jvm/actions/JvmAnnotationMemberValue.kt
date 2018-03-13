// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmAnnotationMemberValue
import com.intellij.lang.jvm.JvmAnnotationNameValuePair
import com.intellij.lang.jvm.JvmPrimitiveLiteral
import com.intellij.lang.jvm.JvmStringLiteral


class SimpleNamedAttribute(val attributeName: String, val memberValue: JvmAnnotationMemberValue) : JvmAnnotationNameValuePair {

  override fun getName(): String = attributeName

  override fun getValue(): JvmAnnotationMemberValue = memberValue

}

fun stringAttribute(name: String, value: String) = SimpleNamedAttribute(name, JvmStringLiteral { value })
fun intAttribute(name: String, value: Int) = SimpleNamedAttribute(name, JvmPrimitiveLiteral { value })
