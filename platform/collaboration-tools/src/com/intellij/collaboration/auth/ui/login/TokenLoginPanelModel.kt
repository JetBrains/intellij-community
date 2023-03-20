// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui.login

/**
 * Model for login interface where login is performed via a token
 *
 * @property serverUri URI of a server
 * @property token access token
 */
interface TokenLoginPanelModel : LoginModel {

  var serverUri: String
  var token: String
}