package fleet.bundles

private fun deps(vararg pairs: Pair<LayerSelector, LayerSelector>): Map<LayerSelector, List<LayerSelector>> =
  pairs.groupBy(keySelector = { (dependant, _) -> dependant }, valueTransform = { (_, dependency) -> dependency })

val dockLayer = LayerSelector("dock")
val frontendApiLayer = LayerSelector("frontendApi")
val frontendImplLayer = LayerSelector("frontendImpl")
@Deprecated(message = "use `frontendImpl` instead", replaceWith = ReplaceWith("frontendImplLayer"))
val frontendLayer = LayerSelector("frontend")
val workspaceApiLayer = LayerSelector("workspaceApi")
val workspaceImplLayer = LayerSelector("workspaceImpl")
@Deprecated(message = "use `workspaceImpl` instead", replaceWith = ReplaceWith("workspaceImpl"))
val workspaceLayer = LayerSelector("workspace")
val commonApiLayer = LayerSelector("commonApi")
val commonImplLayer = LayerSelector("commonImpl")
@Deprecated(message = "use `commonImpl` instead", replaceWith = ReplaceWith("commonImpl"))
val commonLayer = LayerSelector("common")
val testLayer = LayerSelector("test")

@Suppress("DEPRECATION")
val deprecatedLayers = listOf(
  frontendLayer,
  commonLayer,
  workspaceLayer,
)

@Suppress("DEPRECATION")
//this describes dependencies between layers inside a plugin
val internalReadability: Map<LayerSelector, List<LayerSelector>> = deps(
  frontendImplLayer to frontendApiLayer,
  frontendImplLayer to commonImplLayer,
  frontendImplLayer to commonApiLayer,
  frontendImplLayer to dockLayer,
  frontendApiLayer to commonApiLayer,
  workspaceImplLayer to workspaceApiLayer,
  workspaceImplLayer to commonApiLayer,
  workspaceImplLayer to commonImplLayer,
  workspaceApiLayer to commonApiLayer,
  frontendLayer to commonLayer,
  frontendLayer to dockLayer,
  workspaceLayer to commonLayer
)

@Suppress("DEPRECATION")
//this describes dependencies between plugins
val externalReadability: Map<LayerSelector, List<LayerSelector>> = deps(
  frontendImplLayer to frontendApiLayer,
  frontendImplLayer to commonApiLayer,
  frontendApiLayer to frontendApiLayer,
  frontendApiLayer to commonApiLayer,

  workspaceImplLayer to workspaceApiLayer,
  workspaceImplLayer to commonApiLayer,
  workspaceApiLayer to workspaceApiLayer,
  workspaceApiLayer to commonApiLayer,

  commonApiLayer to commonApiLayer,
  commonImplLayer to commonApiLayer,

  //working with old plugins (to be removed)
  workspaceApiLayer to workspaceLayer,
  workspaceImplLayer to workspaceLayer,
  workspaceLayer to workspaceApiLayer,

  workspaceLayer to workspaceLayer,
  frontendApiLayer to frontendLayer,
  frontendImplLayer to frontendLayer,
  frontendLayer to frontendApiLayer,
  frontendLayer to frontendLayer,
  frontendLayer to commonLayer,
  frontendLayer to dockLayer,
  commonLayer to dockLayer,
  workspaceLayer to commonLayer,
  commonLayer to commonLayer,
  frontendLayer to commonApiLayer,
  workspaceLayer to commonApiLayer,
  commonLayer to commonApiLayer,
  commonApiLayer to commonLayer,
  commonImplLayer to commonLayer,
  dockLayer to dockLayer,
)

@Suppress("DEPRECATION")
val frontendLayers: Set<LayerSelector> = setOf(
  dockLayer,
  frontendApiLayer,
  frontendImplLayer,
  commonApiLayer,
  commonImplLayer,
  frontendLayer,
  commonLayer,
)

@Suppress("DEPRECATION")
val workspaceLayers: Set<LayerSelector> = setOf(
  dockLayer,
  workspaceApiLayer,
  workspaceImplLayer,
  commonApiLayer,
  commonImplLayer,
  workspaceLayer,
  commonLayer,
)

fun Collection<LayerSelector>.sortByInternalDependencies(): List<LayerSelector> {
  val currentStack = mutableListOf<LayerSelector>()

  fun logCycle(): Nothing {
    currentStack.reverse()
    val endCycleIndex = currentStack.lastIndexOf(currentStack.first())
    throw IllegalArgumentException(
      "Cyclic dependency: ["
      + currentStack.subList(0, endCycleIndex + 1).joinToString(separator = " <- ") { it.selector }
      + "] <- "
      + currentStack.subList(endCycleIndex + 1, currentStack.size).joinToString(separator = " <- ") { it.selector })
  }

  val times = HashMap<LayerSelector, Int>()
  var timestamp = 0
  fun dfs(selector: LayerSelector) {
    when (times[selector]) {
      null -> {
        currentStack.add(selector)
        times[selector] = -1
        internalReadability[selector]?.forEach { dependency ->
          dfs(dependency)
        }
        times[selector] = timestamp++
        currentStack.removeLast()
      }
      -1 -> {
        currentStack.add(selector)
        logCycle()
      }
    }
  }
  forEach { dfs(it) }
  return times.entries.sortedBy { it.value }.map { it.key }
}
