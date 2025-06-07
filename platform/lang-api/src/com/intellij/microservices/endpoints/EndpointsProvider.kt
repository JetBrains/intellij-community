package com.intellij.microservices.endpoints

import com.intellij.microservices.endpoints.presentation.EndpointMethodPresentation
import com.intellij.microservices.oas.OpenApiSpecification
import com.intellij.microservices.url.UrlTargetInfo
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import javax.swing.JComponent

/**
 * Provides information about client/server endpoints declared in the project using a framework-specific API.
 * Good examples of such endpoints are: Spring MVC Controllers and Retrofit Client interfaces.
 *
 * @param G type of endpoint groups
 * @param E type of endpoints
 */
interface EndpointsProvider<G : Any, E : Any> {
  /**
   * Endpoint type implemented by framework.
   */
  val endpointType: EndpointType

  /**
   * UI presentation of the framework in Endpoints View.
   */
  val presentation: FrameworkPresentation

  /**
   * Fast check if there may be endpoints in the project without searching for them.
   */
  fun getStatus(project: Project): Status

  /**
   * Groups correspond to locations where endpoints are or can be declared.
   * Usually they are classes with some annotation (e.g., Spring Controllers) or some special files (e.g., Swagger YAML/JSON).
   * Providers may return all possible locations using some good-enough heuristics to speed-up list loading.
   *
   * @return endpoint groups corresponding to the passed filter,
   */
  fun getEndpointGroups(project: Project, filter: EndpointsFilter): Iterable<G>

  /**
   * @return endpoints of the group to show in the list
   */
  fun getEndpoints(group: G): Iterable<E>

  /**
   * Checks if endpoint instance is valid, i.e., can be used to get its presentation and data.
   */
  fun isValidEndpoint(group: G, endpoint: E): Boolean

  /**
   * @return presentation of a single endpoint, e.g. URL, HTTP handler, message queue topic
   * @see EndpointMethodPresentation
   */
  fun getEndpointPresentation(group: G, endpoint: E): ItemPresentation

  /**
   * Modification tracker related to the underlying data models.
   * Implementations may use language modification trackers, e.g., YAML/JSON or UAST languages modification tracker.
   *
   * @see com.intellij.psi.util.PsiModificationTracker.forLanguage
   */
  fun getModificationTracker(project: Project): ModificationTracker

  fun getDocumentationElement(group: G, endpoint: E): PsiElement? = null

  fun getNavigationElement(group: G, endpoint: E): PsiElement? = getDocumentationElement(group, endpoint)

  fun uiDataSnapshot(sink: DataSink, group: G, endpoint: E) {
    DataSink.uiDataSnapshot(sink, DataProvider { dataId -> getEndpointData(group, endpoint, dataId)})
  }

  /**
   * @return context data for actions related to endpoints
   */
  @Deprecated("Override [uiDataSnapshot] instead")
  fun getEndpointData(group: G, endpoint: E, dataId: String): Any? = null

  companion object {
    /**
     * Providers may either return documentation PSI element from [EndpointsProvider.getEndpointData] or implement [EndpointsDocumentationProvider].
     */
    @JvmField
    @ScheduledForRemoval
    @Deprecated(message = "Implement [EndpointsProvider.getDocumentationElement] instead")
    val DOCUMENTATION_ELEMENT: DataKey<PsiElement> = DataKey.create("endpoint.documentation.element")

    @JvmField
    val URL_TARGET_INFO: DataKey<Iterable<UrlTargetInfo>> = DataKey.create("endpoint.urlTargetInfo")

    @JvmField
    val EP_NAME: ExtensionPointName<EndpointsProvider<*, *>> =
      ExtensionPointName.create("com.intellij.microservices.endpointsProvider")

    fun hasAnyProviders(): Boolean = EP_NAME.hasAnyExtensions()

    fun getAllProviders(): List<EndpointsProvider<*, *>> {
      return EP_NAME.extensionList
    }

    fun getAvailableProviders(project: Project): Sequence<EndpointsProvider<*, *>> {
      return CachedValuesManager.getManager(project).getCachedValue(project, CachedValueProvider {
        Result.create(EP_NAME.extensionList.filter { it.getStatus(project) != Status.UNAVAILABLE },
                      PsiModificationTracker.MODIFICATION_COUNT,
                      DumbService.getInstance(project),
                      ProjectRootManager.getInstance(project))
      }).asSequence()
    }
  }

  enum class Status {
    /**
     * Provider is not relevant for the project, should not be shown to user.
     */
    UNAVAILABLE,

    /**
     * There may be endpoints from this provider declared in the project, or they can be added by user.
     */
    AVAILABLE,

    /**
     * There are declared endpoints in the project, or there is a high probability of that.
     */
    HAS_ENDPOINTS
  }
}

interface EndpointsUrlTargetProvider<G : Any, E : Any> : EndpointsProvider<G, E> {
  fun getUrlTargetInfo(group: G, endpoint: E): Iterable<UrlTargetInfo>

  fun getOpenApiSpecification(group: G, endpoint: E): OpenApiSpecification? = null

  fun shouldShowOpenApiPanel(): Boolean = true
}

interface EndpointsDocumentationProvider<G : Any, E : Any, R : Any> : EndpointsProvider<G, E> {
  @RequiresReadLock
  @RequiresBackgroundThread
  fun prepareDocumentationRequest(group: G, endpoint: E): R?

  @RequiresEdt
  fun getEndpointDocumentation(request: R, parentDisposable: Disposable): JComponent?
}