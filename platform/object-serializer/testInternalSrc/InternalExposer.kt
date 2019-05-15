// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

fun getBindingProducer(serializer: ObjectSerializer): BindingProducer<RootBinding> = serializer.serializer.bindingProducer

fun getBindingCount(producer: BindingProducer<RootBinding>) = producer.bindingCount