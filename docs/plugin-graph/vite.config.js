// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export default {
  plugins: [
    {
      name: "vite-plugin-fontsource",
      apply: "build",
      transformIndexHtml(html, context) {
        const tags = []
        for (const item of Object.values(context.bundle)) {
          if (item.type !== "asset" || !item.fileName.endsWith(".woff2")) {
            continue
          }

          tags.push({
            tag: "link",
            attrs: {
              rel: "preload",
              href: `/${item.fileName}`,
              as: "font",
              type: "font/woff2",
              crossorigin: true,
            },
            injectTo: "head",
          })
        }
        return tags
      },
    }
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          cytoscape: ["cytoscape", "cytoscape-fcose", "cytoscape/dist/cytoscape.esm.js"]
        },
      },
    }
  }
}

function injectFonts({
                       families,
                       text,
                       preconnect = true,
                       display = 'swap',
                     }) {
  const specs = []
  const deferredSpecs = []
  const tags = []

  for (const family of families) {
    if (typeof family === "string") {
      deferredSpecs.push(family)
      continue
    }

    const {
      name,
      styles,
      defer = true,
    } = family


    let spec = name

    if (typeof styles === 'string')
      spec += `:${styles}`

    if (defer)
      deferredSpecs.push(spec)
    else
      specs.push(spec)
  }

  // warm up the fonts’ origin
  if (preconnect && specs.length + deferredSpecs.length > 0) {
    tags.push({
      tag: 'link',
      attrs: {
        rel: 'preconnect',
        href: 'https://fonts.gstatic.com/',
        crossorigin: true,
      },
    })
  }

  // defer loading font-faces definitions
  // @see https://web.dev/optimize-lcp/#defer-non-critical-css
  if (deferredSpecs.length > 0) {
    let href = `${GoogleFontsBase}?family=${deferredSpecs.join('&family=')}`

    if (typeof display === 'string' && display !== 'auto')
      href += `&display=${display}`

    if (typeof text === 'string' && text.length > 0)
      href += `&text=${text}`

    tags.push({
      tag: "link",
      attrs: {
        rel: "preload",
        as: "font",
        onload: 'this.rel=\'stylesheet\'',
        href,
      },
    })
  }

  // load critical fonts
  if (specs.length > 0) {
    let href = `${GoogleFontsBase}?family=${specs.join('&family=')}`

    if (typeof display === 'string' && display !== 'auto')
      href += `&display=${display}`

    if (typeof text === 'string' && text.length > 0)
      href += `&text=${text}`

    tags.push({
      tag: 'link',
      attrs: {
        rel: 'stylesheet',
        href,
      },
    })
  }

  return tags
}