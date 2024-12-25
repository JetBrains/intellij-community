package com.intellij.microservices.oas.serialization

import com.fasterxml.jackson.core.JsonFactory
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.microservices.MicroservicesBundle
import com.intellij.microservices.endpoints.*
import com.intellij.microservices.oas.*
import com.intellij.microservices.url.UrlPath
import com.intellij.microservices.url.UrlTargetInfo
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

val EMPTY_OPENAPI_SPECIFICATION: OpenApiSpecification = OpenApiSpecification(emptyList())

@ApiStatus.Internal
fun exportOasDraft(models: OpenApiSpecification?, project: Project) {
  exportOasDraft(project) { models }
}

@ApiStatus.Internal
fun exportOasDraft(project: Project, modelProvider: () -> OpenApiSpecification?) {
  val fileRef = Ref<PsiFile>(null)

  ProgressManager.getInstance().runProcessWithProgressSynchronously(
    Runnable {
      val json = runReadAction {
        val models = modelProvider.invoke() ?: return@runReadAction null

        generateOasDraft(project.name, models)
      } ?: return@Runnable

      WriteCommandAction.runWriteCommandAction(project, MicroservicesBundle.message("command.export.openapi.draft"), null, Runnable {
        EndpointsChangeTracker.withExpectedChanges(project) {
          val psiFile = generateOasDraftFile(project, json)

          ReformatCodeProcessor(psiFile, false).runWithoutProgress()

          fileRef.set(psiFile)
        }
      })
    },
    MicroservicesBundle.message("progress.export.openapi.draft"), true, project)

  val generatedFile = fileRef.get()
  if (generatedFile != null) {
    val fileEditorManager = FileEditorManager.getInstance(project)
    if (!fileEditorManager.isFileOpen(generatedFile.virtualFile)) {
      fileEditorManager.openEditor(OpenFileDescriptor(project, generatedFile.virtualFile), true)
    }
  }
}

fun getSpecificationByUrls(urls: Iterable<UrlTargetInfo>): OpenApiSpecification {
  return OpenApiSpecification(urls.map { urlTargetInfo ->
    OasEndpointPath.Builder(urlTargetInfo.path.getPresentation(OPEN_API_PRESENTATION)).build {
      val pathParams = urlTargetInfo.path.segments
        .filterIsInstance<UrlPath.PathSegment.Variable>()
        .mapNotNull {
          val variableName = it.variableName
          if (variableName != null)
            OasParameter.Builder(variableName, OasParameterIn.PATH).build()
          else
            null
        }

      val queryParams = urlTargetInfo.queryParameters.map {
        OasParameter.Builder(it.name, OasParameterIn.QUERY).build()
      }

      operations = urlTargetInfo.methods.map { method ->
        OasOperation.Builder(method).build {
          summary = method.uppercase(Locale.getDefault()) + " " + urlTargetInfo.path.getPresentation(
            UrlPath.FULL_PATH_VARIABLE_PRESENTATION)
          isDeprecated = urlTargetInfo.isDeprecated
          responses = listOf(OasResponse("200", "OK"))
          parameters = pathParams + queryParams
        }
      }
    }
  })
}

fun squashOpenApiSpecifications(specifications: List<OpenApiSpecification>): OpenApiSpecification {
  if (specifications.size <= 1) return specifications.firstOrNull() ?: EMPTY_OPENAPI_SPECIFICATION

  val grouped = specifications.flatMap { it.paths }.groupBy { it.path }

  val list = mutableListOf<OasEndpointPath>()

  val tags = specifications.flatMap { it.tags ?: emptyList() }.distinct()

  for (entry in grouped) {
    val items = entry.value

    if (items.size == 1) {
      list.addAll(items)
    }
    else {
      val path = entry.key
      val summary = items[0].summary

      val operations = items.flatMap { it.operations }

      if (items.all { it.summary == summary } && operations.distinctBy { it.method }.size == operations.size) {
        // squash if summary is the same and no duplicated HTTP methods
        list.add(OasEndpointPath(path, summary, operations))
      }
      else {
        list.addAll(entry.value)
      }
    }
  }

  val mergedSchemas = mutableMapOf<String, OasSchema>()
  specifications.forEach { specification ->
    specification.components?.schemas?.let { specSchemas ->
      mergedSchemas.putAll(specSchemas)
    }
  }
  val components = if (mergedSchemas.isNotEmpty()) OasComponents(mergedSchemas) else null

  return OpenApiSpecification(list, components, tags)
}

