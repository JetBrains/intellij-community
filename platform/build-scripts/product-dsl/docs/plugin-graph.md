# PluginGraph Model and DSL

This document describes the `PluginGraph` - a unified graph model for plugin/module/product relationships, and its type-safe DSL for traversal.

## Overview

### Why Custom Graph (not GraphStore)?

GraphStore uses `ReentrantLock` for index access, causing deadlocks with coroutines. Coroutines can suspend on thread A and resume on thread B, breaking lock ownership. PluginGraph is **lock-free via immutability**.

### Design Principles

- **Lock-free**: Immutable after construction, mutations return new instances
- **Efficient**: Uses fastutil primitive collections
- **Type-safe**: Compile-time checked traversals via value classes
- **GC-free**: Inline lambdas + value classes = zero allocation overhead

## Graph Structure

### Node Types

| Type          | Constant              | Description                 |
|---------------|-----------------------|-----------------------------|
| Product       | `NODE_PRODUCT`        | IDEs (IDEA, WebStorm, etc.) |
| Plugin        | `NODE_PLUGIN`         | Bundled plugins             |
| ContentModule | `NODE_CONTENT_MODULE` | Content modules             |
| ModuleSet     | `NODE_MODULE_SET`     | Groups of modules           |
| Target        | `NODE_TARGET`         | JPS/Bazel build targets     |

### Edge Types

```
Product --bundles--> Plugin (production)
Product --bundlesTest--> Plugin (test plugin)
Product --includesModuleSet--> ModuleSet
Product --containsContent(loadingMode)--> ContentModule
Product --allowsMissing--> ContentModule (allowed missing in validation)
Plugin --containsContent(loadingMode)--> ContentModule
Plugin --containsContentTest(loadingMode)--> ContentModule
Plugin --mainTarget--> Target (plugin's build target)
Plugin --dependsOnPlugin(optional, legacy/modern flags)--> Plugin (plugin.xml <plugin> deps)
Plugin --dependsOnContentModule--> ContentModule (plugin.xml <module> deps)
ModuleSet --containsModule(loadingMode)--> ContentModule
ModuleSet --nestedSet--> ModuleSet (nested hierarchy)
ContentModule --backedBy--> Target (build target backing content module)
ContentModule --moduleDependsOn--> ContentModule (runtime deps from XML)
Target --dependsOn--> Target (build target dependencies)
```

### Dependency Edge Naming

Dependency edges intentionally use different names because they live on different layers of the graph:

- `EDGE_TARGET_DEPENDS_ON`: build-target deps (JPS today, Bazel later), with scope packed into adjacency entries.
- `EDGE_CONTENT_MODULE_DEPENDS_ON` / `EDGE_CONTENT_MODULE_DEPENDS_ON_TEST`: runtime deps between content modules.
- `EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN`: plugin.xml `<plugin>` deps (optional + legacy/modern format flags packed into the edge).
- `EDGE_PLUGIN_XML_DEPENDS_ON_CONTENT_MODULE`: plugin.xml `<module>` deps between plugins and content modules.

Keeping these separate avoids mixing node kinds (Target vs ContentModule vs Plugin) and keeps traversal APIs type-safe.

### Storage Model

Nodes use **columnar storage**:
- `names[]` - node name strings
- `kinds[]` - node type constants

Edges use **per-type adjacency maps**:
- `Int2ObjectOpenHashMap<IntArrayList>` for out-edges and in-edges

Properties are **sparse maps** (only nodes/edges that have them).

Indexes provide **O(1) lookup** by name+kind.

## Store Updates

The graph uses `@Volatile` store swapping for thread-safe updates:

```kotlin
graph.updateWithModuleDependencies(results)  // swaps internal store
```

This is called by `ContentModuleDependencyGenerator` after computing effective dependencies.

## DSL Usage

PluginGraph provides a GC-free DSL for traversal.

### GC-Free Natural Language API

Zero-allocation iteration using invoke operators:

```kotlin
graph.query {
  // Iterate all products
  products { product -> println(product.name()) }
  
  // Iterate test plugins only
  plugins { plugin -> if (plugin.isTest) println(plugin.name()) }
  
  // Nested traversal - type-safe edge chaining
  products { product ->
    product.bundles { plugin ->          // Product → Plugin
      plugin.containsContent { module, _ ->  // Plugin → Module (+ loading mode)
        println(module.name())
      }
    }
  }
}
```

**Available node iterators:**

| Object           | Iterates            |
|------------------|---------------------|
| `products`       | All `ProductNode`   |
| `plugins`        | All `PluginNode`    |
| `contentModules` | All `ModuleNode`    |
| `moduleSets`     | All `ModuleSetNode` |
| `targets`        | All `TargetNode`    |

**Available edge properties:**

| Node Type       | Property                 | Target Type        |
|-----------------|--------------------------|--------------------|
| `ProductNode`   | `bundles`                | `PluginNode`       |
| `ProductNode`   | `bundlesTest`            | `PluginNode`       |
| `ProductNode`   | `includesModuleSet`      | `ModuleSetNode`    |
| `ProductNode`   | `containsContent`        | `ModuleNode`       |
| `ProductNode`   | `allowsMissing`          | `ModuleNode`       |
| `PluginNode`    | `containsContent`        | `ModuleNode`       |
| `PluginNode`    | `containsContentTest`    | `ModuleNode`       |
| `PluginNode`    | `mainTarget`             | `TargetNode`       |
| `PluginNode`    | `dependsOnPlugin`        | `PluginDependency` |
| `PluginNode`    | `dependsOnContentModule` | `ModuleNode`       |
| `ModuleSetNode` | `containsModule`         | `ModuleNode`       |
| `ModuleSetNode` | `nestedSet`              | `ModuleSetNode`    |
| `ModuleNode`    | `backedBy`               | `TargetNode`       |
| `ModuleNode`    | `dependsOn`              | `ModuleNode`       |
| `ModuleNode`    | `dependsOnTest`          | `ModuleNode`       |
| `TargetNode`    | `dependsOn`              | `TargetNode`       |

