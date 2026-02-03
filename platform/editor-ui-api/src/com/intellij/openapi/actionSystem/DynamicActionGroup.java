// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

/**
 * Marker interface for {@link ActionGroup} with dynamic calculated {@link ActionGroup#getChildren(AnActionEvent)}
 * @deprecated Not needed. Everything works out of the box without it.
 */
@Deprecated(forRemoval = true)
public interface DynamicActionGroup {
}