package com.jetbrains.rhizomedb.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.codegen.AnnotationCodegen.Companion.annotationClass
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.backend.js.utils.nameWithoutExtension
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.io.path.Path
import kotlin.io.path.pathString

internal val RHIZOMEDB_FQN = FqName.fromSegments(listOf("com", "jetbrains", "rhizomedb"))
internal val RHIZOMEDB_FQN_IMPL = RHIZOMEDB_FQN.child(Name.identifier("impl"))

class EntityTypeRegistrationGenerator(
  val jvmOutputDir: String?,
  val readProvider: ((String) -> List<String>)? = null,
  val writeProvider: ((String, Collection<String>) -> Unit)? = null
) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    EntityTypeRegistrationGeneratorImpl(
      jvmOutputDir, 
      pluginContext, 
      moduleFragment,
      readProvider,
      writeProvider
    ).generate()
  }
}

private class EntityTypeRegistrationGeneratorImpl(
  val jvmOutputDir: String?,
  pluginContext: IrPluginContext,
  moduleFragment: IrModuleFragment,
  val readProvider: ((String) -> List<String>)? = null,
  val writeProvider: ((String, Collection<String>) -> Unit)? = null
) : DeclarationBasedExtension(pluginContext, moduleFragment) {

  val entityType = classByName("EntityType", RHIZOMEDB_FQN).owner
  val entityTypeProvider = classByName("EntityTypeProvider", RHIZOMEDB_FQN_IMPL).owner
  val listOfFn =
    pluginContext.referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("listOf"))).first {
      it.owner.parameters.singleOrNull()?.varargElementType != null
    }


  /**
   * Generate [META_INF_FILE] and [META_INF_CACHE_FILE]
   *
   * First one contains FQN for the entity type provider classes and can be used to load them on runtime.
   * Each file with an entity type has such provider.
   * The second one stores meta-information required for consistency during IC.
   *
   * [META_INF_CACHE_FILE] stores tuples `<fqn_provider>;<kind_of_ref>;<ref>`.
   * <ref> using to detect file deletion to remove FQN from [META_INF_FILE].
   * It can be a class or a property.
   */
  // TODO: support properties like
  // val MyEntityType = object : EntityType<...>(...) { }
  fun generate() {
    val files = moduleFragment.files.toSet()

    // Process declarations and generate files
    val res = declarations
      .filterIsInstance<IrClass>()
      .filter { it.isObject && it.isSubclassOf(entityType) }
      .groupBy { it.file }
      .toSortedMap { a, b -> a.fileEntry.name.compareTo(b.fileEntry.name) }
      .map { (file, decls) ->
        val providerClass = addEntityProviderClass(file, decls)

        // Non-jvm handling: @EagerInitialization properties that populate a list of providers
        if (includesNonJvmTarget()) {
          addEagerInitializedProperty(moduleFragment.name.asStringStripSpecialMarkers(),  file, providerClass)
        }

        // Return generated class + name for META-INF cache
        providerClass to decls.first().classIdOrFail.asString()
      }

    // Jvm handling: META-INF files loaded with reflection
    if (includesJvmTarget() && jvmOutputDir != null) {
      val newEntries = res.map { (providerClass, firstEntityClassName) ->
        val fqn = providerClass.jvmLikeName()
        "$fqn;C;${firstEntityClassName}"
      }

      // Step 1: cache file
      val cacheFilePath = Path("$jvmOutputDir/META-INF/$META_INF_CACHE_FILE").normalize().pathString
      val cacheProcessor = if (readProvider != null && writeProvider != null) {
        MetaInfListProcessor(cacheFilePath, readProvider, writeProvider)
      } else {
        MetaInfListProcessor(cacheFilePath)
      }

      val result = cacheProcessor.readAndUpdate(newEntries) { row ->
        val (_, kind, fqn) = row.split(';')
        when (kind) {
          "P" -> false // TODO: support properties
          "C" -> pluginContext.referenceClass(ClassId.fromString(fqn)).let { refClass ->
            // 1. Failed to find ref - it is removed, file changed. We will regenerate the provider for file
            // 2. The ref file is null - file not changed
            // 3. The ref file is not in IC files set - file not changed
            refClass != null && refClass.owner.fileOrNull !in files
          }

          else -> error("Unknown ref kind $kind")
        }
      }

      // Step 2: actual list of providers dumped from the previous result
      val metaInfFilePath = Path("$jvmOutputDir/META-INF/$META_INF_FILE").normalize().pathString
      val metaInfProcessor = if (readProvider != null && writeProvider != null) {
        MetaInfListProcessor(metaInfFilePath, readProvider, writeProvider)
      } else {
        MetaInfListProcessor(metaInfFilePath)
      }

      metaInfProcessor.writeOrDelete(result.map { it.substringBefore(';') })
    }
  }

  private fun addEntityProviderClass(file: IrFile, decls: List<IrClass>): IrClass {
    val apiStatusAnnotations = decls.map { it.extractApiStatusAnnotations() }.flatten().distinctBy { it.annotationClass.kotlinFqName.asString() }
    return file.addClass {
      name = Name.identifier("${file.nameWithoutExtension}EntityTypeProvider")
      kind = ClassKind.OBJECT
    }.apply {
      // TODO: it is not entirely correct, but for IJ it should work, and shouldn't affect Fleet at all
      //   if any of the declarations contain ApiStatus annotation, EntityTypeProvider class also will have this annotation
      annotations = annotations + apiStatusAnnotations
      superTypes = listOf(entityTypeProvider.defaultType)
      addSimpleDelegatingConstructor(
        pluginContext.irBuiltIns.anyClass.owner.primaryConstructor!!,
        pluginContext.irBuiltIns, true
      )
      addProperty {
        name = Name.identifier("entityTypes")
      }.apply {
        val listOfEntityTypes = pluginContext.irBuiltIns.listClass.typeWith(entityType.defaultType)
        addGetter { returnType = listOfEntityTypes }.apply {
          parameters = listOf(createDispatchReceiverParameterWithClassParent()) + parameters
          setBody {
            +irReturn(
              irCall(listOfFn).apply {
                arguments[0] = irVararg(entityType.defaultType, decls.map { irGetObject(it.symbol) })
              }
            )
          }
        }
      }
    }
  }

  fun IrFunction.setBody(builder: IrBlockBodyBuilder.() -> Unit) {
    body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody { builder() }
  }

  /**
   * Add field marked with @EagerInitialization, which will initialize its value on startup for non-jvm targets,
   * effectively populating a list of entity types to register.
   *
   * Should not be used on JVM-only targets (EagerInitialization annotation is absent there).
   */
  private fun addEagerInitializedProperty(moduleName: String, file: IrFile, targetClass: IrClass) {
    val registerProviderFunction = funByName("registerEntityTypeProvider", RHIZOMEDB_FQN_IMPL)
    val eagerAnnotation = classByName("EagerInitialization", FqName("kotlin"))

    with(pluginContext.irFactory) {
      // private val MyEntityTypeRegisterToken
      buildProperty {
        name = Name.identifier("${file.nameWithoutExtension}EntityTypeRegisterToken")
        isVar = false
        visibility = DescriptorVisibilities.PRIVATE
      }.apply {
        // @EagerInitialization
        annotations += with(DeclarationIrBuilder(pluginContext, symbol)) {
          irCall(eagerAnnotation.owner.primaryConstructor!!.symbol)
        }

        // Needs to be called before addBackingField
        file.addChild(this)

        // = markForRegistration(MyEntityTypeProvider)
        addBackingField {
          name = Name.identifier("_${file.nameWithoutExtension}EntityTypeRegisterToken")
          type = pluginContext.irBuiltIns.booleanType
          isFinal = true
          isStatic = true
          visibility = DescriptorVisibilities.PRIVATE
          origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD
        }.let {
          it.initializer = createExpressionBody(with(DeclarationIrBuilder(pluginContext, symbol)) {
            irCall(registerProviderFunction).apply {
              arguments[0] = irString(moduleName)
              arguments[1] = irGetObject(targetClass.symbol)
            }
          })
        }
      }
    }
  }

  private fun IrDeclarationParent.addClass(builder: IrClassBuilder.() -> Unit) =
    pluginContext.irFactory.buildClass(builder).apply {
      parent = this@addClass
      (this@addClass as IrDeclarationContainer).declarations += this
      createThisReceiverParameter()
    }

}

internal fun IrAnnotationContainer.extractApiStatusAnnotations(): List<IrConstructorCall> {
  return annotations.filter { it.annotationClass.kotlinFqName.startsWith(FqName.fromSegments(listOf("org", "jetbrains", "annotations", "ApiStatus"))) }
}

private const val META_INF_FILE = "com.jetbrains.rhizomedb.impl.EntityTypeProvider.txt"
private const val META_INF_CACHE_FILE = "com.jetbrains.rhizomedb.impl.EntityTypeProvider.cache"
