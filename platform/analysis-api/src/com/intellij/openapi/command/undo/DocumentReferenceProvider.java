// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * @author Konstantin Bulenkov
 */
public interface DocumentReferenceProvider {
  @Unmodifiable
  Collection<DocumentReference> getDocumentReferences();
}
