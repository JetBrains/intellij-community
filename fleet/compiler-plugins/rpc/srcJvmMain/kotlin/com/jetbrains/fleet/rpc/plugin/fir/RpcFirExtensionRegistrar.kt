package com.jetbrains.fleet.rpc.plugin.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class RpcFirExtensionRegistrar() : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    //+::RpcCheckersComponent
    +::GenerateDescriptorObjectPass
  }
}
//
//object RpcPluginClassChecker : FirClassChecker(MppCheckerKind.Common) {
//  context(checkerContext: CheckerContext, diagnosticReporter: DiagnosticReporter)
//  override fun check(declaration: FirClass) {
//    TODO("Not yet implemented")
//  }
//}
//
//class RpcCheckersComponent(session: FirSession) : FirAdditionalCheckersExtension(session) {
//  override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
//    override val classCheckers: Set<FirClassChecker> = setOf(RpcPluginClassChecker)
//  }
//}
