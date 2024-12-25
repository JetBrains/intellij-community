// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ReadAction;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class RefPackageImpl extends RefEntityImpl implements RefPackage {
  RefPackageImpl(@NotNull String name, @NotNull RefManager refManager) {
    super(name, refManager);
  }

  @Override
  public void accept(final @NotNull RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor javaVisitor) {
      ReadAction.run(() -> javaVisitor.visitPackage(this));
    } else {
      super.accept(visitor);
    }
  }

  static RefEntity packageFromFQName(final RefManager manager, final String name) {
    return manager.getExtension(RefJavaManager.MANAGER).getPackage(name);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public Icon getIcon(final boolean expanded) {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Package);
  }
}
