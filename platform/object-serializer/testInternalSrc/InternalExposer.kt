// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import org.assertj.core.api.Assertions.assertThat

fun getBinding(aClass: Class<*>, serializer: ObjectSerializer): Any = serializer.serializer.bindingProducer.getRootBinding(aClass)

fun getBindingProducer(serializer: ObjectSerializer): Any = serializer.serializer.bindingProducer

fun getBindingCount(producer: Any) = (producer as BindingProducer).bindingCount

fun testThreadLocalPooledBlockAllocatorProvider() {
  val provider = PooledBlockAllocatorProvider()

  var allocated = 1024
  provider.vendAllocator(allocated).use { it.allocateBlock() }
  assertThat(provider.byteSize).isEqualTo(allocated)

  allocated += 1024 + 1
  provider.vendAllocator(1024 + 1).use { it.allocateBlock() }
  assertThat(provider.byteSize).isEqualTo(allocated)

  allocated += PooledBlockAllocatorProvider.POOL_THRESHOLD
  provider.vendAllocator(PooledBlockAllocatorProvider.POOL_THRESHOLD).use { it.allocateBlock() }
  assertThat(provider.byteSize).isLessThanOrEqualTo(2049)

  provider.vendAllocator(PooledBlockAllocatorProvider.POOL_THRESHOLD + 1).use { it.allocateBlock() }
  assertThat(provider.byteSize).isLessThanOrEqualTo(allocated + 1)
}