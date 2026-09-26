// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

/**
 * Default empty stub serializer, when stub should be created but nothing special should be stored in it. *
 */
class DefaultFileStubSerializer : StubSerializer<PsiFileStub<*>> {
  override fun getExternalId(): String = StubSerializerId.DEFAULT_EXTERNAL_ID

  override fun serialize(stub: PsiFileStub<*>, dataStream: StubOutputStream): Unit = Unit

  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): PsiFileStub<*> = PsiFileStubImpl(null)

  override fun indexStub(stub: PsiFileStub<*>, sink: IndexSink): Unit = Unit
}