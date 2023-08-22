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
      index.add(document.id, document)
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