`PluginDependency` exposes `target(): PluginNode`, `isOptional`, and format flags (`hasLegacyFormat`, `hasModernFormat`).

Content edges (`containsContent`, `containsContentTest`, `containsModule`) invoke as
`(ModuleNode, ModuleLoadingRuleValue)` to expose loading mode without extra lookups.

`ModuleNode.contentProductionSources { }` traverses only production content sources
(`EDGE_CONTAINS_CONTENT` and `EDGE_CONTAINS_MODULE`). Test plugin content
(`EDGE_CONTAINS_CONTENT_TEST`) is excluded by design.

### Collecting Results

```kotlin
val moduleNames = HashSet<String>()
graph.query {
  products { product ->
    product.bundles { plugin ->
      plugin.containsContent { module, _ ->
        moduleNames.add(module.name())
      }
    }
  }
}
```

### Recursive Traversals

```kotlin
graph.query {
  // All modules in a module set hierarchy
  moduleSet("essential")?.let { set ->
    set.modulesRecursive(filter = { _ -> true }) { module ->
      println(module.name())
    }
  }
  
  // Transitive module dependencies
  contentModule("intellij.platform.lang")?.let { mod ->
    mod.transitiveDeps { dep -> 
      println(dep.name())
    }
  }
}
```

## Design Decisions

### Why Sealed Interfaces for NodeKind?

- Compiler knows all subtypes → exhaustive `when` expressions
- IDE shows all available options in completion

### Why Lowercase Data Objects (`products`, `plugins`, etc.)?

- Enables natural language syntax: `products { }` reads like "for each product"
- Kotlin convention: instances (objects) are lowercase, types are PascalCase

### Why EdgeInvoker with Packed Long?

Edge traversal needs both `edgeId` AND `sourceId`. Kotlin inline value classes can only wrap ONE property.

**Solution:** Pack `[edgeId:16][sourceId:32]` into Long → zero allocation.

```kotlin
@JvmInline
value class EdgeInvoker<out T : TypedNode>(val packed: Long) {
  val edgeId: Int get() = (packed ushr 32).toInt()
  val sourceId: Int get() = packed.toInt()
}
```

Type parameter `T` is phantom (compile-time only) for type safety.

Content edges use a specialized invoker to expose loading mode from packed adjacency entries.

### Why Invoke Operator with Explicit Parameter?

- `products { product -> }` calls `products.invoke { }` via operator overloading
- Callbacks receive the target node explicitly for clear, non-implicit chaining
- Chained traversals remain GC-free and type-safe without hidden receivers
- Content edges use `(ModuleNode, ModuleLoadingRuleValue)` callbacks to surface loading mode

### Alternatives Considered

| Alternative                           | Why Rejected                            |
|---------------------------------------|-----------------------------------------|
| `forEach(Products) { }`               | Explicit but verbose, less natural      |
| `graph.products.forEach(graph) { }`   | Redundant graph parameter               |
| Explicit methods per edge             | Too many methods (~100)                 |
| Sequence-based API only               | Allocates iterator objects on each call |
| `NodeSet(kind, wrap)` as inline class | Can't have 2 properties in value class  |

## Typed Node Wrappers

All node types are `@JvmInline value class` wrappers around the int ID:

```kotlin
@JvmInline value class ProductNode(override val id: Int) : TypedNode
@JvmInline value class PluginNode(override val id: Int) : TypedNode
@JvmInline value class ModuleNode(override val id: Int) : TypedNode
@JvmInline value class ModuleSetNode(override val id: Int) : TypedNode
@JvmInline value class TargetNode(override val id: Int) : TypedNode
```

This provides:
- **Compile-time safety**: Wrong edges on wrong node types won't compile
- **Zero runtime overhead**: Value classes are unboxed at runtime

## Node Flags

Flags are packed into upper bits of the `kinds` int:

| Flag           | Constant                   | Description                           |
|----------------|----------------------------|---------------------------------------|
| Test plugin    | `NODE_FLAG_IS_TEST`        | Plugin is a test plugin               |
| Self-contained | `NODE_FLAG_SELF_CONTAINED` | Module set is self-contained          |
| DSL-defined    | `NODE_FLAG_IS_DSL_DEFINED` | Plugin has auto-computed dependencies |

## Debugging

Use `PluginGraphDebug` for interactive debugging and troubleshooting:

```kotlin
with(PluginGraphDebug) {
  pluginGraph.traceDependencyPath("intellij.platform.lang", "intellij.libraries.hamcrest")
  pluginGraph.compareProdVsTestDeps("intellij.platform.lang")
}
```

## Related Documentation

- [validation-rules.md](validation-rules.md) - Validation rules including resolved vs. orphan modules
- [architecture-overview.md](architecture-overview.md) - Pipeline architecture overview
