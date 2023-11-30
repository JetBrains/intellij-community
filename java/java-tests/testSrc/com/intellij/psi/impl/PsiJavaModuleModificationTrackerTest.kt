// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaModule
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.junit5.RunInEdt
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

@RunInEdt(writeIntent = true)
class PsiJavaModuleModificationTrackerTest : LightJavaCodeInsightFixtureTestCase5() {
  @Test
  fun changeInPsiModuleInfo() {
    val file = fixture.configureByText("module-info.java", "module M1 {}")
    doTestIncremented {
      WriteCommandAction.runWriteCommandAction(fixture.project) { 
        (file as PsiJavaFile).moduleDeclaration!!.name = "M2" 
      }
      PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
    }
  }

  @Test
  fun renameInPsiModuleInfo() {
    fixture.configureByText("module-info.java", "module M<caret>1 {}")
    doTestIncremented {
      fixture.renameElementAtCaret("M2")
    }
  }

  @Test
  fun typingInPsiModuleInfo() {
    fixture.configureByText("module-info.java", "module M1 {<caret>}")
    doTestIncremented {
      fixture.type("requires java.base;")
      PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
    }
  }

  @Test
  fun changeInManifestVirtualFile() {
    val file = fixture.configureByText("manifest.mf", "${PsiJavaModule.AUTO_MODULE_NAME} = M1\n")
    doTestIncremented {
      WriteCommandAction.runWriteCommandAction(fixture.project) {
        file.virtualFile.setBinaryContent("${PsiJavaModule.AUTO_MODULE_NAME} = M2\n".toByteArray())
      }
    }
  }

  @Test
  fun changeInManifestDocument() {
    val file = fixture.configureByText("manifest.mf", "${PsiJavaModule.AUTO_MODULE_NAME} = M1\n")
    doTestIncremented {
      WriteCommandAction.runWriteCommandAction(fixture.project) {
        val documentManager = PsiDocumentManagerBase.getInstance(fixture.project)
        val document = documentManager.getDocument(file)!!
        document.setText("${PsiJavaModule.AUTO_MODULE_NAME} = M2\n")
        documentManager.commitDocument(document)
      }
    }
  }

  @Test
  fun renameManifest() {
    val file = fixture.configureByText("manifest1.mf", "${PsiJavaModule.AUTO_MODULE_NAME} = M1\n")
    doTestIncremented {
      fixture.renameElement(file, "manifest.mf")
    }
  }

  @Test
  fun renameParent() {
    val file = fixture.addFileToProject("/META-IN/MANIFEST.MF", "")
    doTestIncremented {
      fixture.renameElement(file.parent!!, "META-INF")
    }
  }

  @Test
  fun deleteParent() {
    val file = fixture.addFileToProject("/META-IN/MANIFEST.MF", "")
    doTestIncremented {
      WriteCommandAction.runWriteCommandAction(fixture.project) { 
        file.containingDirectory.delete() 
      }
    }
  }

  private fun doTestIncremented(job: () -> Unit) {
    val tracker = PsiJavaModuleModificationTracker.getInstance(fixture.project)
    val modificationCount = tracker.modificationCount
    job()
    assertTrue { tracker.modificationCount != modificationCount }
  }
}