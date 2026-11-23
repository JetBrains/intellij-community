// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.patterns.PlatformPatterns
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FilePathReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * The project setup and the helpers of the tests of the quick fixes that create a file or a directory for a file reference.
 */
internal object CreateFileQuickFixTestUtil {
  /**
   * The descriptor of a module with the Maven source roots in the content root: `main/java`, `main/resources`,
   * the generated sources in `main/gen`, `test/java` and `test/resources`.
   */
  val STANDARD_CONTENT_ROOTS_DESCRIPTOR: LightProjectDescriptor = StandardContentRootsProjectDescriptor()

  /**
   * Runs [action] while the string literals of Java have the file path references.
   */
  fun withFileReferenceInStringLiteral(project: Project, action: Runnable) {
    val referenceProvidersRegistry = ReferenceProvidersRegistry.getInstance().getRegistrar(JavaLanguage.INSTANCE) as PsiReferenceRegistrarImpl
    val fileReferenceProvider = FilePathReferenceProvider()
    try {
      referenceProvidersRegistry.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression::class.java), fileReferenceProvider)
      PsiManager.getInstance(project).dropPsiCaches()
      action.run()
    }
    finally {
      referenceProvidersRegistry.unregisterReferenceProvider(PsiLiteralExpression::class.java, fileReferenceProvider)
    }
  }

  /**
   * Returns [ref] when it is a [FileReference], or the first [FileReference] of a [PsiMultiReference].
   */
  fun findFileReference(ref: PsiReference?): FileReference {
    if (ref is FileReference) {
      return ref
    }
    if (ref is PsiMultiReference) {
      val filtered = ref.references.filterIsInstance<FileReference>()
      if (filtered.isNotEmpty()) {
        return filtered[0]
      }
    }
    throw AssertionError("Unable to find FileReference")
  }

  private class StandardContentRootsProjectDescriptor : LightJavaCodeInsightFixtureTestCase.ProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)

      contentEntry.clearSourceFolders()

      val contentEntryUrl = contentEntry.url

      contentEntry.addSourceFolder("$contentEntryUrl/main/java", JavaSourceRootType.SOURCE)
      contentEntry.addSourceFolder("$contentEntryUrl/main/resources", JavaResourceRootType.RESOURCE)

      val generatedProperties = JavaSourceRootType.SOURCE.createDefaultProperties()
      generatedProperties.isForGeneratedSources = true
      contentEntry.addSourceFolder("$contentEntryUrl/main/gen", JavaSourceRootType.SOURCE, generatedProperties)

      contentEntry.addSourceFolder("$contentEntryUrl/test/java", JavaSourceRootType.TEST_SOURCE)
      contentEntry.addSourceFolder("$contentEntryUrl/test/resources", JavaResourceRootType.TEST_RESOURCE)
    }
  }
}
