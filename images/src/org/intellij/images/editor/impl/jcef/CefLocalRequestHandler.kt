// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl.jcef

import com.intellij.ui.jcef.utils.JBCefLocalRequestHandler

@Deprecated(replaceWith = ReplaceWith("com.intellij.ui.jcef.utils.JBCefStreamResourceHandler"),
            message = "Use JBCefStreamResourceHandler instead",
            level = DeprecationLevel.WARNING)
class CefLocalRequestHandler(
  myProtocol: String,
  myAuthority: String,
) : JBCefLocalRequestHandler(myProtocol, myAuthority)
