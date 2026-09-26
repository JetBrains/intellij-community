# Content Module Copy Conflict Validation

Entry point: `ContentModuleCopyConflictValidator` (`NodeIds.CONTENT_MODULE_COPY_CONFLICT_VALIDATION`).

## Overview

`loading="embedded"` puts a content module into the main classloader of the owning plugin.

A plugin can also register a library module as its own private content. It declares the module in a `<content>` block with no `namespace` attribute. `PluginModuleId.toActualId` then gives that copy an implicit namespace per plugin. The duplicate check of `PluginContentDuplicatesValidator` compares runtime IDs, so it sees no clash.

The failure comes when one content module can reach two such copies. That module then sees the same class from two classloaders, and the JVM raises a `LinkageError`. Commit `37c4a5fd` made this shape for `intellij.libraries.qodana.sarif`. It dropped every taint finding in Qodana for JVM. See https://youtrack.jetbrains.com/issue/QD-15883.

An isolated private copy stays legal. The plugin model supports a private library copy per plugin. This validator reports a copy only when a content module can reach two of them.

The candidate rule counts an **embedded** declaration and ignores the namespace, so a reported name need not have a private declaration at all. Two declarers that both carry a namespace are reported as well, and `intellij.libraries.test.discovery` is such a case. Read a report as "two embedded copies", not as "two private copies".

