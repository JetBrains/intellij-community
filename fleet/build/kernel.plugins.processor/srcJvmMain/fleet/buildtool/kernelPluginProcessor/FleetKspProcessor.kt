package fleet.buildtool.kernelPluginProcessor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.JvmPlatformInfo
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.PlatformInfo
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclarationContainer
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import kotlin.time.measureTime

class FleetKspProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return FleetKspProcessor(environment.codeGenerator, environment.platforms, environment.logger)
  }
}

private data class ServiceDescriptor(val interfaceFqn: String, val returnValue: String)

private const val ENTITY_TYPE_FQN = "com.jetbrains.rhizomedb.EntityType"
private val SERVICE_DESCRIPTORS = setOf(ServiceDescriptor("fleet.ship.TypedShipLauncher", "fleet.ship.TypedShipLauncher<*>"),
                                        ServiceDescriptor("fleet.kernel.plugins.Plugin", "fleet.kernel.plugins.Plugin<*>"),
                                        ServiceDescriptor("fleet.dock.api.ShipCliHandler", "fleet.dock.api.ShipCliHandler"),
                                        ServiceDescriptor("fleet.dock.api.ShipLauncherProvider", "fleet.dock.api.ShipLauncherProvider")).associateBy { it.interfaceFqn }

class FleetKspProcessor(
  private val codeGenerator: CodeGenerator,
  private val platforms: List<PlatformInfo>,
  private val logger: KSPLogger,
) : SymbolProcessor {
  private val entityTypeFqns = mutableListOf<String>()
  private val serviceFqns = mutableMapOf<ServiceDescriptor, MutableList<String>>()
  private var shortestPackageName: String? = null
  private var moduleName: String? = null

  @OptIn(KspExperimental::class)
  override fun process(resolver: Resolver): List<KSAnnotated> {
    moduleName = resolver.getModuleName().asString()
    val visitor = object : KSDefaultVisitor<Unit, Unit>() {
      override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        super.visitClassDeclaration(classDeclaration, data)

        when {
          classDeclaration.classKind == ClassKind.OBJECT -> {
            if (classDeclaration.hasSuperType(ENTITY_TYPE_FQN)) {
              classDeclaration.qualifiedName?.asString()?.let {
                entityTypeFqns.add(it)
              }
            }
          }
          !classDeclaration.isPrivate() -> {
            classDeclaration.getAllSuperTypes().forEach { superType ->
              superType.declaration.qualifiedName?.asString()?.let { superTypeFqn ->
                if (superTypeFqn in SERVICE_DESCRIPTORS) {
                  updateTargetPackageIfNeeded(classDeclaration.containingFile)
                  serviceFqns.getOrPut(SERVICE_DESCRIPTORS.getValue(superTypeFqn)) { mutableListOf() }
                    .add(classDeclaration.qualifiedName?.asString()!!)
                }
              }
            }
          }
        }
      }

      override fun visitDeclarationContainer(
        declarationContainer: KSDeclarationContainer,
        data: Unit,
      ) {
        declarationContainer.declarations.forEach { it.accept(this, data) }
      }

      private fun KSClassDeclaration.hasSuperType(superTypeFqn: String): Boolean {
        return getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == superTypeFqn }
      }

      override fun defaultHandler(node: KSNode, data: Unit) {
      }
    }
    val measureTime = measureTime {
      resolver.getNewFiles().forEach { file ->
        file.accept(visitor, Unit)
      }
    }
    logger.info("Processed ${entityTypeFqns.size} entity types and ${serviceFqns.size} plugins in $measureTime")
    return emptyList()
  }

  private fun updateTargetPackageIfNeeded(file: KSFile?) {
    val packageName = file?.packageName?.asString()
    if (packageName != null && (shortestPackageName == null || packageName.length < shortestPackageName!!.length)) {
      shortestPackageName = packageName
    }
  }

  override fun finish() {
    if (entityTypeFqns.isEmpty() && serviceFqns.isEmpty()) return

    val moduleName = moduleName ?: run {
      logger.error("Cannot infer module name. EntityTypes=$entityTypeFqns, Services=$serviceFqns")
      return
    }

    if (entityTypeFqns.isNotEmpty()) {
      codeGenerator.createNewFileByPath(
        dependencies = Dependencies.ALL_FILES,
        path = "entityTypes.txt",
        extensionName = ""
      ).bufferedWriter().use { writer ->
        writer.appendLine("# The file is generated, it contains all entity types declared in the module. The list should be used for validation only.")
        entityTypeFqns
          .sorted()
          .forEach { entityType ->
            writer.appendLine(entityType)
          }
      }
    }

    if (platforms.any { it is JvmPlatformInfo }) {
      serviceFqns.forEach { (serviceDescriptor, implementationFqns) ->
        generateJvmServicesResource(serviceDescriptor.interfaceFqn, implementationFqns)
      }
    }

    if (platforms.any { it.platformName.contains("wasm", ignoreCase = true) }) {
      if (serviceFqns.isNotEmpty()) {
        val packageName = shortestPackageName ?: run {
          logger.error("Cannot infer package name. EntityTypes=$entityTypeFqns, Services=$serviceFqns")
          return
        }

        codeGenerator.createNewFile(
          dependencies = Dependencies.ALL_FILES,
          packageName = packageName,
          fileName = "ServiceProvider.wasm"
        ).bufferedWriter().use { writer ->
          writer.appendLine("package $packageName")
          serviceFqns.forEach { (serviceDescriptor, implementationFqns) ->
            writer.appendLine()
            writer.appendJsServiceAccessor(moduleName, serviceDescriptor.interfaceFqn, serviceDescriptor.returnValue, implementationFqns)
          }
        }
      }
    }
  }

  private fun generateJvmServicesResource(serviceFqn: String, implementationFqns: Collection<String>) {
    if (implementationFqns.isNotEmpty()) {
      codeGenerator.createNewFileByPath(
        dependencies = Dependencies.ALL_FILES,
        path = "META-INF/services/$serviceFqn",
        extensionName = ""
      ).bufferedWriter().use { writer ->
        implementationFqns.forEach { writer.appendLine(it) }
      }
    }
  }

  private fun java.io.Writer.appendJsServiceAccessor(
    moduleName: String,
    serviceFqn: String,
    returnValueFqn: String,
    serviceImplementationFqns: List<String>,
  ) {
    val sanitizedModuleName = sanitizeIdentifier(moduleName)
    val sanitizedServiceFqn = sanitizeIdentifier(serviceFqn)
    appendLine("@JsExport")
    appendLine("fun ${sanitizedModuleName}_findServices_${sanitizedServiceFqn}(): JsReference<List<$returnValueFqn>> {")
    appendLine("  return (listOf(${serviceImplementationFqns.joinToString(", ") { "$it()" }}) as List<$returnValueFqn>).toJsReference()")
    appendLine("}")
  }

  private fun sanitizeIdentifier(string: String?): String? = string?.replace(Regex("[^a-zA-Z0-9_]"), "_")
}
