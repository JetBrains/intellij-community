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
          cytoscape: ["cytoscape", "cytoscape-cola"],
        },
      },
    }
  }
}