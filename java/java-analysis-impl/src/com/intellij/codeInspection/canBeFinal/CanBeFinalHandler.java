// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.canBeFinal;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMember;
import org.jetbrains.annotations.NotNull;

/**
 * Allows registering custom handlers that may suppress warnings from
 * {@link CanBeFinalInspection}. For example, if some field is known
 * to be modified via reflection by a custom framework, a plugin for
 * this framework may recognize such fields and report them to prevent
 * inspection false-positives.
 */
public abstract class CanBeFinalHandler {
  public static final ExtensionPointName<CanBeFinalHandler> EP_NAME = ExtensionPointName.create("com.intellij.canBeFinal");

  /**
   * @param member a member (field or method) to check whether it's allowed to be final
   * @return false if this extension prohibits marking this member as final.
   * Must return true for every member that is unknown to the current extension.
   * <p>
   * If the field is written explicitly, it is detected by the inspection itself,
   * so implementations of this method shouldn't care about such a case.
   */
  public abstract boolean canBeFinal(@NotNull PsiMember member);

  /**
   * @param member member to check
   * @return true if no registered extension prohibits marking this member as final.
   */
  public static boolean allowToBeFinal(@NotNull PsiMember member) {
    for (CanBeFinalHandler handler : EP_NAME.getExtensionList()) {
      if (!handler.canBeFinal(member)) return false;
    }
    return true;
  }
}
