package com.jetbrains.fleet.rpc.plugin

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.diagnostics.errorWithoutSource
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.BaseSourcelessDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.warningWithoutSource

object RpcPluginDiagnostics : KtDiagnosticsContainer() {
  val RPC_PLUGIN_WARNING: KtSourcelessDiagnosticFactory by warningWithoutSource()
  val RPC_PLUGIN_ERROR: KtSourcelessDiagnosticFactory by errorWithoutSource()

  override fun getRendererFactory(): BaseDiagnosticRendererFactory = Messages

  object Messages : BaseSourcelessDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap("RPC") { map ->
      map.put(RPC_PLUGIN_WARNING, MESSAGE_PLACEHOLDER)
      map.put(RPC_PLUGIN_ERROR, MESSAGE_PLACEHOLDER)
    }
  }
}
