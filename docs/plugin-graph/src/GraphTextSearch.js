// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import FlexSearch from "flexsearch"

export class GraphTextSearch {
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
