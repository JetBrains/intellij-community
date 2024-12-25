package com.intellij.microservices.url.parameters

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.presentation.Presentation
import com.intellij.microservices.url.HttpMethods
import com.intellij.microservices.url.references.*
import com.intellij.microservices.utils.CommonFakeNavigatablePomTarget
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.pom.Navigatable
import com.intellij.pom.PomTarget
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.mkAttachments
import com.intellij.semantic.SemKey
import com.intellij.util.asSafely
import org.jetbrains.annotations.TestOnly

class QueryParameterNameReference(val context: UrlPathContext,
                                  host: PsiLanguageInjectionHost,
                                  rangeInElement: TextRange = ElementManipulators.getValueTextRange(host),
                                  private val forceFindUsagesOnNavigate: Boolean = false
) : PsiReferenceBase<PsiElement>(host, rangeInElement, false), UrlSegmentReference {

  override fun resolve(): PsiElement? = queryParameterPomTarget?.let {
    QueryParameterInfoFakeElement(element.project, it, forceFindUsagesOnNavigate)
  }

  private val queryParameterPomTarget: QueryParameterNamePomTarget? by lazy {
    if (context.resolveRequests.none()) return@lazy null
    QueryParameterNamePomTarget(UrlPathReferenceUnifiedPomTarget(context, element.project), value)
  }

  override fun getValue(): String {
    try {
      return super.getValue()
    }
    catch (e: Exception) {
      if (e is ControlFlowException) throw e
      // Diagnostics for IDEA-319862
      throw RuntimeExceptionWithAttachments(e, *mkAttachments(element))
    }
  }

  override fun getVariants(): Array<Any> =
    queryParameterPomTarget?.urlPathReferenceUnifiedPomTarget?.resolvedTargets?.asSequence().orEmpty()
      .filter { it.methods.isEmpty() || it.methods.contains(HttpMethods.GET) }
      .flatMap { it.queryParameters.asSequence() }
      .map { param ->
        LookupElementBuilder.create(param.name).withIcon(AllIcons.Nodes.Parameter)
      }
      .toList().toTypedArray()

  private class QueryParameterInfoFakeElement(
    project: Project,
    private val queryParameterPomTarget: QueryParameterNamePomTarget,
    private val forceFindUsages: Boolean
  ) : CommonFakeNavigatablePomTarget(project, queryParameterPomTarget), UrlSegmentReferenceTarget {

    override fun navigate(requestFocus: Boolean) {
      if (forceFindUsages) return showFindUsages()
      if (queryParameterPomTarget.canNavigate())
        queryParameterPomTarget.navigate(requestFocus)
      else
        super.navigate(requestFocus)
    }
  }

  @Presentation(provider = QueryParameterPresentationProvider::class)
  public class QueryParameterNamePomTarget(
    val urlPathReferenceUnifiedPomTarget: UrlPathReferenceUnifiedPomTarget,
    name: String
  ) : CommonFakeNavigatablePomTarget.SimpleNamePomTarget(name) {

    val navigatablePsiElement: PsiElement?
      get() = this.paramNavigatable ?: urlPathReferenceUnifiedPomTarget.navigatablePsiElement

    val paramNavigatable: PsiElement?
      get() {
        val resolvedParameters = urlPathReferenceUnifiedPomTarget.resolvedTargets.asSequence()
          .flatMap { it.queryParameters.asSequence().filter { it.name == name } }

        val paramNavigatable = resolvedParameters
          .mapNotNull { it.resolveToPsiElement() }
          .firstOrNull()
        return paramNavigatable
      }

    override fun navigate(requestFocus: Boolean) {
      val psiElement = navigatablePsiElement

      if (psiElement is Navigatable && psiElement.canNavigate())
        psiElement.navigate(requestFocus)
      else
        urlPathReferenceUnifiedPomTarget.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = urlPathReferenceUnifiedPomTarget.canNavigate()

    override fun equals(other: Any?): Boolean {
      if (!super.equals(other)) return false
      other as QueryParameterNamePomTarget
      return urlPathReferenceUnifiedPomTarget == other.urlPathReferenceUnifiedPomTarget
    }

    override fun hashCode(): Int {
      var result = super.hashCode()
      result = 31 * result + urlPathReferenceUnifiedPomTarget.hashCode()
      return result
    }
  }

  companion object : RenameableSemElementSupport<QueryParameterSem> {
    private fun createQueryParameterInfoPomTargetElement(project: Project,
                                                         context: UrlPathContext,
                                                         name: String,
                                                         forceFindUsages: Boolean): PomTargetPsiElement {
      val pomTarget = QueryParameterNamePomTarget(UrlPathReferenceUnifiedPomTarget(context, project), name)
      return QueryParameterInfoFakeElement(project, pomTarget, forceFindUsages)
    }

    override fun findReferencingPsiElements(pomTarget: PomTarget): Iterable<PsiElement> =
      pomTarget.asSafely<QueryParameterNamePomTarget>()
        ?.paramNavigatable
        ?.takeIf { getSemElement(it) != null }
        ?.let { listOf(it) } ?: emptyList()

    override fun supportsTarget(pomTarget: PomTarget): Boolean = pomTarget is QueryParameterNamePomTarget

    override fun createPomTargetPsi(project: Project, sem: QueryParameterSem): PomTargetPsiElement =
      createQueryParameterInfoPomTargetElement(
        project,
        sem.urlPathContext,
        sem.name,
        true
      )

    override val SEM_KEY: SemKey<QueryParameterSem>
      get() = QUERY_PARAMETER_SEM_KEY

    @TestOnly
    @JvmStatic
    fun getNavigatablePsiElement(reference: QueryParameterNameReference): PsiElement? =
      reference.resolve().asSafely<QueryParameterInfoFakeElement>()
        ?.target.asSafely<QueryParameterNamePomTarget>()
        ?.navigatablePsiElement
  }
}