/**
 * Write module endpoints to file with Path, use it only for tests and headless IDE.
 */
@ApiStatus.Internal
fun generateOasExports(module: Module, path: Path) {
  val openApiSpecification = runReadAction { getModuleSpecification(module) }

  Files.newBufferedWriter(path, StandardCharsets.UTF_8).use {
    generateOasContent(JsonFactory(), module.name, openApiSpecification, it)
  }
}

@RequiresReadLock
fun generateOasExports(module: Module, providers: List<EndpointsProvider<*, *>>, writer: Writer) {
  val openApiSpecification = getModuleSpecification(module.project, DefaultEndpointsModule(module), providers)
  generateOasContent(JsonFactory(), module.name, openApiSpecification, writer)
}

private fun generateOasDraftFile(project: Project, json: String): PsiFile {
  val file = exportOpenApiRootType()
    .findFile(project, FileUtil.sanitizeFileName(project.name) + "-openapi.yaml", ScratchFileService.Option.create_new_always)

  ProgressManager.checkCanceled()
  VfsUtil.saveText(file, json)

  return PsiManager.getInstance(project).findFile(file)!!
}

@ApiStatus.Internal
fun getModuleSpecification(module: Module): OpenApiSpecification {
  return getModuleSpecification(module.project, DefaultEndpointsModule(module), EndpointsProvider.getAllProviders())
}

fun getModuleSpecification(project: Project, moduleEntity: EndpointsModuleEntity): OpenApiSpecification {
  return getModuleSpecification(project, moduleEntity, EndpointsProvider.getAllProviders())
}

private fun getModuleSpecification(project: Project,
                                   moduleEntity: EndpointsModuleEntity,
                                   providers: List<EndpointsProvider<*, *>>): OpenApiSpecification {
  return providers
    .filter { it.endpointType == HTTP_SERVER_TYPE && it.getStatus(project) != EndpointsProvider.Status.UNAVAILABLE }
    .flatMap { getEndpointSpecifications(project, moduleEntity, it) }
    .let { squashOpenApiSpecifications(it) }
}

fun getOpenApiSpecification(endpointsList: Collection<EndpointsListItem>): OpenApiSpecification? {
  fun <G : Any, E : Any> getOpenApi(i: EndpointsElementItem<G, E>): OpenApiSpecification? =
    getOpenApi(i.provider, i.group, i.endpoint)

  val specifications = endpointsList.asSequence()
    .filterIsInstance<EndpointsElementItem<*, *>>()
    .mapNotNull { getOpenApi(it) }
    .toList()

  if (specifications.isEmpty()) return null

  return squashOpenApiSpecifications(specifications)
}

private fun <G : Any, E : Any> getEndpointSpecifications(project: Project,
                                                         moduleEntity: EndpointsModuleEntity,
                                                         provider: EndpointsProvider<G, E>): Collection<OpenApiSpecification> {
  return provider.getEndpointGroups(project, createFilter(moduleEntity))
    .flatMap { group ->
      provider.getEndpoints(group).mapNotNull {
        getOpenApi(provider, group, it)
      }
    }
}

private fun <G : Any, E : Any> getOpenApi(provider: EndpointsProvider<G, E>, group: G, endpoint: E): OpenApiSpecification? {
  return if (provider is EndpointsUrlTargetProvider<G, E> && provider.shouldShowOpenApiPanel()) {
    val openApiFromProvider = provider.getOpenApiSpecification(group, endpoint)
    openApiFromProvider ?: getSpecificationByUrls(provider.getUrlTargetInfo(group, endpoint))
  }
  else
    null
}

private fun createFilter(moduleEntity: EndpointsModuleEntity): EndpointsFilter {
  val projectModel = EndpointsProjectModel.EP_NAME.extensionList.firstOrNull()
  if (projectModel != null) {
    return projectModel.createFilter(moduleEntity, false, false)
  }

  return ModuleEndpointsFilter((moduleEntity as DefaultEndpointsModule).module, false, false)
}
