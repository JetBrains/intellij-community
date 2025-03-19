@file:JvmName("PathVariableUtils")

package com.intellij.microservices.url.parameters

import com.intellij.microservices.url.UrlPath
import com.intellij.microservices.url.references.UrlPathContext

fun getPathVariablesFromContext(urlPathContext: UrlPathContext): Iterable<String> {
  return generateSequence(urlPathContext.fullyEvaluated, { it.parent })
    .flatMap { it.selfPaths.asSequence() }
    .flatMap { it.segments.asSequence() }
    .flatMap { pathSegment ->
      when (pathSegment) {
        is UrlPath.PathSegment.Composite -> pathSegment.segments.asSequence().filterIsInstance<UrlPath.PathSegment.Variable>()
        is UrlPath.PathSegment.Variable -> sequenceOf(pathSegment)
        else -> emptySequence()
      }
    }
    .mapNotNull { it.variableName }
    .asIterable()
}