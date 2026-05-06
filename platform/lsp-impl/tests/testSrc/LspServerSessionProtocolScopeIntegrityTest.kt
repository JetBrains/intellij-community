package com.intellij.platform.lsp

import com.intellij.platform.lsp.common.LspNotification
import com.intellij.platform.lsp.common.LspRequest
import com.intellij.platform.lsp.common.ServerSessionProtocolScope
import com.intellij.platform.lsp.common.ServerToClientLspNotification
import com.intellij.platform.lsp.common.ServerToClientLspRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.NotebookDocumentService
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.concurrent.CompletableFuture

class LspServerSessionProtocolScopeIntegrityTest {

  private data class LspMethodInfo(val paramsClass: Class<*>, val responseClass: Class<*>?, val serverToClient: Boolean)

  @Test
  fun `all lsp4j methods are declared in scope`() {
    val lsp4jMethods = collectLsp4jMethods()
    val scopeMethods = collectScopeMethods()

    val errors = mutableListOf<String>()

    for ((method, lsp4jInfo) in lsp4jMethods) {
      val scopeEntry = scopeMethods[method]
      if (scopeEntry == null) {
        errors.add("Missing scope declaration for '$method'")
        continue
      }
      if (lsp4jInfo.paramsClass != scopeEntry.paramsClass) {
        errors.add("Wrong params class for '$method': expected ${lsp4jInfo.paramsClass.simpleName}, got ${scopeEntry.paramsClass.simpleName}")
      }
      if (lsp4jInfo.responseClass != null && scopeEntry.responseClass != null) {
        if (lsp4jInfo.responseClass != scopeEntry.responseClass) {
          errors.add("Wrong response class for '$method': expected ${lsp4jInfo.responseClass.simpleName}, got ${scopeEntry.responseClass.simpleName}")
        }
      }
      if (lsp4jInfo.serverToClient != scopeEntry.serverToClient) {
        val expected = if (lsp4jInfo.serverToClient) "ServerToClient" else "ClientToServer"
        val got = if (scopeEntry.serverToClient) "ServerToClient" else "ClientToServer"
        errors.add("Wrong direction for '$method': expected $expected, got $got")
      }
    }

    for (method in scopeMethods.keys) {
      if (method !in lsp4jMethods) {
        errors.add("Stale scope declaration for '$method' (not found in lsp4j interfaces)")
      }
    }

    if (errors.isNotEmpty()) {
      fail<Unit>(errors.joinToString("\n"))
    }
  }

  private val serverToClientInterfaces = setOf(LanguageClient::class.java)

  private fun collectLsp4jMethods(): Map<String, LspMethodInfo> {
    val interfaces = listOf(
      LanguageClient::class.java,
      LanguageServer::class.java,
      TextDocumentService::class.java,
      WorkspaceService::class.java,
      NotebookDocumentService::class.java,
    )

    val result = mutableMapOf<String, LspMethodInfo>()
    for (clazz in interfaces) {
      val serverToClient = clazz in serverToClientInterfaces
      for (method in clazz.declaredMethods) {
        val lspMethod = resolveLspMethodName(clazz, method) ?: continue
        val paramsClass = if (method.parameterCount > 0) method.parameterTypes[0] else Unit::class.java
        val responseClass = if (method.getAnnotation(JsonRequest::class.java) != null) extractResponseClass(method) else null
        result[lspMethod] = LspMethodInfo(paramsClass, responseClass, serverToClient)
      }
    }
    return result
  }

  private fun resolveLspMethodName(clazz: Class<*>, method: Method): String? {
    val jsonRequest = method.getAnnotation(JsonRequest::class.java)
    val jsonNotification = method.getAnnotation(JsonNotification::class.java)

    val (annotationValue, useSegment) = when {
      jsonRequest != null -> jsonRequest.value to jsonRequest.useSegment
      jsonNotification != null -> jsonNotification.value to jsonNotification.useSegment
      else -> return null
    }

    val baseName = annotationValue.ifEmpty { method.name }

    if (useSegment) {
      val segment = clazz.getAnnotation(JsonSegment::class.java)?.value
      if (segment != null) return "$segment/$baseName"
    }

    return baseName
  }

  private fun extractResponseClass(method: Method): Class<*> {
    val returnType = method.genericReturnType
    if (returnType is ParameterizedType && returnType.rawType == CompletableFuture::class.java) {
      return rawClassOf(returnType.actualTypeArguments[0])
    }
    return Void::class.java
  }

  private fun rawClassOf(type: Type): Class<*> = when (type) {
    is Class<*> -> type
    is ParameterizedType -> type.rawType as Class<*>
    is WildcardType -> rawClassOf(type.upperBounds.firstOrNull() ?: Any::class.java)
    else -> Any::class.java
  }

  private fun collectScopeMethods(): Map<String, LspMethodInfo> {
    val scope = object : ServerSessionProtocolScope() {}
    val result = mutableMapOf<String, LspMethodInfo>()

    for (field in ServerSessionProtocolScope::class.java.declaredFields) {
      field.isAccessible = true // Kotlin val properties have private backing fields
      val value = field.get(scope)
      when (value) {
        is LspRequest<*, *> -> result[value.method] = LspMethodInfo(value.paramsClass, value.responseClass, value is ServerToClientLspRequest<*, *>)
        is LspNotification<*> -> result[value.method] = LspMethodInfo(value.paramsClass, null, value is ServerToClientLspNotification<*>)
      }
    }
    return result
  }
}
