// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.psi.PsiElement

/**
 * Implement this interface to unite the functionality of the [StubElementFactory] and [StubSerializer] in a single class. This is the
 * most common scenario for element types. Basically, it represents the subset of the old [IStubElementType]. To register a factory, use [StubRegistryExtension]
 */
interface StubSerializingElementFactory<Stub, Psi> : StubElementFactory<Stub, Psi>, StubSerializer<Stub>
  where Psi : PsiElement, Stub : StubElement<*>