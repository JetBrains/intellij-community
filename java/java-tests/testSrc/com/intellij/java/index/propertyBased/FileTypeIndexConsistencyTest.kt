// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.index.propertyBased

import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.PropertyChecker
import kotlin.test.assertEquals

private const val FILE_NAME = "FileIdentifiableByText"

@SkipSlowTestLocally
class FileTypeIndexConsistencyTest : LightJavaCodeInsightFixtureTestCase() {
  fun testFuzzActions() {
    Disposer.register(testRootDisposable, loadExtensionWithText(
      "<fileTypeDetector implementation=\"${MyFileTypeDetector::class.java.name}\"/>"))

    val genAction: Generator<PsiIndexConsistencyTester.Action> =  Generator.from { data ->
      MyTextChange(
        data.generate(Generator.sampledFrom("java", "txt", "xml", "dll", "")),
        data.generate(Generator.booleans()))
    }

    PropertyChecker.customized().withIterationCount(50).forAll(Generator.listsOf(genAction)) { actions ->
      PsiIndexConsistencyTester.runActions(
        PsiIndexConsistencyTester.Model(myFixture.addFileToProject(FILE_NAME, "").virtualFile, myFixture), *actions.toTypedArray())
      true
    }
  }
}

class MyTextChange(text: String, viaDocument: Boolean) : JavaPsiIndexConsistencyTest.TextChange(text, viaDocument) {
  override fun performAction(model: PsiIndexConsistencyTester.Model) {
    val file = model.vFile
    val project = model.project

    if (file.fileType.isBinary && viaDocument) {
      return
    }
    super.performAction(model)

    //ensure we've updated content dependent indexes
    //file-type index is not very honest
    FileBasedIndex.getInstance().getFileData(StubUpdatingIndex.INDEX_ID, file, project)

    val fileTypeFromFile = file.fileType
    val fileTypeFromIndex = FileTypeIndex.getIndexedFileType(file, project)
    assertEquals(fileTypeFromFile, fileTypeFromIndex)
  }

}

@InternalIgnoreDependencyViolation
class MyFileTypeDetector : FileTypeRegistry.FileTypeDetector {
  override fun detect(file: VirtualFile, firstBytes: ByteSequence, firstCharsIfText: CharSequence?): FileType? {
    if (file.name != FILE_NAME) return null
    val extension = firstCharsIfText!!.toString()
    if (extension.isEmpty()) return null
    return FileTypeManager.getInstance().getFileTypeByExtension(extension)
  }
}
