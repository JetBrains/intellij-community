/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.jsp;

import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class JspSpiUtil {

  @Nullable
  private static JspSpiUtil getJspSpiUtil() {
    return ApplicationManager.getApplication().getComponent(JspSpiUtil.class);
  }

  @Nullable
  public static IChameleonElementType createSimpleChameleon(@NonNls String debugName, IElementType start, IElementType end, final int startLength) {
    final JspSpiUtil util = getJspSpiUtil();
    return util != null ? util._createSimpleChameleon(debugName, start, end, startLength) : null;
  }

  protected abstract IChameleonElementType _createSimpleChameleon(@NonNls String debugName, IElementType start, IElementType end,
                                                                  final int startLength);

  @Nullable
  public static IFileElementType createTemplateType() {
    final JspSpiUtil util = getJspSpiUtil();
    return util != null ? util._createTemplateType() : null;
  }

  protected abstract IFileElementType _createTemplateType();

}
