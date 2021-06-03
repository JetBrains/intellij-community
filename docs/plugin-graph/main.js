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
import {NodeTooltipManager} from "./src/NodeTooltipManager"

import popper from "cytoscape-popper"

cytoscape.use(fCose)
cytoscape.use(popper)

function listener() {
  document.fonts.load("13px 'JetBrains Mono'", "a").then(function () {
    initGraph(graphData)
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
          "arrow-scale": 0.8,
        },
      },
      {
        selector: "edge[type=1]",
        style: {
          "target-arrow-shape": "square",
          // square is too big
          "arrow-scale": 0.5,
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
  new NodeTooltipManager(cy)
}