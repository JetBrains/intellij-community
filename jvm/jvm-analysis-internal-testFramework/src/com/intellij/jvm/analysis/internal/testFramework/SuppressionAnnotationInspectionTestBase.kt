package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.SuppressionAnnotationInspection
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.pom.java.LanguageLevel
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.testFramework.LightProjectDescriptor

abstract class SuppressionAnnotationInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: SuppressionAnnotationInspection = SuppressionAnnotationInspection()

  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(LanguageLevel.HIGHEST, true) {}

  protected fun testAllowSuppressionQuickFix(jvmLanguage: JvmLanguage, code: String, vararg ids: String) {
    InspectionProfileImpl.INIT_INSPECTIONS = true
    try {
      assertEmpty(inspection.myAllowedSuppressions)
      myFixture.configureByText("A.${jvmLanguage.ext}", code)
      val intention = myFixture.findSingleIntention("Allow these suppressions")
      val previewText = buildPreviewText(ids)
      myFixture.checkIntentionPreviewHtml(
        intention,
        previewText
      )
      myFixture.launchAction(intention)
      val inspection = InspectionProfileManager.getInstance(project).currentProfile
        .getUnwrappedTool("SuppressionAnnotation", file) as SuppressionAnnotationInspection
      assertContainsElements(inspection.myAllowedSuppressions, ids.toList())
    }
    finally {
      InspectionProfileImpl.INIT_INSPECTIONS = false
    }
  }

  private fun buildPreviewText(ids: Array<out String>): String {
    val idsPart = ids.joinToString(separator = "") { "<option selected=\"selected\">$it</option>" }
    return "Ignored suppressions:<br/><br/><select multiple=\"multiple\" size=\"${ids.size + 2}\">$idsPart</select>"
  }
}
