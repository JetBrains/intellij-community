// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bookmarks.backend

import com.intellij.platform.bookmarks.rpc.BookmarksApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

private class BookmarksServiceProvider : RemoteApiProvider{
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<BookmarksApi>()){
      BookmarksApiImpl()
    }
  }
}