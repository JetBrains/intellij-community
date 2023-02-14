// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export class GraphHighlighter {
  constructor(cy, graphTextSearch) {
    this.cy = cy
    this.graphTextSearch = graphTextSearch

    let timeout = null
    cy.on("mouseover", "node", e => {
      const target = e.target
      timeout = setTimeout(() => {
        timeout = null
        this.selectNode(target)
      }, 100)
    })
    cy.on("mouseout", "node", e => {
      if (timeout === null) {
        this.deselectNode(e.target)
      }
      else {
        clearTimeout(timeout)
        timeout = null
      }
    })
  }

  selectNode(selection) {
    const cy = this.cy

    if (!this.graphTextSearch.totalUnion.empty()) {
      cy.elements().difference(this.graphTextSearch.totalUnion).removeClass("semiTransparent")
    }

    const toHighlight = GraphHighlighter.computeToHighlight(selection)

    cy.elements().difference(toHighlight).difference(this.graphTextSearch.totalUnion).addClass("semiTransparent")
    toHighlight.difference(this.graphTextSearch.totalUnion).addClass("highlight")
  }

  static computeToHighlight(selection) {
    let result = null
    if (selection.isParent()) {
      for (const child of selection.children()) {
        const partialResult = child.outgoers().union(child.incomers())
        result = result === null ? partialResult : result.union(partialResult)
      }
    }
    else {
      result = selection.outgoers().union(selection.incomers())
      const parent = selection.parent()
      if (parent != null) {
        result = result.union(parent)
      }
    }
    return result.union(selection)
  }

  deselectNode(selection) {
    const cy = this.cy
    cy.elements().removeClass("semiTransparent")
    if (!this.graphTextSearch.totalUnion.empty()) {
      cy.elements().difference(this.graphTextSearch.totalUnion).addClass("semiTransparent")
    }

    GraphHighlighter.computeToHighlight(selection).difference(this.graphTextSearch.totalUnion).removeClass("highlight")
  }
}