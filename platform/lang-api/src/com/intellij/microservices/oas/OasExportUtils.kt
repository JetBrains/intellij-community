// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.oas

import com.intellij.microservices.endpoints.EndpointsElementItem
import com.intellij.microservices.endpoints.EndpointsListItem
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.microservices.endpoints.EndpointsUrlTargetProvider
import com.intellij.microservices.url.UrlPath
import com.intellij.microservices.url.UrlTargetInfo
import java.util.*
import kotlin.collections.iterator

val EMPTY_OPENAPI_SPECIFICATION: OpenApiSpecification = OpenApiSpecification(emptyList())

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

fun <G : Any, E : Any> getOpenApi(provider: EndpointsProvider<G, E>, group: G, endpoint: E): OpenApiSpecification? {
  return if (provider is EndpointsUrlTargetProvider<G, E> && provider.shouldShowOpenApiPanel()) {
    val openApiFromProvider = provider.getOpenApiSpecification(group, endpoint)
    openApiFromProvider ?: getSpecificationByUrls(provider.getUrlTargetInfo(group, endpoint))
  }
  else
    null
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