The rule reads an embedded declaration only. A declaration that is not embedded is a stated non-goal, not an approximation. Read [Non-goals](#non-goals) for the reason.

## Which route to pick

A library module takes the lowest route that works. `build/decisions/0005-a-library-copy-belongs-to-the-plugin-that-owns-its-api.md`
holds the decision; this is the short form.

1. **Isolate.** One plugin uses the library, so that plugin registers the module as private content. This rule
   never reports that shape, because nothing outside the owner can see the copy.
2. **Reuse.** The library types cross a plugin boundary through an API. The plugin that owns the API holds the
   one copy, and every dependent plugin reuses it. The owner publishes a wrapper content module in the
   `jetbrains` namespace, and a foreign plugin names the wrapper. `intellij.qodana.sarif` is that wrapper for
   SARIF.
3. **Core.** Neither route works, so the module joins a shared module set. This is the last resort, because a
   set ships the library to every product.

A report means the shape sits at rung one while the code needs rung two. Ask whether a value of a library type
passes between two plugins. An extension point signature, a public parameter and a public return type all count.

Ask that question once per **boundary**, not once per library. A route belongs to a boundary. One library can
sit at rung two on one boundary and at rung one on another, and SARIF is that case. The Qodana plugin holds the
copy that crosses its own API. The DFA Analysis plugin holds a second copy, which is not embedded, for
`intellij.dfa.analysis.rml.utils`. No SARIF value crosses the OpenGrep to Qodana boundary, so that second copy
is rung one. A rung-one copy leaves `loading="embedded"` out, which is what keeps it out of a main classloader
that a foreign plugin can reach.

## Inputs

- `Slots.CONTENT_MODULE_PLAN`, for the `writtenPluginDependencies` of each content module. The analysis needs this slot because a content module `<plugin id="..."/>` dependency is not a graph edge. The slot also guarantees that `ContentModuleDependencyPlanner` published the module dependency edges before validation runs.
- Graph: product bundling edges, plugin content edges with a namespace, and the dependency edges of each content module. The walk also reads plugin-to-plugin dependency edges, plugin-to-module dependency edges, and plugin alias edges.

## Three sets, three questions

The graph keys a content module by name and holds one node per name, so a node can stand for more than one
runtime module. The rule needs three things from that, and they answer three different questions. They once
coincided, and treating them as one produced the same defect four times. Keep them apart.

| | Question it answers | Definition |
|---|---|---|
| **Candidate** | What is reportable? | A name that two or more distinct bundled plugins declare with an **embedded** copy. |
| **Expansion** | Can a reference resolve the copy the walk stands on? | A test on the walk, not a set. See below. |
| **Seer** | Does this node stand for one module? | The union of the candidate names and the names that two or more bundled plugins declare, **whatever the loading mode and whatever the namespace**. |

Privacy plays no part in the seer set. The union is equal to the ambiguous half today, and that is safe rather
than lucky. A candidate needs two distinct plugins with an embedded declaration, so it has two distinct
declarers. It is therefore already ambiguous. It stays written as a union, so a later change to the candidate
rule cannot break the equality unnoticed.

The candidate set and the seer set read the bundled production plugins of the product. A test plugin
declaration is out of scope, and counting one would change every answer. Counting repo-wide instead of per
product would too. Test plugins declare `intellij.libraries.qodana.sarif` in a shared namespace, and letting
those count would reopen the QD-15883 false hops. The expansion test reads a wider input, below.

### The expansion test

The walk expands a content module only when a reference from outside can resolve the copy that the walk stands
on. Which copy that is depends on **how the walk arrived**, not on the kind of the parent node. Three arrivals
have a plugin as the parent, and only one of them means that the plugin declares the module as its own content.

- **`OWNED_CONTENT`** — the arriving plugin declares this module in its `<content>`, through
  `containsContent`. The walk stands on that plugin's copy. A reference from outside the plugin resolves that
  copy only when the plugin gave the name a namespace. A reference from inside the plugin needs no expansion,
  because `containsContent` already found that module. So: expand unless the arriving plugin declared this name
  privately.
- **Every other arrival**, which is `PLUGIN_DEPENDENCY`, `MODULE_DEPENDENCY`, and the alias hop. The walk
  stands on whichever copy a reference to the name resolves. So: expand only when the name is resolvable.
  A `<plugin id="..."/>` arrival belongs here and not in the first group. The named plugin need not declare
  the name at all, so the private test would ask about a plugin that never declared it. The alias hop belongs
  here for the same reason, and an alias node declares no content of its own.

**Resolvable by name** means a reference can resolve a copy somewhere in the product. Three routes qualify:

1. A bundled plugin declares the name with a namespace.
2. The product declares it as direct content with a namespace. A product spec can also declare a private
   module, through `ProductModulesContentSpecBuilder.privateModule`, so product content is read with its
   namespace rather than assumed to be shared.
3. A module set of the product holds it. A module set is always shared: `ModuleSetBuilder.module` records that
   `buildModuleSetXml` emits one `<content namespace="jetbrains">` block, so a module set cannot hold a private
   module.

Routes 2 and 3 matter because every ordinary platform module arrives that way. Without them the second group
above answers "not resolvable" for the whole platform, and the walk stops at each of those modules.

This is a per-walk test rather than a set, because the answer depends on which copy the walk reached. A name
private to plugin A and shared by plugin B shows why. It must stop the walk rooted at A, and it must not stop
the walk rooted at B.

### The count of reports is not monotone in the candidate set

**A smaller candidate set can produce more reports.** This is counter-intuitive and it has already bitten once,
so it is worth stating plainly.

The walk is bounded by the expansion test, not by the candidate set. While the two were the same set, narrowing
the candidate rule also narrowed what stopped the walk. Restricting the rule to embedded copies removed
`intellij.libraries.completion.ranking.js` from the candidate set, because only one of its two declarers uses
`loading="embedded"`. It therefore stopped bounding the walk, the walk travelled through it, and
a new report appeared on `intellij.libraries.completion.ranking.sh` that the wider rule never produced.

The three sets are separate for this reason. Narrowing what the rule reports can no longer widen where the walk
goes.

## Rules

- For each product, over the bundled production plugins:
  - Group every **embedded** content declaration by content module name. Skip a declaration that is not embedded.
  - Keep a name that two or more distinct plugins declare as embedded content. Such a name is a candidate. A candidate name is rare, which keeps the rest of the work small.
  - For each candidate name, build the set of content modules that see the copy of each owner. An embedded copy lives in the main classloader of the owning plugin. The set therefore holds every content module of the product that reaches that main classloader, subject to the expansion test above.
  - Report a content module that appears in the set of two or more owners. The report names the module, the candidate name, and one entry per reachable copy. Each entry holds the plugin, the runtime ID, and the path that reaches the copy.
  - Skip every name in the seer set. Such a node stands for more than one runtime module, so it is never a module that sees a copy.
- The walk models the runtime class visibility of the plugin system:
  - A content module sees the main classloader of its own plugin.
  - A content module sees the classloader of each module dependency and of each plugin dependency.
  - A plugin main classloader sees the classloader of each plugin dependency and of each module dependency.
  - A dependency can name a plugin by an alias, so the walk takes the `EDGE_PLUGIN_DECLARES_ALIAS` edge as an extra hop.
  - The walk stays inside the product. It skips a content module that the product does not ship. It also skips a plugin that the product does not bundle.
- The candidate filter needs two distinct plugins. The owner list of a report can still hold two declarations of one plugin under different namespaces. That shape is also a defect, because one main classloader then holds two class sets.

## Suppression and allowlists

`ErrorCategory.CONTENT_MODULE_COPY_CONFLICT` is suppressible per name. It is no longer a hard failure.

**The node applies the config itself.** `ContentModuleCopyConflictValidator` reads
`model.suppressionConfig.contentModuleCopyConflicts` and drops a listed name before it emits anything, the way
`TestLibraryScopeValidator` reads the config in-node. This matters for two reasons. The pipeline filter on
`suppressionKey` runs in `Pipeline.aggregate`, outside the node, so a unit test that calls the rule directly
never sees it. And the grain has to be per name, which only the node can decide.

The error also carries `suppressionKey = "contentModuleCopyConflict:<duplicated name>"`, so the generic pipeline
path agrees with the node. The spelling follows `suppressedErrors`, whose only producer is
`"nonStandardRoot:$moduleName"`: a camelCase prefix, a colon, then the content module name. The camelCase prefix
also keeps the key apart from the kebab-case `errorId()`, which is
`content-module-copy-conflict:$product:$name`.

The allowlist lives in the `contentModuleCopyConflicts` map of `platform/buildScripts/suppressions.json`. The
key is the duplicated content module name and the value carries a free-text `reason` that records the owners and
the kind of entry. Read [Allowlist entries](#allowlist-entries) for the two kinds and for the current list.

Suppression is **by name**, on purpose. A new owner of a listed name stays silent, because the copies of that
name are already recorded debt. A name that is not listed still fails the build, which is the case the rule
guards. One error covers one name in one product, so a listed name never hides another name.

`SuppressionConfigGenerator` copies the map without a change, like `validationExceptions`. An entry therefore
survives regeneration, and the generator neither adds nor removes one. A stale entry is not reported, so a
`DEBT:` entry has to be removed by hand once the copies go away. An `INTENTIONAL:` entry is never stale.

## Output

- `ContentModuleCopyConflictError`, one per candidate name per product. The error ID is `content-module-copy-conflict:$context:$name`, where the context is the product name.
- For each reported module the error prints every reachable copy, with the owning plugin, the runtime ID, and the path. It also prints the `suppressions.json` entry that would grandfather the name.
- A path stops at 64 steps. A longer path arrives truncated in the report.

## Auto-fix

- None. The error text gives two manual fixes. Declare the module once as shared content of a module set, and remove the private copies. Or break the dependency path, so that no module reaches two copies. The text also shows the `suppressions.json` entry that grandfathers the name.

## Known limits

- A node in the seer set is never reported as the module that sees a copy, even when it really does see two. Such a node stands for more than one runtime module, so naming it in a report would name something that does not exist at runtime. The modules that depend on it are still reported, so the conflict is not lost, only attributed one hop further out.
- The expansion test decides per walk, from how the walk reached a node, so it cannot use a copy that no reference names. A name declared privately by one plugin and privately again by another therefore stops both walks. That is correct for each walk on its own, and it means a consumer that names such a name is never followed. There is no copy for it to resolve, so no hop is lost.
- **One arrival per node per walk, and the first arrival wins.** `visitedModules` records a node once. A second arrival at that node is rejected, and its hop is discarded. Take a name `M` that plugin `P` declares privately and plugin `Q` declares with a namespace. `M` also declares `<plugin id="P">`. In the walk rooted at `P`, `M` arrives first as `OWNED_CONTENT` through `containsContent`, because the plugin turn runs that traversal first. The later `PLUGIN_DEPENDENCY` arrival is dropped. `canExpand` therefore applies the private test and refuses to expand, although `M` is resolvable through `Q`. The walk then misses the modules that resolve `Q`'s shared copy and reach `P` through `M`'s own plugin dependency. **The direction is one way: it can only miss a report, never invent one.** That is why this is documented rather than fixed.

  This is a different surface from the five conflations that came before it. `ArrivalKind` made the routing honest, and it did not give the walk room to hold two arrivals at one node. The conflation moved from a set, to a boolean, to the visit bookkeeping. Do not read `ArrivalKind` as having closed the whole class.

  The fix shape, so nobody re-derives it. On an already-visited node, compare the new arrival with the recorded one. Keep the arrival that permits expansion, or expand on the union of the arrivals. That is a change to the visit rule, not to `canExpand`. It needs its own measurement, because it can only **add** reports.

  No test covers this limit. The test phase tried and could not build a case that discriminates it.
- The rule reads the production side of a product only. It walks `ProductNode.bundles` and the production content edges. A declaration by a bundled test plugin is out of scope, and a test plugin never appears as an owner or as a hop.
- The candidate filter needs two distinct plugins. The owner list of a report can still hold two declarations of one plugin under different namespaces. That shape is also a defect, because one main classloader then holds two class sets.

## Allowlist entries

`contentModuleCopyConflicts` in `platform/buildScripts/suppressions.json` holds two kinds of entry. The `reason`
text starts with the kind, because the mechanism has no separate field for it. Read the kind before you touch an
entry.

### Known debt (`DEBT:`)

Each name below is a real instance of the QD-15883 defect. Two or more bundled plugins pack the library
privately, and some content module reaches two copies. Fixing one means declaring the library once as shared
module-set content. That was outside the run that added this validator. Each entry is expected to go away.
Remove it once the copies are gone. One tracker issue lists the names above, and each `reason` links to it.

- `intellij.libraries.clikt`
- `intellij.libraries.flexmark`
- `intellij.libraries.ktor.server.content.negotiation`
- `intellij.libraries.ktor.server.sse`
- `intellij.libraries.schema.kenerator`
- `intellij.libraries.kotlin.logging`
- `intellij.libraries.test.discovery`

### Intentional (`INTENTIONAL:`)

- `intellij.platform.commercial.verifier`

This duplication is deliberate and repository-wide. `intellij.spring`, `intellij.microservices.jvm`,
`intellij.javaee.web`, `intellij.uml`, and many more pack the module privately on purpose. That is why
`jpsModelToBazel` prints its own WARN for the pattern instead of an error. **The entry is
permanent.** It is not debt, and nobody should try to remove the duplication. Removing the entry makes the
generator fail on a pattern the repository wants.

Read the `reason` field of each entry for the owning plugins.

## Non-goals

- **A copy that is not embedded.** The rule states no verdict there, and this is a decision rather than a gap. An embedded copy joins the main classloader of its owning plugin, so a reverse walk from that plugin decides which modules see it. A copy that is not embedded has its own classloader, and the only route to it is the shared name node. A walk from that node must expand it to reach any consumer, and an expansion crosses owners by construction. Such a walk gives every owner the **same** path, which is the proof that it cannot tell the copies apart. An earlier version reported those rows, and each one was a false positive of that shape. There is no fix inside this rule, because the graph keys a content module by name and holds no per-copy node.
- The duplicate between a production plugin and a test plugin. `PluginContentDuplicatesValidator` covers it. That validator does not cover two production plugins that declare one name, because each private copy carries a distinct runtime ID.
- Module set duplication (handled by `ProductModuleSetValidator`).

### What a per-copy verdict would cost

Do not reach for the cheap version of this. `PluginGraphBuilder.addModule(PluginModuleId)` keys a
`NODE_CONTENT_MODULE_WITH_NAMESPACE` node by `"<namespace>:<name>"`, and by the bare name when the
declaration carries no namespace, so every private copy of one name shares a node. Re-keying that node per
owner looks like the fix and is not: the walk never touches those nodes. It runs on the by-name
`NODE_CONTENT_MODULE` nodes, because `EDGE_CONTAINS_CONTENT` and the content module dependency edges both
connect by-name nodes. A per-copy declaration node beside a by-name walk changes nothing.

A per-copy verdict needs per-copy identity on the walk itself: every content module node keyed by owner and
name, and every dependency edge resolved to one copy. That is a migration of the whole content module graph,
and every validator reads those nodes. The seer set and the expansion test are what buy the verdict without
it.

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [plugin-content-duplicates.md](plugin-content-duplicates.md)
- [plugin-graph.md](../plugin-graph.md)
