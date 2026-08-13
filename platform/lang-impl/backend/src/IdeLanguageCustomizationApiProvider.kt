// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.impl.backend

import com.intellij.find.impl.IdeLanguageCustomizationApi
import com.intellij.find.impl.IdeLanguageCustomizationApiImpl
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class IdeLanguageCustomizationApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<IdeLanguageCustomizationApi>()) {
      IdeLanguageCustomizationApiImpl()
    }
  }
}
