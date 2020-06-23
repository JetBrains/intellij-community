// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeWithMe

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager

interface ClientIdValueStoreService {
    companion object {
        fun tryGetInstance(): ClientIdValueStoreService? {
            if (!LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred || ApplicationManager.getApplication().isDisposed) return null
            return ServiceManager.getService(ClientIdValueStoreService::class.java)
        }
    }
    var value: String?
}
