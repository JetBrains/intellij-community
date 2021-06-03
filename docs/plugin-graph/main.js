// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import "@fontsource/jetbrains-mono/400.css"
import "@fontsource/jetbrains-mono/600.css"

import "./style.css"

import graphData from "./plugin-graph.json"
import cytoscape from "cytoscape"
// noinspection SpellCheckingInspection
import fCose from "cytoscape-fcose"

import {GraphTextSearch} from "./src/GraphTextSearch"
import {GraphHighlighter} from "./src/GraphHighlighter"
import {GraphTooltipManager} from "./src/GraphTooltipManager"

import popper from "cytoscape-popper"

cytoscape.use(fCose)
cytoscape.use(popper)

function listener() {
  Promise.all([
    document.fonts.load("11px 'JetBrains Mono'"),
    document.fonts.load("13px 'JetBrains Mono'"),
    document.fonts.load("bold 13px 'JetBrains Mono'")
  ]).then(function () {
    const fileInput = document.getElementById("fileInput")
    fileInput.addEventListener("change", event => {
      handleFile(event.target.files[0])
    })

    const dropbox = document.body
    dropbox.addEventListener("dragenter", preventDefault);
    dropbox.addEventListener("dragover", preventDefault);
    dropbox.addEventListener("drop", event => {
      event.stopPropagation()
      event.preventDefault()

      const file = event.dataTransfer.files[0]
      const timeFormat = new Intl.DateTimeFormat('default', {
        timeStyle: "medium",
      })
      document.title = `${file.name} (${timeFormat.format(new Date())})`
      handleFile(file)
    });

    initGraph(graphData)
  })
}

function preventDefault(e) {
  e.stopPropagation();
  e.preventDefault();
}

function handleFile(selectedFile) {
  selectedFile.text().then(it => {
    initGraph(JSON.parse(it))
  })
}

if (document.readyState === "loading") {
  window.addEventListener("DOMContentLoaded", listener)
}
else {
  listener()
}

function getItemSizeStyle(factor) {
  const baseNodeDiameter = 10
  const size = `${Math.ceil(baseNodeDiameter * factor)}px`
  return {
    "width": size,
    "height": size,
  }
}

function initGraph(graph) {
  // noinspection SpellCheckingInspection
  const layoutOptions = {
    name: "fcose",
    quality: "proof",
    randomize: false,
    nodeDimensionsIncludeLabels: true,
  }

  // noinspection SpellCheckingInspection
  const cy = cytoscape({
    container: document.getElementById("cy"),
    elements: graph,
    layout: layoutOptions,
    minZoom: 0.4,
    maxZoom: 3,
    autoungrabify: true,

    style: [
      {
        selector: "node",
        style: {
          "label": "data(n)",
          "font-family": "JetBrains Mono",
          "font-size": 13,
          "color": "#515151",
        },
      },

      // compound node - smaller label font size
      {
        selector: "node[^sourceModule]",
        style: {
          "font-size": 11,
          "color": "#515151",
        },
      },
      {
      	selector: ":parent",
      	style: {
      		"shape" : "roundrectangle",
      	}
      },

      {
        selector: "node[type=0]",
        style: getItemSizeStyle(1),
      },
      {
        selector: "node[type=1]",
        style: getItemSizeStyle(1.2),
      },
      {
        selector: "node[type=2]",
        style: getItemSizeStyle(1.4),
      },
      {
        selector: "edge",
        style: {
          "curve-style": "straight",
          "width": 1,
        }
      },
      {
        selector: "edge[type=0]",
        style: {
          "target-arrow-shape": "triangle-backcurve",
        },
      },
      {
        selector: "edge[type=1]",
        style: {
          "target-arrow-shape": "square",
          // square is too big
          "arrow-scale": 0.7,
        },
      },

      // highlighting (https://stackoverflow.com/a/38468892)
      {
        selector: "node.semiTransparent",
        style: {"opacity": "0.5"}
      },
      {
        selector: "edge.highlight",
        style: {"mid-target-arrow-color": "#FFF"}
      },
      {
        selector: "edge.semiTransparent",
        style: {"opacity": "0.2"}
      },

      {
        selector: "node.found",
        style: {
          "font-weight": "600",
        },
      },
    ]
  })

  // ensure that dragging of element causes panning and not selecting
  // https://github.com/cytoscape/cytoscape.js/issues/1905
  cy.elements().panify()

  function debounce(func) {
    let timeout
    return function (...args) {
      clearTimeout(timeout)
      const handler = function() {
        func.apply(null, args)
      }
      timeout = setTimeout(handler, 300)
    }
  }

  const search = new GraphTextSearch(graph, cy)
  document.getElementById("searchField").addEventListener("input", debounce(function (event) {
    search.searchNodes(event.target.value.trim())
  }))

  new GraphHighlighter(cy, search)
  new GraphTooltipManager(cy)
}