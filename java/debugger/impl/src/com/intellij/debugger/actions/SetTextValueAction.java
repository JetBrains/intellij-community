// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.inline.SetTextValueActionBase;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class SetTextValueAction extends SetTextValueActionBase {

  @Override
  protected void setTextValue(@NotNull XValueNodeImpl node, @NotNull String text) {
    DebuggerUIUtil.setTreeNodeValue(node,
                                    XExpressionImpl.fromText(
                                      StringUtil.wrapWithDoubleQuote(DebuggerUtils.translateStringValue(text))),
                                    (@NlsContexts.DialogMessage String errorMessage) -> Messages.showErrorDialog(node.getTree(),
                                                                                                                 errorMessage));
  }
}