import { readFileSync, statSync } from "node:fs"
import { dirname, join } from "node:path"
import { readDescriptors } from "./descriptors.mjs"

const KINDS = { PLUGIN: "plugin", MODULE_V2: "moduleV2", PRODUCT_MODULE_V2: "productModuleV2", PLUGIN_ALIAS: "pluginAlias" }

// Build an enriched data model for the viewer from a JetBrains IDE distribution.
//
// distRoot may be:
//   - the .app bundle root (e.g. /Applications/Idea.app)
//   - the Contents directory directly
//   - a Linux/Windows layout root that contains lib/, plugins/, modules/, product-info.json
export function buildModel(distRoot) {
  const layoutRoot = resolveLayoutRoot(distRoot)
  const productInfoPath = join(layoutRoot, "product-info.json")
  const productInfo = JSON.parse(readFileSync(productInfoPath, "utf8"))
  const descriptorsJar = findFirstExisting([
    join(layoutRoot, "modules", "module-descriptors.jar"),
    join(layoutRoot, "..", "modules", "module-descriptors.jar"),
    join(layoutRoot, "Contents", "modules", "module-descriptors.jar"),
  ])
  const descriptors = descriptorsJar ? readDescriptors(descriptorsJar) : { pluginModules: new Map(), modules: new Map() }

  // index loading mode per content module name
  const loadingByModule = new Map()
  const moduleOwnerPlugin = new Map()
  for (const [pluginId, p] of descriptors.pluginModules) {
    for (const cm of p.contentModules) {
      loadingByModule.set(cm.name, cm.loading)
      moduleOwnerPlugin.set(cm.name, pluginId)
    }
    if (p.mainModule?.name) moduleOwnerPlugin.set(p.mainModule.name, pluginId)
  }

  const bundledSet = new Set(productInfo.bundledPlugins ?? [])
  const essentialSet = new Set(productInfo.modules ?? []) // these are "modules" listed at product-info root; treat as essential plugin IDs

  // build layout-indexed views
  const entries = (productInfo.layout ?? []).map(it => attachSizes(it, layoutRoot))

  const plugins = []
  const modules = []
  for (const e of entries) {
    if (e.kind === KINDS.PLUGIN) {
      const desc = descriptors.pluginModules.get(e.name)
      plugins.push({
        id: e.name,
        classPath: e.classPath ?? [],
        sizeBytes: e.sizeBytes ?? 0,
        bundled: bundledSet.has(e.name),
        essential: essentialSet.has(e.name),
        mainModule: desc?.mainModule ?? null,
        contentModules: desc?.contentModules ?? [],
        pluginDir: inferPluginDir(e.classPath),
      })
    } else if (e.kind === KINDS.MODULE_V2 || e.kind === KINDS.PRODUCT_MODULE_V2) {
      const desc = descriptors.modules.get(e.name)
      modules.push({
        name: e.name,
        kind: e.kind,
        namespace: desc?.namespace ?? null,
        visibility: desc?.visibility ?? "private",
        package: desc?.package ?? null,
        loading: loadingByModule.get(e.name) ?? null,
        embedded: loadingByModule.get(e.name) === "embedded",
        ownerPlugin: moduleOwnerPlugin.get(e.name) ?? inferOwnerPluginFromClassPath(e.classPath),
        classPath: e.classPath ?? [],
        sizeBytes: e.sizeBytes ?? 0,
        dependencies: desc?.dependencies ?? [],
        resourceJars: desc?.resourceJars ?? [],
      })
    }
  }

  const aliases = entries.filter(e => e.kind === KINDS.PLUGIN_ALIAS).map(e => ({ name: e.name }))

  // Surface descriptor-only modules. These have no productInfo.layout entry
  // (typically $legacy_jps_module namespace: e.g. intellij.platform.util whose
  // classes are packed into lib/util-8.jar alongside ~16 other modules). They
  // are loaded directly by the platform's RuntimeModuleRepository, so they're
  // "core" modules from the user's perspective — and they are precisely the
  // population that the ongoing extraction work targets.
  const inLayout = new Set(modules.map(m => m.name))
  for (const [name, desc] of descriptors.modules) {
    if (inLayout.has(name)) continue
    if (!desc.resourceJars?.length) continue
    const host = desc.resourceJars[0]
    const sizeBytes = jarSize(resolveJarPath(layoutRoot, host))
    modules.push({
      name,
      kind: KINDS.PRODUCT_MODULE_V2,
      namespace: desc.namespace ?? null,
      visibility: desc.visibility ?? "private",
      package: desc.package ?? null,
      loading: null,
      embedded: false,
      ownerPlugin: null,
      classPath: desc.resourceJars,
      sizeBytes: 0, // size is shared with co-tenants; per-module size is unknown — leave 0, container jar carries the total
      dependencies: desc.dependencies ?? [],
      resourceJars: desc.resourceJars,
      fromLayout: false,
    })
  }
  for (const m of modules) if (m.fromLayout === undefined) m.fromLayout = true

  // aggregate per-plugin total size from owned modules + main classpath
  const sizeByOwner = new Map()
  for (const m of modules) {
    if (!m.ownerPlugin) continue
    sizeByOwner.set(m.ownerPlugin, (sizeByOwner.get(m.ownerPlugin) ?? 0) + m.sizeBytes)
  }
  for (const p of plugins) {
    p.aggregateSizeBytes = p.sizeBytes + (sizeByOwner.get(p.id) ?? 0)
    p.moduleCount = modules.filter(m => m.ownerPlugin === p.id).length
  }

  let edgeCount = 0
  for (const m of modules) edgeCount += m.dependencies.length

  const kindCounts = entries.reduce((acc, e) => { acc[e.kind] = (acc[e.kind] ?? 0) + 1; return acc }, {})
  const totalBytes = entries.reduce((s, e) => s + (e.sizeBytes ?? 0), 0)

  // --- scope + extraction --------------------------------------------------
  // Each module's classes ultimately live in exactly one jar — the descriptor's
  // first <resource-root> (e.g. "lib/util-8.jar"), or, when no descriptor is
  // available, the layout entry's classPath[0]. Modules that share that jar
  // with any other module are "embedded" (still packed into a monolith);
  // modules that are the sole tenant are "extracted" (own dedicated jar).
  // Scope is "core" when the jar lives under lib/; "plugin" when it lives
  // under plugins/X/lib/.
  const tenantsByJar = new Map() // jar → [moduleName, ...]
  for (const m of modules) {
    const jar = (m.resourceJars && m.resourceJars[0]) ?? m.classPath[0] ?? null
    m.hostJar = jar
    if (jar) {
      let arr = tenantsByJar.get(jar)
      if (!arr) { arr = []; tenantsByJar.set(jar, arr) }
      arr.push(m.name)
    }
  }
  for (const m of modules) {
    const tenants = m.hostJar ? tenantsByJar.get(m.hostJar) : null
    m.coTenants = tenants ? tenants.filter(n => n !== m.name) : []
    m.extraction = !m.hostJar ? "unknown" : (m.coTenants.length === 0 ? "extracted" : "embedded")
    m.scope = m.hostJar?.startsWith("lib/") ? "core"
      : m.hostJar?.startsWith("plugins/") ? "plugin"
      : (m.kind === KINDS.PRODUCT_MODULE_V2 ? "core" : "plugin")
  }

  const jarTenants = [...tenantsByJar.entries()].map(([jar, names]) => {
    // Prefer real jar size on disk — per-module sizes are unknown when many
    // descriptors share one jar (we can't split util-8.jar by class without
    // actually scanning the zip entries).
    const sizeBytes = jarSize(resolveJarPath(layoutRoot, jar))
    return {
      jar,
      modules: names,
      scope: jar.startsWith("lib/") ? "core" : "plugin",
      shared: names.length > 1,
      sizeBytes,
    }
  }).sort((a, b) => b.modules.length - a.modules.length || b.sizeBytes - a.sizeBytes)

  for (const p of plugins) {
    const owned = modules.filter(m => m.ownerPlugin === p.id)
    p.extractedModuleCount = owned.filter(m => m.extraction === "extracted").length
    p.embeddedModuleCount = owned.filter(m => m.extraction === "embedded").length
  }

  // extra stats
  const coreModules = modules.filter(m => m.scope === "core")
  const pluginContentModules = modules.filter(m => m.scope === "plugin")
  const coreExtractedCount = coreModules.filter(m => m.extraction === "extracted").length
  const coreEmbeddedCount = coreModules.filter(m => m.extraction === "embedded").length
  const pluginContentExtractedCount = pluginContentModules.filter(m => m.extraction === "extracted").length
  const pluginContentEmbeddedCount = pluginContentModules.filter(m => m.extraction === "embedded").length
  const sharedJarCount = jarTenants.filter(j => j.shared).length

  const launch = (productInfo.launch ?? []).map(l => ({
    os: l.os, arch: l.arch,
    boot: (l.bootClassPathJarNames ?? []).map(n => ({ name: n, sizeBytes: jarSize(join(layoutRoot, "lib", n)) })),
  }))

  return {
    product: {
      name: productInfo.name,
      version: productInfo.version,
      versionSuffix: productInfo.versionSuffix,
      buildNumber: productInfo.buildNumber,
      productCode: productInfo.productCode,
      vendor: productInfo.productVendor,
      dataDir: productInfo.dataDirectoryName,
      majorVersionReleaseDate: productInfo.majorVersionReleaseDate,
    },
    stats: {
      layoutCount: entries.length,
      kindCounts,
      pluginCount: plugins.length,
      moduleCount: modules.length,
      aliasCount: aliases.length,
      bundledCount: bundledSet.size,
      totalBytes,
      edgeCount,
      coreCount: coreModules.length,
      coreExtractedCount,
      coreEmbeddedCount,
      pluginContentCount: pluginContentModules.length,
      pluginContentExtractedCount,
      pluginContentEmbeddedCount,
      sharedJarCount,
    },
    plugins,
    modules,
    aliases,
    jarTenants,
    launch,
    layoutRoot,
  }
}

