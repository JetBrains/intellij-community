/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 15, 2001
 * Time: 5:17:38 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class RefPackageImpl extends RefEntityImpl implements RefPackage {
  public RefPackageImpl(@NotNull String name, @NotNull RefManager refManager) {
    super(name, refManager);
  }

  @Override
  public void accept(@NotNull final RefVisitor visitor) {
    if (visitor instanceof RefJavaVisitor) {
      ApplicationManager.getApplication().runReadAction(() -> ((RefJavaVisitor)visitor).visitPackage(this));
    } else {
      super.accept(visitor);
    }
  }

  public static RefEntity packageFromFQName(final RefManager manager, final String name) {
    return manager.getExtension(RefJavaManager.MANAGER).getPackage(name);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public Icon getIcon(final boolean expanded) {
    return PlatformIcons.PACKAGE_ICON;
  }
}
