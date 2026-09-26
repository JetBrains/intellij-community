#!/usr/bin/env bun
// IDEA Dist Plugin-Model Visualizer
//
// Usage:
//   bun community/docs/dist-visualizer/visualize.mjs [path-to-dist]
//
// path-to-dist defaults to /Applications/Idea.app. Accepts:
//   - macOS .app bundle (e.g. /Applications/Idea.app, /Applications/PyCharm.app)
//   - Linux/Windows install root (the dir containing product-info.json + lib/)
//   - Contents/Resources directly
//
// The script reads the dist, builds an enriched data model around the Plugin Model v2
// + Product DSL concepts (plugins, content modules, loading modes, embedded modules,
// visibility, dependency edges, boot classpath), writes one self-contained HTML
// file to a temp path, and opens it in the default browser.

import { writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { spawn } from "node:child_process"

import { buildModel } from "./lib/product-info.mjs"
import { buildHtml } from "./lib/template.mjs"

const arg = process.argv[2] ?? "/Applications/Idea.app"

console.log(`> reading ${arg}`)
const t0 = performance.now()
const model = buildModel(arg)
const t1 = performance.now()
console.log(`> ${model.product.name} ${model.product.version} (${model.product.buildNumber}) · ${model.stats.layoutCount} layout entries · ${model.stats.pluginCount} plugins · ${model.stats.moduleCount} modules · ${model.stats.edgeCount} edges  [${(t1 - t0).toFixed(0)} ms]`)

const html = buildHtml(model)
const tag = `${(model.product.productCode ?? "ide")}-${(model.product.buildNumber ?? "build").replace(/[^\w.-]+/g, "_")}`
const out = join(tmpdir(), `idea-dist-viewer-${tag}.html`)
writeFileSync(out, html)
console.log(`> wrote ${out} (${(html.length / 1024).toFixed(1)} KB)`)

openInBrowser(out)

function openInBrowser(path) {
  const platform = process.platform
  const cmd = platform === "darwin" ? "open" : platform === "win32" ? "cmd" : "xdg-open"
  const args = platform === "win32" ? ["/c", "start", "", path] : [path]
  const child = spawn(cmd, args, { stdio: "ignore", detached: true })
  child.on("error", err => {
    console.error(`> could not open browser (${err.message}). Open this URL manually:`)
    console.error(`  file://${path}`)
  })
  child.unref()
}