function resolveLayoutRoot(distRoot) {
  const candidates = [
    distRoot,
    join(distRoot, "Contents", "Resources"),
    join(distRoot, "Resources"),
  ]
  for (const c of candidates) {
    try {
      if (statSync(join(c, "product-info.json")).isFile()) return c
    } catch {}
  }
  throw new Error(`product-info.json not found under ${distRoot}`)
}

function attachSizes(entry, layoutRoot) {
  if (!entry.classPath) return { ...entry, sizeBytes: 0 }
  let total = 0
  const paths = entry.classPath.map(cp => {
    const abs = resolveJarPath(layoutRoot, cp)
    const size = jarSize(abs)
    total += size
    return cp
  })
  return { ...entry, classPath: paths, sizeBytes: total }
}

function jarSize(absPath) {
  try { return statSync(absPath).size } catch { return 0 }
}

function resolveJarPath(layoutRoot, relPath) {
  // product-info.json paths are relative to Contents/ on Mac, or to dist root on Linux.
  // layoutRoot is Contents/Resources on Mac (or dist root). Walk one level up if needed.
  const candidates = [
    join(layoutRoot, relPath),
    join(layoutRoot, "..", relPath), // Contents/
    join(layoutRoot, "..", "..", relPath),
  ]
  for (const c of candidates) {
    try { if (statSync(c).isFile()) return c } catch {}
  }
  return candidates[0]
}

function findFirstExisting(paths) {
  for (const p of paths) {
    try { if (statSync(p).isFile()) return p } catch {}
  }
  return null
}

function inferPluginDir(classPath) {
  if (!classPath?.length) return null
  const first = classPath[0]
  const m = first.match(/^plugins\/([^/]+)\//)
  return m ? m[1] : null
}

function inferOwnerPluginFromClassPath(classPath) {
  if (!classPath?.length) return null
  const m = classPath[0].match(/^plugins\/([^/]+)\//)
  return m ? m[1] : null
}
