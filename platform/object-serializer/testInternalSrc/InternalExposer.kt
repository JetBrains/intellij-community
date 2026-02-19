// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization

fun getBinding(aClass: Class<*>, serializer: ObjectSerializer): Any = serializer.serializer.bindingProducer.getRootBinding(aClass)

fun getBindingProducer(serializer: ObjectSerializer): Any = serializer.serializer.bindingProducer

fun getBindingCount(producer: Any) = (producer as BindingProducer).bindingCount