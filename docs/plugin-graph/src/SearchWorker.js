// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {expose} from "comlink"
import Document from "flexsearch/src/document"
import {is_string} from "flexsearch/src/common"

// flexsearch is the only lib that able to handle search queries like ruby (prefix search)
// or com.intellij.microservices.config (a lot of not related results due to common prefix com.intellij or common word config)
const index = new Document({
  document: {
    id: "id",
    index: ["name", "pluginId", "sourceModule", "package"],
  },
})

expose({
  add(data) {
    for (const document of data) {
      index.addFixed(document.id, document)
    }
  },
  search(query) {
    const result = index.search(query)
    const ids = new Set()
    for (const resultPerField of result) {
      for (const id of resultPerField.result) {
        ids.add(id)
      }
    }
    return ids
  }
})

// fix adding null fields
Document.prototype.addFixed = function (id, content) {
  for (let i = 0, tree, field; i < this.field.length; i++) {
    field = this.field[i]
    tree = this.tree[i]
    if (is_string(tree)) {
      tree = [tree]
    }

    add_index(content, tree, this.marker, 0, this.index[field], id, tree[0], false)
  }
}

function add_index(obj, tree, marker, pos, index, id, key, _append) {
  obj = obj[key]
  if (obj == null) {
    return
  }

  // reached target field
  if (pos === (tree.length - 1)) {
    // handle target value
    index.add(id, obj, _append, /* skip_update: */ true)
  }
  else if (obj) {
    if (Array.isArray(obj)) {
      for (let i = 0; i < obj.length; i++) {
        // do not increase index, an array is not a field
        add_index(obj, tree, marker, pos, index, id, i, _append)
      }
    }
    else {
      key = tree[++pos]
      add_index(obj, tree, marker, pos, index, id, key, _append)
    }
  }
}