package com.jetbrains.fleet.rpc.plugin.fir

import com.jetbrains.fleet.rpc.plugin.REMOTE_API_DESCRIPTOR_INTERFACE_CLASS_ID
import com.jetbrains.fleet.rpc.plugin.RPC_ANNOTATION_FQN
import com.jetbrains.fleet.rpc.plugin.remoteApiDescriptorImplClassName
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createNestedClass
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.Name

object RpcPluginKey : GeneratedDeclarationKey() {
  override fun toString(): String = "RpcPluginKey"
}

val rpcAnnotationPredicate = DeclarationPredicate.create { annotated(RPC_ANNOTATION_FQN) }

class GenerateDescriptorObjectPass(session: FirSession) : FirDeclarationGenerationExtension(session) {

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(rpcAnnotationPredicate)
  }

  override fun getNestedClassifiersNames(classSymbol: FirClassSymbol<*>, context: NestedClassGenerationContext): Set<Name> {
    return if (classSymbol.classKind.isInterface && session.predicateBasedProvider.matches(rpcAnnotationPredicate, classSymbol)) {
      setOf(remoteApiDescriptorImplClassName)
    }
    else {
      emptySet()
    }
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext
  ): FirClassLikeSymbol<*>? {
    return when (name) {
      remoteApiDescriptorImplClassName -> {
        createNestedClass(owner, remoteApiDescriptorImplClassName, RpcPluginKey, ClassKind.OBJECT) {
          superType(REMOTE_API_DESCRIPTOR_INTERFACE_CLASS_ID.constructClassLikeType(arrayOf(owner.defaultType())))
        }.symbol
      }

      else -> null
    }
  }
}
