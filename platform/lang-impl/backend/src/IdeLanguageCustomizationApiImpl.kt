// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.impl.backend

import com.intellij.find.impl.IdeLanguageCustomizationApi
import com.intellij.lang.IdeLanguageCustomization
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

private class IdeLanguageCustomizationApiImpl : IdeLanguageCustomizationApi {
  override suspend fun getPrimaryIdeLanguagesExtensions(): Set<String> {
    return IdeLanguageCustomization.getInstance().primaryIdeLanguages
      .mapNotNull { it.associatedFileType }
      .flatMap { fileType: LanguageFileType ->
        mutableListOf(fileType.getDefaultExtension()).plus(getAssociatedExtensions(fileType))
      }.toSet()
  }

  private fun getAssociatedExtensions(fileType: LanguageFileType): List<String> {
    return FileTypeManager.getInstance().getAssociations(fileType)
      .filterIsInstance<ExtensionFileNameMatcher>()
      .map { it.extension }
  }
}

private class IdeLanguageCustomizationApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<IdeLanguageCustomizationApi>()) {
      IdeLanguageCustomizationApiImpl()
    }
  }
}