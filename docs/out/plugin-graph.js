// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
fetch("plugin-graph.json")
  .then(it => it.json())
  .then(graph => {
    const listener = () => initGraph(graph)
    if (document.readyState === "loading") {
      window.addEventListener("DOMContentLoaded", listener)
    }
    else {
      listener()
    }
  })

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
          "label": (e) => {
            const name = e.data("name")
            if (name.startsWith("intellij.")) {
              return `i.${name.substring("intellij.".length)}`
            }
            else if (name.startsWith("com.intellij.modules.")) {
              return `c.i.m.${name.substring("com.intellij.modules.".length)}`
            }
            else {
              return name
            }
          },
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
}

function buildTooltip(lines) {
  let result = ""
  for (const line of lines) {
    if (line.main) {
      result += `<span style="user-select: text">${line.name}</span>`
    }
    else {
      result += `<br/>${line.name}`
    }
    const valueStyleClass = line.selectable ? "tooltipSelectableValue" : (line.main ? "tooltipMainValue" : "tooltipValue")
    if (line.value != null) {
      result += `<span class="${valueStyleClass}"`
      if (line.extraStyle != null && line.extraStyle.length > 0) {
        result += ` style="${line.extraStyle}"`
      }
      if (line.hint != null && line.hint.length !== 0) {
        result += ` title="${line.hint}"`
      }
      result += `>${line.value}</span>`
    }
  }
  return result
}

class GraphHighlighter {
  constructor(cy, graphTextSearch) {
    this.cy = cy
    this.graphTextSearch = graphTextSearch

    cy.on("mouseover", "node", e => {
      this.selectNode(e.target)
    })
    cy.on("mouseout", "node", e => {
      this.deselectNode(e.target)
    })
  }

  selectNode(selection) {
    const cy = this.cy

    if (!this.graphTextSearch.totalUnion.empty()) {
      cy.elements().difference(this.graphTextSearch.totalUnion).removeClass("semiTransparent")
    }

    const toHighlight = selection.outgoers().union(selection.incomers()).union(selection)
    cy.elements().difference(toHighlight).difference(this.graphTextSearch.totalUnion).addClass("semiTransparent")
    toHighlight.difference(this.graphTextSearch.totalUnion).addClass("highlight")
  }

  deselectNode(selection) {
    const cy = this.cy
    cy.elements().removeClass("semiTransparent")
    if (!this.graphTextSearch.totalUnion.empty()) {
      cy.elements().difference(this.graphTextSearch.totalUnion).addClass("semiTransparent")
    }

    selection.outgoers().union(selection.incomers()).union(selection).difference(this.graphTextSearch.totalUnion).removeClass("highlight")
  }
}

class GraphTextSearch {
  constructor(graph, cy) {
    this.cy = cy
    this.selectedNodes = new Set()
    this.totalUnion = cy.collection()

    this.index = new FlexSearch({
      tokenize: "strict",
      depth: 3,
      doc: {
        id: "data:id",
        field: [
          "data:name",
          "data:pluginId",
          "data:sourceModule",
          "data:package",
        ]
      }
    })
    this.index.add(graph)
  }

  searchNodes(text) {
    const {selectedNodes, cy, index} = this

    const newNodes = []
    if (text.length !== 0) {
      for (const item of index.search(text)) {
        const node = cy.getElementById(item.data.id)
        if (node == null) {
          console.error(`Cannot find node by id ${item.data.id}`)
        }
        selectedNodes.delete(node)
        newNodes.push(node)
      }
    }

    cy.elements().removeClass("semiTransparent")
    for (const prevNode of selectedNodes) {
      prevNode.removeClass(["highlight", "found"])
      prevNode.outgoers().union(prevNode.incomers()).removeClass("highlight")
    }

    selectedNodes.clear()
    if (newNodes.length === 0) {
      this.totalUnion = cy.collection()
      return
    }

    let totalUnion = null

    for (const newNode of newNodes) {
      selectedNodes.add(newNode)

      const union = newNode.outgoers().union(newNode.incomers())
      totalUnion = totalUnion == null ? union : totalUnion.union(union)
      totalUnion = totalUnion.union(newNode)

      newNode.addClass("found")
    }

    cy.elements().difference(totalUnion).addClass("semiTransparent")
    totalUnion.addClass("highlight")
    this.totalUnion = totalUnion

    cy.animate({
      // pan: totalUnion.boundingBox(),
      // center: {eles: totalUnion},
      fit: {eles: totalUnion},
    })
  }
}

function shortenPath(p) {
  const prefix = "plugins/"
  if (p.startsWith(prefix)) {
    p = p.substring(prefix.length)
  }
  return p
    .replace("/resources/META-INF/", " ")
    .replace("/src/main/resources/", " ")
    .replace("/META-INF/", " ")
    .replace("/resources/", " ")
    .replace("/java/src/main/", " ")
    .replace("/src/main/", " ")
    .replace("/src/", " ")
}