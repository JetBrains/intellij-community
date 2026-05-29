// IDEA Dist Plugin-Model Viewer — single-file client
// Data is injected as window.__DIST_DATA__ before this script loads.

(() => {
  const data = window.__DIST_DATA__
  if (!data) { document.body.textContent = "No data."; return }

  // ---------- helpers ----------
  const fmt = new Intl.NumberFormat("en-US")
  const KB = 1024, MB = KB * KB, GB = KB * MB
  function bytes(n) {
    if (n == null) return "—"
    if (n < KB) return `${n} B`
    if (n < MB) return `${(n / KB).toFixed(1)} KB`
    if (n < GB) return `${(n / MB).toFixed(1)} MB`
    return `${(n / GB).toFixed(2)} GB`
  }
  function el(tag, attrs = {}, ...kids) {
    const e = document.createElement(tag)
    for (const [k, v] of Object.entries(attrs)) {
      if (k === "class") e.className = v
      else if (k === "html") e.innerHTML = v
      else if (k.startsWith("on") && typeof v === "function") e.addEventListener(k.slice(2), v)
      else if (v !== null && v !== undefined && v !== false) e.setAttribute(k, v === true ? "" : v)
    }
    for (const k of kids.flat()) {
      if (k == null || k === false) continue
      e.appendChild(typeof k === "string" ? document.createTextNode(k) : k)
    }
    return e
  }
  function chip(text, cls) { return el("span", { class: `chip ${cls}` }, text) }
  const KIND_LABELS = { plugin: "plugin", moduleV2: "module", productModuleV2: "product", pluginAlias: "alias" }
  function kindChip(kind) {
    return el("span", { class: `chip chip-kind-${kind}`, title: kind }, KIND_LABELS[kind] ?? kind)
  }
  function loadChip(loading) { return loading ? chip(loading, `chip-load-${loading}`) : null }
  function visChip(v) { return chip(v, `chip-vis-${v}`) }

  // ---------- node icons (scope + extraction) ----------
  // 14×14 inline SVGs, themed via currentColor (set by .scope-* class).
  //  - core / extracted     → solid teal tile (own lib/ jar)
  //  - core / embedded      → teal tile with 2 internal partitions (shared lib/ jar)
  //  - plugin / extracted   → outlined blue tile (own jar in plugins/X/lib/)
  //  - plugin / embedded    → outlined blue tile with partitions (shared with siblings)
  //  - plugin descriptor    → filled diamond (kind=plugin row)
  //  - pluginAlias          → outlined diamond
  function svg(scopeCls, title, innerHtml) {
    const w = el("span", { class: `node-icon ${scopeCls}`, title })
    w.innerHTML = `<svg viewBox="0 0 14 14" xmlns="http://www.w3.org/2000/svg" width="14" height="14" aria-hidden="true">${innerHtml}</svg>`
    return w
  }
  const ICON = {
    coreExtracted: () => svg("scope-core",
      "Core module — extracted (own lib/ jar)",
      `<rect x="1.5" y="1.5" width="11" height="11" rx="1.5" fill="currentColor"/>`),
    coreEmbedded: () => svg("scope-core",
      "Core module — embedded (shares a lib/ jar with other modules)",
      `<rect x="1.5" y="1.5" width="11" height="11" rx="1.5" fill="currentColor" fill-opacity="0.45"/>
       <line x1="1.5" y1="5.5" x2="12.5" y2="5.5" stroke="var(--bg)" stroke-width="1"/>
       <line x1="1.5" y1="8.5" x2="12.5" y2="8.5" stroke="var(--bg)" stroke-width="1"/>`),
    pluginExtracted: () => svg("scope-plugin",
      "Plugin content module — extracted (own jar inside the plugin)",
      `<rect x="1.5" y="1.5" width="11" height="11" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.5"/>`),
    pluginEmbedded: () => svg("scope-plugin",
      "Plugin content module — embedded (packed into another jar in the plugin)",
      `<rect x="1.5" y="1.5" width="11" height="11" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-dasharray="2 1.5"/>
       <line x1="2" y1="5.5" x2="12" y2="5.5" stroke="currentColor" stroke-width="0.8" stroke-dasharray="1.5 1.5"/>
       <line x1="2" y1="8.5" x2="12" y2="8.5" stroke="currentColor" stroke-width="0.8" stroke-dasharray="1.5 1.5"/>`),
    pluginDescriptor: () => svg("scope-plugin-descriptor",
      "Plugin (descriptor entry)",
      `<path d="M7 1.2 L12.8 7 L7 12.8 L1.2 7 Z" fill="currentColor"/>`),
    pluginAlias: () => svg("scope-alias",
      "Plugin alias",
      `<path d="M7 1.2 L12.8 7 L7 12.8 L1.2 7 Z" fill="none" stroke="currentColor" stroke-width="1.5"/>`),
  }
  // Pick the right glyph for a module or plugin entry.
  function nodeIcon(entry) {
    if (entry.__plugin || entry.kind === "plugin") return ICON.pluginDescriptor()
    if (entry.kind === "pluginAlias") return ICON.pluginAlias()
    const scope = entry.scope ?? (entry.kind === "productModuleV2" ? "core" : "plugin")
    const embedded = entry.extraction === "embedded"
    if (scope === "core") return embedded ? ICON.coreEmbedded() : ICON.coreExtracted()
    return embedded ? ICON.pluginEmbedded() : ICON.pluginExtracted()
  }

  // Host-jar chip. Click → filter the Modules tab by that container jar.
  function hostChip(m) {
    if (!m.hostJar) return el("span", {})
    const leaf = jarLeaf(m.hostJar)
    const shared = m.coTenants?.length > 0
    const title = shared
      ? `Packed into ${m.hostJar} together with ${m.coTenants.length} other module${m.coTenants.length === 1 ? "" : "s"}: ${m.coTenants.slice(0, 12).join(", ")}${m.coTenants.length > 12 ? ", …" : ""}`
      : `Packed into ${m.hostJar} (extracted — sole tenant of this jar)`
    return el("span", {
      class: `chip chip-host${shared ? " shared" : ""}`,
      title,
      onclick: e => {
        e.stopPropagation()
        modulesState.q = ""
        modulesState.containerJar = m.hostJar
        showTab("modules")
        modulesState.render()
      },
    }, leaf)
  }

  const LEGACY_NS_LABELS = {
    "$legacy_jps_module": "legacy JPS module",
    "$legacy_jps_library": "legacy JPS library",
    "$legacy_jps_module_tests": "legacy JPS tests",
  }
  const LEGACY_NS_TOOLTIP = "Auto-assigned namespace for build-system (JPS) modules/libraries that haven't been migrated to a Plugin Model v2 content module with a vendor namespace."
  function nsChip(ns) {
    if (!ns) return null
    const isLegacy = ns.startsWith("$")
    const label = LEGACY_NS_LABELS[ns] ?? ns
    return el("span", {
      class: `chip ${isLegacy ? "chip-ns-legacy" : "chip-ns"}`,
      title: isLegacy ? LEGACY_NS_TOOLTIP : `namespace: ${ns}`,
    }, label)
  }
  function placeholder() { return el("span", {}, "") }

  // ---------- top bar ----------
  const product = data.product
  document.title = `${product.name} ${product.version} · dist viewer`
  document.querySelector(".product").textContent = `${product.name} ${product.version}${product.versionSuffix ? " " + product.versionSuffix : ""}`
  document.querySelector(".build").textContent = `${product.productCode} · ${product.buildNumber}`

  // ---------- theme ----------
  function palette() {
    const cs = getComputedStyle(document.documentElement)
    const v = (n) => cs.getPropertyValue(n).trim()
    return {
      bg: v("--bg"), bgElev: v("--bg-elev"), bgElev2: v("--bg-elev-2"),
      line: v("--line"), lineSoft: v("--line-soft"),
      fg: v("--fg"), fgDim: v("--fg-dim"), fgVeryDim: v("--fg-very-dim"),
      accent: v("--accent"),
      kind: {
        plugin: v("--kind-plugin"), moduleV2: v("--kind-moduleV2"),
        productModuleV2: v("--kind-productModuleV2"), pluginAlias: v("--kind-pluginAlias"),
      },
      scope: { core: v("--scope-core"), plugin: v("--scope-plugin") },
      load: { embedded: v("--load-embedded") },
    }
  }
  function setTheme(t, persist) {
    document.documentElement.dataset.theme = t
    if (persist) localStorage.setItem("distViewerTheme", t)
    const btn = document.querySelector(".theme-toggle")
    if (btn) btn.textContent = t === "light" ? "◑" : "◐"
    // re-render the active chart-bearing tab so it picks up new colors
    const active = document.querySelector("nav.tabs button.active")?.dataset.tab
    if (active && ["treemap", "graph", "boot"].includes(active)) {
      rendered[active] = false
      renderers[active]()
      rendered[active] = true
    }
  }
  document.querySelector(".theme-toggle")?.addEventListener("click", () => {
    const next = document.documentElement.dataset.theme === "light" ? "dark" : "light"
    setTheme(next, true)
  })
  // initial sync of toggle glyph (theme attr is set early by template inline script)
  {
    const btn = document.querySelector(".theme-toggle")
    if (btn) btn.textContent = document.documentElement.dataset.theme === "light" ? "◑" : "◐"
  }

  const tabs = document.querySelectorAll("nav.tabs button")
  const views = document.querySelectorAll("section.view")
  function showTab(id) {
    tabs.forEach(t => t.classList.toggle("active", t.dataset.tab === id))
    views.forEach(v => v.classList.toggle("hidden", v.id !== `view-${id}`))
    if (renderers[id] && !rendered[id]) { renderers[id](); rendered[id] = true }
    if (id === "treemap" || id === "graph") { window.dispatchEvent(new Event("resize")) }
    history.replaceState(null, "", "#" + id)
  }
  tabs.forEach(t => t.addEventListener("click", () => showTab(t.dataset.tab)))

  const rendered = {}
  const renderers = {}

  // ---------- Overview ----------
  renderers.overview = () => {
    const v = document.getElementById("view-overview")
    const s = data.stats
    const cards = el("div", { class: "cards" },
      card("Total layout entries", fmt.format(s.layoutCount), `${Object.entries(s.kindCounts).map(([k, c]) => `${k}: ${fmt.format(c)}`).join(" · ")}`),
      card("Plugins", fmt.format(s.pluginCount), `${fmt.format(s.bundledCount)} bundled`),
      card("Modules", fmt.format(s.moduleCount), `${fmt.format(s.coreCount)} core · ${fmt.format(s.pluginContentCount)} plugin-content`),
      card("Plugin aliases", fmt.format(s.aliasCount)),
      card("Total dist size", bytes(s.totalBytes), `${fmt.format(s.edgeCount)} dep edges`),
      card("Boot classpath jars", fmt.format(data.launch[0]?.boot.length ?? 0), `${data.launch[0]?.os ?? ""} ${data.launch[0]?.arch ?? ""}`),
    )

    // Core modules (lib/) — extraction state.
    const coreCard = (kind, count, sub, embedded) => {
      const c = card(kind, fmt.format(count), sub)
      if (embedded) c.classList.add("hatched")
      c.style.cursor = "pointer"
      c.addEventListener("click", () => {
        modulesState.filters = { extracted: false, embedded: false, core: true, "plugin-content": false, required: false, "loading-embedded": false, optional: false, "on-demand": false, public: false, internal: false, private: false }
        if (embedded) modulesState.filters.embedded = true
        else modulesState.filters.extracted = true
        showTab("modules")
        modulesState.render()
      })
      return c
    }
    const coreRow = el("div", { class: "cards" },
      coreCard("Extracted core modules", s.coreExtractedCount,
        `${((s.coreExtractedCount / Math.max(1, s.coreCount)) * 100).toFixed(0)}% of core · own lib/ jar`),
      coreCard("Embedded core modules", s.coreEmbeddedCount,
        `${fmt.format(s.sharedJarCount)} shared jars · share lib/*.jar with siblings`, true),
    )

    // Plugin content modules — loading mode (legacy chart).
    const loadingDist = { required: 0, embedded: 0, optional: 0, "on-demand": 0 }
    for (const m of data.modules) {
      if (m.scope !== "plugin") continue
      if (m.loading) loadingDist[m.loading] = (loadingDist[m.loading] ?? 0) + 1
    }
    const LOADING_TOOLTIPS = {
      "required": "Declared with loading=\"required\". The plugin fails to load if this module's dependencies aren't satisfied.",
      "embedded": "Declared with loading=\"embedded\". Classes are packed into the main plugin JAR and share the plugin descriptor module's classloader.",
      "optional": "Declared with loading=\"optional\" (or default). Skipped silently if dependencies aren't satisfied.",
      "on-demand": "Declared with loading=\"on-demand\". Loaded only when another module that depends on it is loaded.",
    }
    const loadingRow = el("div", { class: "cards" },
      ...["required", "embedded", "optional", "on-demand"].map(k =>
        loadingDist[k] ? card(k, fmt.format(loadingDist[k]), `${((loadingDist[k] / Math.max(1, s.pluginContentCount)) * 100).toFixed(1)}% of plugin-content`, LOADING_TOOLTIPS[k]) : null
      ).filter(Boolean)
    )

    // Container jars table (top 10 shared lib jars).
    const sharedCore = data.jarTenants.filter(j => j.shared && j.scope === "core").slice(0, 10)
    const containerList = sharedCore.length === 0 ? el("div", { class: "dim" }, "— none —") : el("div", { class: "list" },
      ...sharedCore.map(j => el("div", {
        class: "row row-container",
        onclick: () => {
          modulesState.filters = { extracted: false, embedded: false, core: false, "plugin-content": false, required: false, "loading-embedded": false, optional: false, "on-demand": false, public: false, internal: false, private: false }
          modulesState.containerJar = j.jar
          modulesState.q = ""
          showTab("modules")
          modulesState.render()
        },
      },
        ICON.coreEmbedded(),
        el("span", { class: "name", style: "font-family: ui-monospace, monospace; font-size: 12px;" }, j.jar),
        chip(`${j.modules.length} tenants`, "chip-load-embedded chip-on"),
        el("span", { class: "size" }, bytes(j.sizeBytes)),
      )),
    )

    // top 10 plugins by aggregate size
    const top = [...data.plugins].sort((a, b) => b.aggregateSizeBytes - a.aggregateSizeBytes).slice(0, 10)
    const topList = el("div", { class: "list" },
      ...top.map(p => el("div", { class: "row row-plugin", onclick: () => { showTab("plugins"); pluginsState.focus = p.id; pluginsState.render() } },
        el("span", { class: "name" }, ICON.pluginDescriptor(), p.id),
        p.essential ? chip("essential", "chip-essential") : (p.bundled ? chip("bundled", "chip-bundled") : placeholder()),
        chip(`${p.moduleCount} modules`, "chip-bundled"),
        el("span", { class: "meta" }, `${p.classPath.length} jars`),
        el("span", { class: "size" }, bytes(p.aggregateSizeBytes)),
      ))
    )

    v.replaceChildren(
      el("h2", {}, "Distribution"),
      cards,
      el("h2", {}, "Core modules (lib/) — extraction state"),
      coreRow,
      el("h2", {}, "Container jars — shared lib/*.jar (top 10)"),
      containerList,
      el("h2", {}, "Plugin content modules — loading mode"),
      loadingRow,
      el("h2", {}, "Top 10 plugins by aggregate size"),
      topList,
    )
  }
  function card(label, value, sub, tooltip) {
    return el("div", { class: "card", title: tooltip ?? false },
      el("div", { class: "label" }, label),
      el("div", { class: "value" }, value),
      sub ? el("div", { class: "sub" }, sub) : null,
    )
  }

  // ---------- Plugins ----------
  const pluginsState = { q: "", focus: null, openId: null }
  pluginsState.render = () => {
    const v = document.getElementById("view-plugins")
    const q = pluginsState.q.toLowerCase()
    let list = data.plugins
    if (q) list = list.filter(p => p.id.toLowerCase().includes(q))
    list = [...list].sort((a, b) => b.aggregateSizeBytes - a.aggregateSizeBytes)
    if (pluginsState.focus) {
      pluginsState.openId = pluginsState.focus
      pluginsState.focus = null
    }

    const search = el("input", {
      type: "search", placeholder: "Filter plugins by id…", value: pluginsState.q,
      oninput: e => { pluginsState.q = e.target.value; pluginsState.render() },
    })
    const count = el("span", { class: "count" }, `${fmt.format(list.length)} / ${fmt.format(data.plugins.length)}`)
    const toolbar = el("div", { class: "toolbar" }, search, count)

    const rows = []
    for (const p of list) {
      const isOpen = pluginsState.openId === p.id
      const row = el("div", {
        class: `row row-plugin${isOpen ? " is-open" : ""}`,
        onclick: () => { pluginsState.openId = isOpen ? null : p.id; pluginsState.render() },
      },
        el("span", { class: "name" }, ICON.pluginDescriptor(), p.id),
        p.essential ? chip("essential", "chip-essential") : (p.bundled ? chip("bundled", "chip-bundled") : placeholder()),
        chip(`${p.moduleCount} modules`, "chip-bundled"),
        el("span", { class: "meta" }, `${p.classPath.length} jar${p.classPath.length === 1 ? "" : "s"}`),
        el("span", { class: "size" }, bytes(p.aggregateSizeBytes)),
      )
      rows.push(row)
      if (isOpen) rows.push(renderPluginExpand(p))
    }
    v.replaceChildren(toolbar, el("div", { class: "list" }, rows))
    if (pluginsState.openId) {
      const r = v.querySelector(".row.is-open")
      r?.scrollIntoView({ block: "nearest" })
    }
  }
  function renderPluginExpand(p) {
    const owned = data.modules.filter(m => m.ownerPlugin === p.id)
    const main = p.mainModule
    const ext = owned.filter(m => m.extraction === "extracted").length
    const emb = owned.filter(m => m.extraction === "embedded").length
    const moduleByName = new Map(data.modules.map(m => [m.name, m]))
    return el("div", { class: "expand" },
      el("h3", {}, "Plugin descriptor module"),
      main ? el("div", {},
        el("span", { class: "pill" }, main.name), " ",
        nsChip(main.namespace),
      ) : el("div", { class: "dim" }, "—"),
      el("h3", {}, `Main classpath (${p.classPath.length})`),
      el("ul", { class: "cols" }, ...p.classPath.map(j => el("li", {}, j))),
      el("h3", {}, `Content modules (${p.contentModules.length}) — ${ext} extracted, ${emb} embedded`),
      p.contentModules.length === 0 ? el("div", { class: "dim" }, "— none —") :
        el("ul", {}, ...p.contentModules.map(cm => {
          const cmMod = moduleByName.get(cm.name)
          return el("li", {},
            cmMod ? nodeIcon(cmMod) : null,
            loadChip(cm.loading),
            el("span", { class: "crosslink", onclick: e => { e.stopPropagation(); modulesState.q = cm.name; modulesState.openName = cm.name; showTab("modules"); modulesState.render() } }, cm.name),
            " ", nsChip(cm.namespace),
            cm.requiredIfAvailable ? el("span", { class: "dim" }, ` · required-if=${cm.requiredIfAvailable}`) : null,
          )
        })),
      el("h3", {}, `Modules in this plugin (${owned.length})`),
      owned.length === 0 ? el("div", { class: "dim" }, "— none inferred from classpath —") :
        el("ul", {}, ...owned.map(m => el("li", {},
          nodeIcon(m),
          el("span", { class: "crosslink", onclick: e => { e.stopPropagation(); modulesState.q = m.name; modulesState.openName = m.name; showTab("modules"); modulesState.render() } }, m.name),
          el("span", { class: "dim" }, ` · ${bytes(m.sizeBytes)}`),
        ))),
    )
  }
  renderers.plugins = pluginsState.render

  // ---------- Modules ----------
  const modulesState = {
    q: "", openName: null, containerJar: null,
    filters: {
      // extraction (shares-a-jar vs sole-tenant; NOT the same as loading="embedded")
      extracted: false, embedded: false,
      // scope
      core: false, "plugin-content": false,
      // loading mode — "loading-embedded" key is intentional to avoid colliding with
      // the extraction "embedded" key above (both meanings legitimately use the word).
      required: false, "loading-embedded": false, optional: false, "on-demand": false,
      // visibility
      public: false, internal: false, private: false,
    },
  }
  modulesState.render = () => {
    const v = document.getElementById("view-modules")
    const q = modulesState.q.toLowerCase()
    const fl = modulesState.filters
    const anyExtraction = fl.extracted || fl.embedded
    const anyScope = fl.core || fl["plugin-content"]
    const anyLoad = fl.required || fl["loading-embedded"] || fl.optional || fl["on-demand"]
    const anyVis = fl.public || fl.internal || fl.private
    let list = data.modules
    if (q) list = list.filter(m => m.name.toLowerCase().includes(q) || (m.ownerPlugin ?? "").toLowerCase().includes(q))
    if (anyExtraction) list = list.filter(m => fl[m.extraction])
    if (anyScope) list = list.filter(m => (fl.core && m.scope === "core") || (fl["plugin-content"] && m.scope === "plugin"))
    if (anyLoad) {
      list = list.filter(m => {
        if (!m.loading) return false
        if (m.loading === "embedded") return fl["loading-embedded"]
        return fl[m.loading]
      })
    }
    if (anyVis) list = list.filter(m => fl[m.visibility])
    if (modulesState.containerJar) list = list.filter(m => m.hostJar === modulesState.containerJar)
    list = [...list].sort((a, b) => b.sizeBytes - a.sizeBytes)

    const search = el("input", {
      type: "search", placeholder: "Filter modules by name or owner…", value: modulesState.q,
      oninput: e => { modulesState.q = e.target.value; modulesState.render() },
    })
    const mkFilter = (label, key, title) => el("button", {
      class: `filter${fl[key] ? " on" : ""}`,
      title: title ?? null,
      onclick: () => { fl[key] = !fl[key]; modulesState.render() },
    }, label)
    // Extraction buttons get the row-icon glyph as a visual prefix so they can never
    // be confused with the loading-mode "embedded" button below.
    const mkExtractionFilter = (label, key, icon, title) => {
      const btn = el("button", {
        class: `filter filter-with-icon${fl[key] ? " on" : ""}`,
        title,
        onclick: () => { fl[key] = !fl[key]; modulesState.render() },
      }, icon, el("span", {}, label))
      return btn
    }
    const groupLabel = (text) => el("span", { class: "filter-group-label" }, text)

    // Container-jar dropdown (only shared core jars listed; +1 "(any)" option).
    const sharedCoreJars = data.jarTenants.filter(j => j.shared && j.scope === "core")
    const containerSelect = el("select", {
      onchange: e => { modulesState.containerJar = e.target.value || null; modulesState.render() },
      title: "Filter by container jar (shared lib/*.jar)",
    },
      el("option", { value: "" }, "any container"),
      ...sharedCoreJars.map(j => el("option", { value: j.jar, selected: modulesState.containerJar === j.jar ? true : null }, `${j.jar} (${j.modules.length})`)),
    )
    if (modulesState.containerJar) containerSelect.value = modulesState.containerJar

    const toolbar = el("div", { class: "toolbar" },
      search,
      groupLabel("scope:"),
      mkFilter("core", "core", "Module lives in lib/ — loaded by the platform's RuntimeModuleRepository."),
      mkFilter("plugin-content", "plugin-content", "Module lives in plugins/X/lib/ — owned by exactly one plugin."),
      groupLabel("extraction:"),
      mkExtractionFilter("extracted", "extracted", ICON.coreExtracted(),
        "Module owns its jar — sole tenant of its host jar."),
      mkExtractionFilter("embedded", "embedded", ICON.coreEmbedded(),
        "Module shares a jar with other modules (e.g. lib/util-8.jar packs 17 modules). Not the same as loading=\"embedded\" — use the loading filter below for that."),
      groupLabel("loading:"),
      mkFilter("required", "required", "loading=\"required\" — plugin fails to load if dependencies aren't satisfied."),
      mkFilter("embedded", "loading-embedded", "loading=\"embedded\" — plugin content module whose classes are packed into the plugin's main jar and share its classloader. Not the same as the extraction \"embedded\" chip above."),
      mkFilter("optional", "optional", "loading=\"optional\" (or default) — skipped silently when dependencies aren't satisfied."),
      mkFilter("on-demand", "on-demand", "loading=\"on-demand\" — loaded only when another module that depends on it is loaded."),
      groupLabel("visibility:"),
      mkFilter("public", "public"),
      mkFilter("internal", "internal"),
      mkFilter("private", "private"),
      el("span", { class: "count" }, `${fmt.format(list.length)} / ${fmt.format(data.modules.length)}`),
    )
    const toolbar2 = el("div", { class: "toolbar" },
      el("span", { class: "meta" }, "Container jar:"),
      containerSelect,
      modulesState.containerJar
        ? el("button", { class: "filter", onclick: () => { modulesState.containerJar = null; modulesState.render() } }, "clear")
        : null,
    )

    // Virtualized list — handles ~1500 rows comfortably as plain DOM, but cap at 500 visible when no filter
    const anyFilter = q || anyExtraction || anyScope || anyLoad || anyVis || modulesState.containerJar
    const cap = anyFilter ? list.length : Math.min(list.length, 500)
    const shown = list.slice(0, cap)
    const rows = []
    for (const m of shown) {
      const isOpen = modulesState.openName === m.name
      rows.push(el("div", {
        class: `row row-module${isOpen ? " is-open" : ""}`,
        onclick: () => { modulesState.openName = isOpen ? null : m.name; modulesState.render() },
      },
        el("span", { class: "name" }, nodeIcon(m), m.name),
        loadChip(m.loading) ?? el("span", {}, ""),
        hostChip(m),
        visChip(m.visibility),
        m.ownerPlugin ? el("span", { class: "meta crosslink", onclick: e => { e.stopPropagation(); pluginsState.focus = m.ownerPlugin; showTab("plugins"); pluginsState.render() } }, m.ownerPlugin) : el("span", { class: "meta" }, "—"),
        el("span", { class: "size" }, bytes(m.sizeBytes)),
      ))
      if (isOpen) rows.push(renderModuleExpand(m))
    }
    const note = list.length > shown.length ? el("div", { class: "hint" }, `Showing first ${shown.length} of ${list.length} — type to narrow.`) : null

    v.replaceChildren(toolbar, toolbar2, el("div", { class: "list" }, rows), note)
    if (modulesState.openName) v.querySelector(".row.is-open")?.scrollIntoView({ block: "nearest" })
  }
  function renderModuleExpand(m) {
    const deps = m.dependencies
    const byKind = { module: [], plugin: [], library: [] }
    for (const d of deps) (byKind[d.kind] ?? byKind.module).push(d)
    const renderDeps = (arr, label, jumpKind) => el("div", {},
      el("h3", {}, `${label} (${arr.length})`),
      arr.length === 0 ? el("div", { class: "dim" }, "— none —") :
        el("ul", { class: "cols" }, ...arr.map(d => el("li", {},
          jumpKind === "module"
            ? el("span", { class: "crosslink", onclick: e => { e.stopPropagation(); modulesState.q = d.name; modulesState.openName = d.name; modulesState.render() } }, d.name)
            : d.name,
          d.namespace ? el("span", {}, " ", nsChip(d.namespace)) : null,
        ))),
    )
    return el("div", { class: "expand" },
      el("div", {},
        el("h3", {}, "Properties"),
        el("ul", {},
          el("li", {}, "kind: ", kindChip(m.kind)),
          el("li", {}, "scope: ", chip(m.scope, m.scope === "core" ? "chip-host" : "chip-ns")),
          el("li", {}, "extraction: ", chip(m.extraction, m.extraction === "embedded" ? "chip-load-embedded chip-on" : "chip-load-required")),
          el("li", {}, "host jar: ", hostChip(m)),
          m.coTenants?.length > 0 ? el("li", {}, "co-tenants: ", el("span", { class: "dim" }, `${m.coTenants.length} module${m.coTenants.length === 1 ? "" : "s"} share this jar`)) : null,
          el("li", {}, "namespace: ", nsChip(m.namespace) ?? el("span", { class: "dim" }, "—")),
          el("li", {}, "package: ", el("span", { class: "dim" }, m.package ?? "—")),
          el("li", {}, "visibility: ", visChip(m.visibility)),
          m.loading ? el("li", {}, "loading: ", loadChip(m.loading)) : null,
        ),
      ),
      el("h3", {}, `Classpath (${m.classPath.length})`),
      el("ul", { class: "cols" }, ...m.classPath.map(j => el("li", {}, j))),
      renderDeps(byKind.module, "Module dependencies", "module"),
      renderDeps(byKind.plugin, "Plugin dependencies", "plugin"),
      renderDeps(byKind.library, "Library dependencies", "library"),
    )
  }
  renderers.modules = modulesState.render

  // ---------- Treemap (ECharts) ----------
  renderers.treemap = () => {
    const v = document.getElementById("view-treemap")
    const modeSelect = el("select", {},
      el("option", { value: "container-jar" }, "container jar → tenant module"),
      el("option", { value: "kind-plugin-jar" }, "kind → plugin → jar"),
      el("option", { value: "plugin-module-jar" }, "plugin → module → jar"),
    )
    const wrap = el("div", { class: "chart-wrap" })
    v.replaceChildren(el("div", { class: "chart-controls" }, el("span", { class: "meta" }, "Group by:"), modeSelect), wrap)
    const chart = echarts.init(wrap, null, { renderer: "canvas" })
    const ro = new ResizeObserver(() => chart.resize())
    ro.observe(wrap)
    function update() { chart.setOption({ ...treemapOption(modeSelect.value) }, { notMerge: true }) }
    modeSelect.addEventListener("change", update)
    update()
  }
  function treemapOption(mode) {
    const pal = palette()
    let tree
    if (mode === "container-jar") {
      // Groups: scope (core | plugin) → container jar → tenant module.
      // Shared (multi-tenant) jars get a thicker embedded-orange border to make monoliths pop.
      const byScope = new Map()
      for (const j of data.jarTenants) {
        const tenantNodes = j.modules.map(name => {
          const m = data.modules.find(x => x.name === name)
          return {
            name,
            value: m?.sizeBytes || (j.shared ? Math.max(1, Math.floor((j.sizeBytes || 1) / j.modules.length)) : 1),
            itemStyle: j.shared ? { borderColor: pal.scope[j.scope] || pal.fg, borderWidth: 1 } : null,
          }
        })
        const jarNode = {
          name: j.jar,
          value: j.sizeBytes || 1,
          children: tenantNodes,
          itemStyle: j.shared
            ? { color: pal.scope[j.scope], borderColor: pal.load.embedded, borderWidth: 2 }
            : { color: pal.scope[j.scope] },
        }
        push(byScope, j.scope === "core" ? "core (lib/)" : "plugin-owned (plugins/X/lib/)", jarNode)
      }
      tree = [...byScope.entries()].map(([k, v]) => ({ name: k, children: v }))
    } else if (mode === "kind-plugin-jar") {
      const byKind = new Map()
      for (const p of data.plugins) {
        const node = { name: p.id, value: p.aggregateSizeBytes, children: p.classPath.map(j => ({ name: jarLeaf(j), value: jarSize(j) ?? 1 })) }
        push(byKind, "plugin", node)
      }
      for (const m of data.modules) {
        const node = { name: m.name, value: m.sizeBytes || 1, children: m.classPath.map(j => ({ name: jarLeaf(j), value: jarSize(j) ?? 1 })) }
        push(byKind, m.kind, node)
      }
      tree = [...byKind.entries()].map(([k, v]) => ({ name: k, children: v }))
    } else {
      const byPlugin = new Map()
      for (const p of data.plugins) {
        const children = p.classPath.map(j => ({ name: jarLeaf(j), value: jarSize(j) ?? 1 }))
        push(byPlugin, p.id, { name: `(${p.id} main)`, value: p.sizeBytes || 1, children })
      }
      for (const m of data.modules) {
        const children = m.classPath.map(j => ({ name: jarLeaf(j), value: jarSize(j) ?? 1 }))
        const owner = m.ownerPlugin ?? "(unowned)"
        push(byPlugin, owner, { name: m.name, value: m.sizeBytes || 1, children, itemStyle: { borderColor: m.embedded ? "var(--load-embedded)" : null } })
      }
      tree = [...byPlugin.entries()].map(([k, v]) => ({ name: k, children: v }))
    }
    return {
      tooltip: { formatter: info => `${info.marker}${info.name}<span style="margin-left:20px;font-weight:600">${bytes(info.value)}</span>` },
      series: [{
        type: "treemap", roam: false, nodeClick: "zoomToNode", leafDepth: 2,
        data: tree, breadcrumb: { show: true },
        levels: [
          { itemStyle: { borderColor: pal.bg, borderWidth: 2, gapWidth: 2 } },
          { itemStyle: { borderColor: pal.bg, borderWidth: 4, gapWidth: 1, borderColorSaturation: 0.5 }, upperLabel: { show: true, color: pal.fg } },
          { itemStyle: { borderColor: pal.bg, borderWidth: 1, gapWidth: 1 } },
        ],
      }],
    }
  }
  function jarLeaf(p) { const i = p.lastIndexOf("/"); return i < 0 ? p : p.slice(i + 1) }
  const jarSizeCache = new Map()
  function jarSize(p) {
    // sizes are present per-classpath-entry on plugin/module objects; compute on demand
    if (jarSizeCache.size === 0) {
      const accum = (entries) => { for (const e of entries) for (const cp of (e.classPath ?? [])) jarSizeCache.set(cp, jarSizeCache.get(cp) ?? null) }
      accum(data.plugins); accum(data.modules)
      // jar bytes aren't stored per-jar in the model; reuse parent total / count as a fallback
      for (const arr of [data.plugins, data.modules]) {
        for (const e of arr) {
          const cps = e.classPath ?? []
          if (cps.length === 0) continue
          const each = (e.sizeBytes ?? 0) / cps.length
          for (const cp of cps) {
            if (jarSizeCache.get(cp) == null) jarSizeCache.set(cp, each || 1)
          }
        }
      }
    }
    return jarSizeCache.get(p) ?? 1
  }
  function push(m, k, v) { let a = m.get(k); if (!a) { a = []; m.set(k, a) } a.push(v) }

  // ---------- Graph (ECharts) ----------
  renderers.graph = () => {
    const v = document.getElementById("view-graph")
    const ownerPlugins = [...new Set(data.modules.map(m => m.ownerPlugin).filter(Boolean))].sort()
    const pluginSelect = el("select", {},
      el("option", { value: "" }, "(plugin overview)"),
      ...ownerPlugins.map(p => el("option", { value: p }, p)),
    )
    const depthInput = el("input", {
      type: "number", min: "1", max: "5", value: "1", style: "width: 56px;",
      title: "Number of dependency hops to expand from the plugin's own modules.",
    })
    const depthLabel = el("span", { class: "meta" }, "Depth:")
    const wrap = el("div", { class: "chart-wrap" })

    const pal = palette()
    const legend = el("div", { class: "legend" },
      el("span", { class: "swatch" }, ICON.coreExtracted(), "core / extracted"),
      el("span", { class: "swatch" }, ICON.coreEmbedded(), "core / embedded"),
      el("span", { class: "swatch" }, ICON.pluginExtracted(), "plugin / extracted"),
      el("span", { class: "swatch" }, ICON.pluginEmbedded(), "plugin / embedded"),
      el("span", { class: "swatch" }, ICON.pluginDescriptor(), "plugin descriptor"),
      el("span", { class: "sep" }, "│"),
      el("span", { class: "swatch" }, el("span", { class: "line", style: `color:${pal.fg};border-top-style:solid` }), "required / embedded"),
      el("span", { class: "swatch" }, el("span", { class: "line", style: `color:${pal.fg};border-top-style:dashed` }), "optional"),
      el("span", { class: "swatch" }, el("span", { class: "line", style: `color:${pal.fg};border-top-style:dotted` }), "on-demand"),
      el("span", { class: "sep" }, "│"),
      el("span", { class: "swatch" }, "→ = depends on"),
    )

    const note = el("div", { class: "hint" }, "")

    v.replaceChildren(
      el("div", { class: "chart-controls" },
        el("span", { class: "meta" }, "Plugin:"), pluginSelect,
        depthLabel, depthInput,
      ),
      legend, wrap, note,
    )

    const chart = echarts.init(wrap, null, { renderer: "canvas" })
    const ro = new ResizeObserver(() => chart.resize())
    ro.observe(wrap)

    chart.on("click", params => {
      if (params.dataType !== "node") return
      const d = params.data
      if (d?.__plugin) {
        pluginsState.focus = d.__plugin
        showTab("plugins")
        pluginsState.render()
      } else if (d?.__module) {
        modulesState.q = d.__module
        modulesState.openName = d.__module
        showTab("modules")
        modulesState.render()
      }
    })

    function update() {
      const plugin = pluginSelect.value
      const depth = +depthInput.value || 1
      depthLabel.style.display = plugin ? "" : "none"
      depthInput.style.display = plugin ? "" : "none"
      if (plugin) {
        const stats = focusedPluginGraph(plugin, depth)
        chart.setOption(stats.option, { notMerge: true })
        note.textContent = `Showing ${stats.ownedCount} module${stats.ownedCount === 1 ? "" : "s"} of ${plugin} and ${stats.depCount} dependency module${stats.depCount === 1 ? "" : "s"} up to ${depth} hop${depth === 1 ? "" : "s"} away. Bigger nodes belong to the plugin. Click a node to open it in Modules.`
      } else {
        const stats = pluginOverviewGraph()
        chart.setOption(stats.option, { notMerge: true })
        note.textContent = `Plugin overview: ${stats.nodeCount} plugins, ${stats.edgeCount} cross-plugin dependency relations. Node size = aggregate jar size. Click a plugin to open it in Plugins.`
      }
    }
    pluginSelect.addEventListener("change", update)
    depthInput.addEventListener("change", update)
    update()
  }

  function pluginOverviewGraph() {
    const pal = palette()
    const moduleByName = new Map(data.modules.map(m => [m.name, m]))
    const ownerOf = (n) => moduleByName.get(n)?.ownerPlugin
    const edgeWeight = new Map()
    for (const m of data.modules) {
      const from = m.ownerPlugin
      if (!from) continue
      for (const d of m.dependencies) {
        if (d.kind !== "module") continue
        const to = ownerOf(d.name)
        if (!to || to === from) continue
        const k = `${from} → ${to}`
        edgeWeight.set(k, (edgeWeight.get(k) ?? 0) + 1)
      }
    }
    const involved = new Set()
    for (const k of edgeWeight.keys()) { const [a, b] = k.split(" → "); involved.add(a); involved.add(b) }
    // also include owner-plugins without cross-deps so isolated plugins still appear
    for (const m of data.modules) if (m.ownerPlugin) involved.add(m.ownerPlugin)

    const sizes = new Map()
    for (const p of data.plugins) sizes.set(p.id, p.aggregateSizeBytes ?? 0)
    const maxSize = Math.max(1, ...involved.size ? [...involved].map(p => sizes.get(p) ?? 0) : [1])

    const nodes = [...involved].sort().map(p => ({
      id: p, name: p, __plugin: p,
      symbolSize: 10 + Math.sqrt((sizes.get(p) ?? 0) / maxSize) * 30,
      itemStyle: { color: pal.kind.plugin, borderColor: pal.fg, borderWidth: 0.5 },
      label: { show: (sizes.get(p) ?? 0) / maxSize > 0.15, color: pal.fg, fontSize: 10, position: "right" },
      tooltip: { formatter: () => `${p}<br/><span style="color:${pal.fgDim}">${bytes(sizes.get(p) ?? 0)}</span>` },
    }))
    const links = [...edgeWeight.entries()].map(([k, w]) => {
      const [from, to] = k.split(" → ")
      return { source: from, target: to, value: w, lineStyle: { width: Math.min(4, 0.4 + Math.log(1 + w) * 0.7) } }
    })

    return {
      nodeCount: nodes.length, edgeCount: links.length,
      option: {
        tooltip: {},
        animation: false,
        series: [{
          type: "graph", layout: "force", roam: true,
          force: { repulsion: 140, gravity: 0.08, edgeLength: [60, 140] },
          data: nodes, links,
          edgeSymbol: ["none", "arrow"],
          edgeSymbolSize: [0, 7],
          lineStyle: { color: pal.line, opacity: 0.55, curveness: 0.08 },
          emphasis: { focus: "adjacency", label: { show: true }, lineStyle: { color: pal.accent } },
        }],
      },
    }
  }

  function focusedPluginGraph(plugin, depth) {
    const pal = palette()
    const moduleByName = new Map(data.modules.map(m => [m.name, m]))
    const seeds = data.modules.filter(m => m.ownerPlugin === plugin).map(m => m.name)
    const kept = new Set(seeds)
    const tier = new Map()  // name → hop distance from seed (0 = owned)
    for (const n of seeds) tier.set(n, 0)
    let frontier = new Set(seeds)
    for (let i = 1; i <= depth; i++) {
      const next = new Set()
      for (const n of frontier) {
        const mod = moduleByName.get(n)
        if (!mod) continue
        for (const d of mod.dependencies) {
          if (d.kind !== "module" || !moduleByName.has(d.name)) continue
          if (!kept.has(d.name)) { kept.add(d.name); tier.set(d.name, i); next.add(d.name) }
        }
      }
      frontier = next
    }

    const nodes = []
    let ownedCount = 0, depCount = 0
    for (const name of kept) {
      const m = moduleByName.get(name)
      if (!m) continue
      const owned = tier.get(name) === 0
      if (owned) ownedCount++; else depCount++
      const borderType = m.embedded ? "solid" : m.loading === "on-demand" ? "dotted" : m.loading === "optional" ? "dashed" : "solid"
      nodes.push({
        id: name, name, __module: name,
        symbolSize: owned ? 22 : 11,
        itemStyle: {
          color: pal.kind[m.kind] ?? pal.fgDim,
          borderColor: m.embedded ? pal.fg : (owned ? pal.fg : pal.line),
          borderWidth: m.embedded ? 2 : (owned ? 1.5 : 0.8),
          borderType,
          opacity: owned ? 1 : 0.75,
        },
        label: { show: true, color: owned ? pal.fg : pal.fgDim, fontSize: owned ? 11 : 9, position: owned ? "right" : "right" },
        tooltip: { formatter: () => `${name}<br/><span style="color:${pal.fgDim}">${m.kind} · ${m.loading ?? "—"} · ${m.visibility} · ${m.ownerPlugin ?? "—"}</span>` },
      })
    }
    const links = []
    for (const name of kept) {
      const m = moduleByName.get(name)
      if (!m) continue
      for (const d of m.dependencies) {
        if (d.kind === "module" && kept.has(d.name)) links.push({ source: name, target: d.name })
      }
    }
    return {
      ownedCount, depCount,
      option: {
        tooltip: {},
        animation: false,
        series: [{
          type: "graph", layout: "force", roam: true,
          force: { repulsion: 110, gravity: 0.06, edgeLength: [50, 110] },
          data: nodes, links,
          edgeSymbol: ["none", "arrow"],
          edgeSymbolSize: [0, 6],
          lineStyle: { color: pal.line, opacity: 0.55, width: 0.9, curveness: 0.05 },
          emphasis: { focus: "adjacency", lineStyle: { width: 1.5, color: pal.accent } },
        }],
      },
    }
  }

  // ---------- Boot classpath ----------
  renderers.boot = () => {
    const v = document.getElementById("view-boot")
    const launches = data.launch.length ? data.launch : [{ os: "?", arch: "?", boot: [] }]
    const sel = el("select", {}, ...launches.map((l, i) => el("option", { value: i }, `${l.os} / ${l.arch}`)))
    const wrap = el("div")
    v.replaceChildren(el("div", { class: "chart-controls" }, el("span", { class: "meta" }, "OS/arch:"), sel), wrap)
    function update() {
      const l = launches[+sel.value]
      const totalSize = l.boot.reduce((s, j) => s + (j.sizeBytes ?? 0), 0)
      wrap.replaceChildren(
        el("div", { class: "hint" }, `${l.boot.length} JARs · ${bytes(totalSize)} total — ordered as loaded.`),
        el("div", { class: "list" }, ...l.boot.map((j, i) => el("div", { class: "row row-boot" },
          el("span", { class: "idx" }, String(i + 1).padStart(3, "0")),
          el("span", { class: "name" }, j.name),
          el("span", { class: "size" }, bytes(j.sizeBytes)),
        ))),
      )
    }
    sel.addEventListener("change", update)
    update()
  }

  // ---------- init ----------
  const startTab = (location.hash || "#overview").slice(1)
  showTab(["overview", "plugins", "modules", "treemap", "graph", "boot"].includes(startTab) ? startTab : "overview")
})()
