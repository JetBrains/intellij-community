import { readZip } from "./zip.mjs"

// Parses module-descriptors.jar produced by the build into:
//   pluginModules: Map<pluginId, { mainModule, contentModules: [{ name, namespace, loading }] }>
//   modules:       Map<moduleName, { name, namespace, visibility, package, dependencies, resourceJars }>
// where loading ∈ { "required", "embedded", "optional", "on-demand" } (default = "optional"
// per Plugin-Model-v1-v2.md), and "embedded" implies the module is packed into the main plugin
// jar and shares the plugin descriptor module's classloader.
export function readDescriptors(jarPath) {
  const zip = readZip(jarPath)
  const xmls = zip.readAllText(n => n.endsWith(".xml"))

  const pluginModules = new Map()
  const modules = new Map()

  for (const [entryName, xml] of xmls) {
    if (entryName.startsWith("plugins/")) {
      const parsed = parsePluginDescriptor(xml)
      if (parsed) pluginModules.set(parsed.id, parsed)
    }
    else {
      const parsed = parseModuleDescriptor(xml)
      if (parsed) modules.set(parsed.name, parsed)
    }
  }
  return { pluginModules, modules }
}

function parsePluginDescriptor(xml) {
  const idMatch = xml.match(/<plugin\b[^>]*\bid="([^"]+)"/)
  if (!idMatch) return null
  const id = idMatch[1]
  const mainMatch = xml.match(/<plugin-descriptor-module\b([^/]*?)\/>/)
  const mainModule = mainMatch ? parseAttrs(mainMatch[1]) : null
  const contentModules = []
  const moduleRe = /<module\b([^/]*?)\/>/g
  let m
  while ((m = moduleRe.exec(xml)) !== null) {
    const a = parseAttrs(m[1])
    if (!a.name) continue
    contentModules.push({
      name: a.name,
      namespace: a.namespace ?? null,
      loading: a.loading ?? "optional",
      requiredIfAvailable: a["required-if-available"] ?? null,
    })
  }
  return { id, mainModule, contentModules }
}

function parseModuleDescriptor(xml) {
  const rootMatch = xml.match(/<module\b([^>]*)>/)
  if (!rootMatch) return null
  const a = parseAttrs(rootMatch[1])
  if (!a.name) return null

  const depBlock = xml.match(/<dependencies>([\s\S]*?)<\/dependencies>/)
  const dependencies = []
  if (depBlock) {
    const depRe = /<(module|plugin|library)\b([^/]*?)\/>/g
    let d
    while ((d = depRe.exec(depBlock[1])) !== null) {
      const kind = d[1] === "plugin" ? "plugin" : d[1] === "library" ? "library" : "module"
      const da = parseAttrs(d[2])
      const name = da.name ?? da.id ?? null
      if (!name) continue
      dependencies.push({ kind, name, namespace: da.namespace ?? null })
    }
  }

  const resourceJars = []
  const resRe = /<resource-root\b[^/]*?\bpath="([^"]+)"/g
  let r
  while ((r = resRe.exec(xml)) !== null) resourceJars.push(normalizeResourcePath(r[1]))

  return {
    name: a.name,
    namespace: a.namespace ?? null,
    visibility: a.visibility ?? "private",
    package: a.package ?? null,
    dependencies,
    resourceJars,
  }
}

function parseAttrs(s) {
  const out = {}
  const re = /(\w[\w-]*)="([^"]*)"/g
  let m
  while ((m = re.exec(s)) !== null) out[m[1]] = m[2]
  return out
}

// Descriptor resource-root paths are written as "../lib/foo.jar" or "../plugins/X/lib/foo.jar"
// (relative to the descriptors jar inside modules/). Strip the leading "../" so values match
// the form used in productInfo.layout[].classPath (e.g. "lib/foo.jar", "plugins/X/lib/foo.jar").
function normalizeResourcePath(p) {
  let s = p
  while (s.startsWith("../")) s = s.slice(3)
  return s
}
