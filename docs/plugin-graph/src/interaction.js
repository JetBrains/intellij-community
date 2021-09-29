// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import pDebounce from "p-debounce"

import { GraphTextSearch} from "./GraphTextSearch"
import { GraphTooltipManager} from "./GraphTooltipManager"
import { GraphHighlighter} from "./GraphHighlighter"

export function init(graph, cy) {
  const search = new GraphTextSearch(graph, cy)
  // noinspection JSCheckFunctionSignatures
  document.getElementById("searchField").addEventListener("input", pDebounce(function (event) {
    return search.searchNodes(event.target.value.trim())
  }, 300))

  new GraphHighlighter(cy, search)
  new GraphTooltipManager(cy)
}