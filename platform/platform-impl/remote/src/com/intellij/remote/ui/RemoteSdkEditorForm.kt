// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ui

import com.intellij.openapi.Disposable
import com.intellij.remote.CredentialsType
import com.intellij.ui.StatusPanel

interface RemoteSdkEditorForm {
  val statusPanel: StatusPanel

  val validator: Runnable?

  val disposable: Disposable

  val bundleAccessor: BundleAccessor

  val sdkScopeController: SdkScopeController

  val parentContainer: RemoteSdkEditorContainer

  fun isSdkInConsistentState(connectionType: CredentialsType<*>): Boolean
}