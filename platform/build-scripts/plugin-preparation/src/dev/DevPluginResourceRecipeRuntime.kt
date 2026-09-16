package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.impl.writeResourceArchiveImpl
import java.nio.file.Files

/** Executes the ordered resource recipe without a product layout. It does not pack library or module jars. */
@ApiStatus.Internal
class DevPluginResourceRecipeRuntime {
  fun compile(
    operations: List<DevPluginPreparationOperation>,
    plan: PluginPackingPlan,
    catalogue: DevPluginArtifactCatalogue,
  ): Map<String, DevPluginPreparationAction> {
    require(operations.isNotEmpty() && operations.all { it.kind == "ordinary-resource" }) { "Expected ordinary resource operations" }
    val normalized = operations.filter { requireNotNull(it.resource).normalizedDirectory }
    require(normalized.isEmpty() || normalized.size == operations.size) { "Normalized and inventoried resource operations must use separate recipes" }
    if (normalized.isNotEmpty()) {
      val artifacts = PreparationCatalogue(catalogue)
      return normalized.associate { operation ->
        val resource = requireNotNull(operation.resource)
        val definition = plan.preparations.single { it.id == operation.id }
        require(artifacts.requireReference(operation.input).kind == "directory") {
          "Normalized resource '${operation.id}' requires a directory artifact"
        }
        operation.id to DevPluginPreparationAction { context ->
          require(context.definition == definition) { "Stale resource action '${definition.id}'" }
          val source = context.inputPath(operation.input)
          require(Files.isDirectory(source)) { "Normalized resource '${operation.id}' is not a directory" }
          withResourceScratch { scratch ->
            val archive = writeResourceArchiveImpl(source, scratch.resolve("resource.zip"))
            listOf(completedResource(context, resource.outputs.single(), Files.readAllBytes(archive)))
          }
        }
      }
    }
    val configuration = requireNotNull(operations.first().resource)
    val resources = operations.map { operation ->
      val resource = requireNotNull(operation.resource)
      DevPluginResourceSpec(resource.moduleName, resource.resourcePath, resource.relativeOutputPath, resource.packToZip)
    }
    val sources = operations.map { operation ->
      val resource = requireNotNull(operation.resource)
      DevPluginResourceSource(
        moduleName = resource.moduleName, resourcePath = resource.resourcePath, input = operation.input,
        entries = resource.entries.map { DevPluginResourceEntry(it.path, it.kind, it.mode, it.symlinkTarget) },
        timestampMetadata = resource.timestampMetadata,
      )
    }
    val adapter = DevPluginResourcePreparationCore(configuration.mainModule, resources, catalogue, sources, configuration.idPrefix)
    require(adapter.recipeOperations() == operations) { "The resource recipe differs from the original layout" }
    return adapter.compileActions(plan, catalogue)
  }
}
