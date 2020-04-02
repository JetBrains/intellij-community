// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeWithMe

open class ClientIdValueStoreServiceImpl : ClientIdValueStoreService {
    private val myStorage = ThreadLocal<String>()

    override var value: String?
        get() = myStorage.get()
        set(value) = myStorage.set(value)
}