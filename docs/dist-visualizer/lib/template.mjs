import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const __dirname = dirname(fileURLToPath(import.meta.url))
const ASSETS = join(__dirname, "..", "assets")

const ECHARTS_CDN = "https://cdn.jsdelivr.net/npm/echarts@5.5.1/dist/echarts.min.js"

export function buildHtml(data) {
  const css = readFileSync(join(ASSETS, "viewer.css"), "utf8")
  const js = readFileSync(join(ASSETS, "viewer.js"), "utf8")
  const title = `${data.product.name} ${data.product.version} · dist viewer`
  const dataJson = JSON.stringify(data).replace(/</g, "\\u003c")
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)}</title>
<script>
  (() => {
    const t = localStorage.getItem("distViewerTheme")
      || (matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark")
    document.documentElement.dataset.theme = t
  })()
</script>
<style>${css}</style>
</head>
<body>
<div class="app">
  <header class="topbar">
    <div class="brand"><span class="product">…</span><span class="build">…</span></div>
    <nav class="tabs">
      <button data-tab="overview" class="active">Overview</button>
      <button data-tab="plugins">Plugins</button>
      <button data-tab="modules">Modules</button>
      <button data-tab="treemap">Treemap</button>
      <button data-tab="graph">Graph</button>
      <button data-tab="boot">Boot</button>
    </nav>
    <button class="theme-toggle" title="Toggle light/dark theme">◐</button>
  </header>
  <main class="content">
    <section id="view-overview" class="view"></section>
    <section id="view-plugins" class="view hidden"></section>
    <section id="view-modules" class="view hidden"></section>
    <section id="view-treemap" class="view hidden"></section>
    <section id="view-graph" class="view hidden"></section>
    <section id="view-boot" class="view hidden"></section>
  </main>
</div>
<script>window.__DIST_DATA__ = ${dataJson};</script>
<script src="${ECHARTS_CDN}" defer></script>
<script defer>${js}</script>
</body>
</html>
`
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c])
}
