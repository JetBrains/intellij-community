// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class NotificationParentGroupBean {
  @Attribute("id")
  public String id;

  @Attribute("parentId")
  public String parentId;

  @Attribute("title")
  public String title;

  @Attribute("titlePrefix")
  public String titlePrefix;

  @Override
  public String toString() {
    return title;
  }
}