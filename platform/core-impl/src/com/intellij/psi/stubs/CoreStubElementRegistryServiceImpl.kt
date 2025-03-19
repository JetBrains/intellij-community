// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILightStubFileElementType
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class CoreStubElementRegistryServiceImpl : StubElementRegistryService {
  override fun getStubFactory(type: IElementType): StubElementFactory<*, *>? {
    if (type is IStubElementType<*, *>) {
      @Suppress("UNCHECKED_CAST")
      return StubElementFactoryAdapter(type as IStubElementType<StubElement<PsiElement>, PsiElement>)
    }

    return null
  }

  override fun getLightStubFactory(type: IElementType): LightStubElementFactory<*, *>? {
    if (type is ILightStubElementType<*, *>) {
      @Suppress("UNCHECKED_CAST")
      return LightStubElementFactoryAdapter(type as ILightStubElementType<StubElement<PsiElement>, PsiElement>)
    }

    return null
  }

  override fun getStubSerializer(type: IElementType): ObjectStubSerializer<*, Stub>? {
    if (type is ObjectStubSerializer<*, *>) {
      @Suppress("UNCHECKED_CAST")
      return type as ObjectStubSerializer<*, Stub>
    }

    return null
  }

  override fun getStubDescriptor(language: Language): LanguageStubDescriptor? {
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language) ?: return null
    val fileNodeType = parserDefinition.getFileNodeType() as? IStubFileElementType<*> ?: return null

    val stubDefinition = when (fileNodeType) {
      is ILightStubFileElementType<*> -> LightLanguageStubDefinitionAdapter(fileNodeType)
      else -> LanguageStubDefinitionAdapter(fileNodeType)
    }

    return LanguageStubDescriptorAdapter(
      language = language,
      fileElementType = fileNodeType,
      stubDefinition = stubDefinition
    )
  }
}

private class StubElementFactoryAdapter<Stub, Psi>(
  val type: IStubElementType<Stub, Psi>,
) : StubElementFactory<Stub, Psi> where Psi : PsiElement, Stub : StubElement<Psi> {
  override fun createPsi(stub: Stub): Psi? =
    type.createPsi(stub)

  override fun createStub(psi: Psi, parentStub: StubElement<out PsiElement>?): Stub =
    type.createStub(psi, parentStub)

  override fun shouldCreateStub(node: ASTNode): Boolean =
    type.shouldCreateStub(node)

  override fun equals(other: Any?): Boolean =
    this === other || other is StubElementFactoryAdapter<*, *> && type == other.type
  override fun hashCode(): Int = type.hashCode()
}

private class LightStubElementFactoryAdapter<Stub, Psi>(
  val type: ILightStubElementType<Stub, Psi>,
) : LightStubElementFactory<Stub, Psi> where Psi : PsiElement, Stub : StubElement<Psi> {

  override fun createStub(tree: LighterAST, node: LighterASTNode, parentStub: StubElement<*>): Stub =
    type.createStub(tree, node, parentStub)

  override fun createStub(psi: Psi, parentStub: StubElement<out PsiElement>?): Stub =
    type.createStub(psi, parentStub)

  override fun shouldCreateStub(node: ASTNode): Boolean =
    type.shouldCreateStub(node)

  override fun createPsi(stub: Stub): Psi? =
    type.createPsi(stub)

  override fun shouldCreateStub(tree: LighterAST, node: LighterASTNode, parentStub: StubElement<*>): Boolean =
    type.shouldCreateStub(tree, node, parentStub)

  override fun equals(other: Any?): Boolean =
    this === other || other is LightStubElementFactoryAdapter<*, *> && type == other.type

  override fun hashCode(): Int = type.hashCode()
}

private class LanguageStubDescriptorAdapter(
  override val language: Language,
  override val fileElementType: IStubFileElementType<*>,
  override val stubDefinition: LanguageStubDefinition
) : LanguageStubDescriptor {

  override val fileElementSerializer: IStubFileElementType<*>
    get() = fileElementType

  override fun toString(): String = "LanguageStubDescriptorAdapter(language=$language)"
}

private class LanguageStubDefinitionAdapter(
  private val fileElementType: IStubFileElementType<*>
) : LanguageStubDefinition {

  override val stubVersion: Int
    get() = fileElementType.stubVersion

  override val builder: StubBuilder
    get() = fileElementType.builder

  override fun shouldBuildStubFor(file: VirtualFile): Boolean =
    fileElementType.shouldBuildStubFor(file)

  override fun toString(): String = "LanguageStubDefinitionAdapter(language=${fileElementType.language})"
}

private class LightLanguageStubDefinitionAdapter(
  private val fileElementType: ILightStubFileElementType<*>
) : LightLanguageStubDefinition {

  override val stubVersion: Int
    get() = fileElementType.stubVersion

  override val builder: StubBuilder
    get() = fileElementType.builder

  override fun shouldBuildStubFor(file: VirtualFile): Boolean =
    fileElementType.shouldBuildStubFor(file)

  override fun parseContentsLight(chameleon: ASTNode): FlyweightCapableTreeStructure<LighterASTNode> =
    fileElementType.parseContentsLight(chameleon)

  override fun toString(): String = "LightLanguageStubDefinitionAdapter(language=${fileElementType.language})"
}

@ApiStatus.Internal
object StubSerializerId {
  const val DEFAULT_EXTERNAL_ID: String = "psi.file"
}