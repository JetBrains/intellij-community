// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

/**
 * Consider using {@link ExternalChangeActionUtil#externalChangeAction(Runnable)}
 *
 * @deprecated This class was used as a marker interface for write actions in document subsystem.
 * It unnecessarily complicates the core API of IntelliJ Platform, so it was extracted to a separate utility
 */
@Deprecated
@SuppressWarnings("unused")
public interface ExternalChangeAction {
}
