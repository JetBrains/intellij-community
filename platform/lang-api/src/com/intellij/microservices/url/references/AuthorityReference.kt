package com.intellij.microservices.url.references

import com.intellij.ide.presentation.Presentation
import com.intellij.microservices.url.UrlResolverManager
import com.intellij.microservices.utils.CommonFakeNavigatablePomTarget
import com.intellij.microservices.utils.MicroservicesUsageCollector.URL_PATH_SEGMENT_NAVIGATE_EVENT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*

class AuthorityReference @JvmOverloads constructor(val givenValue: String?,
                                                   host: PsiLanguageInjectionHost,
                                                   range: TextRange,
                                                   private val customNavigate: ((UrlSegmentReference) -> Unit)? = null)
  : PsiReferenceBase.Poly<PsiElement>(host, range, false), UrlSegmentReference {
  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
    PsiElementResolveResult.createResults(resolve())

  // resolve in any case
  override fun resolve(): PsiElement =
    createElement(givenValue ?: value)

  private fun createElement(refValue: String) =
    AuthorityReferenceFakeElement(element.project, AuthorityPomTarget(refValue), customNavigate?.let { { it.invoke(this) } })

  override fun toString(): String {
    val refValue = givenValue ?: value
    return "AuthorityReference($refValue, $rangeInElement)"
  }

  @Presentation(provider = AuthorityPresentationProvider::class)
  class AuthorityPomTarget(text: String) : CommonFakeNavigatablePomTarget.SimpleNamePomTarget(text)
}

internal fun getAvailableAuthorities(project: Project, schema: String?): List<AuthorityReference.AuthorityPomTarget> =
  UrlResolverManager.getInstance(project).getAuthorityHints(schema).map { AuthorityReference.AuthorityPomTarget(it.text) }

private class AuthorityReferenceFakeElement(project: Project, unifiedPomTarget: AuthorityReference.AuthorityPomTarget,
                                            private val customNavigate: (() -> Unit)?)
  : CommonFakeNavigatablePomTarget(project, unifiedPomTarget), UrlSegmentReferenceTarget {

  override fun canNavigateToSource(): Boolean = customNavigate != null || super.canNavigateToSource()

  override fun navigate(requestFocus: Boolean) {
    if (customNavigate != null) return customNavigate.invoke()
    URL_PATH_SEGMENT_NAVIGATE_EVENT.log(project)

    super.navigate(requestFocus)
  }
}