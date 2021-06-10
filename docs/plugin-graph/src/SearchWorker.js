// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {expose} from "comlink"
import MiniSearch from "minisearch"

const index = new MiniSearch({
  fields: ["name", "pluginId", "sourceModule", "package"],
  searchOptions: {
    boost: {name: 4, pluginId: 3, sourceModule: 2},
    prefix: true,
  },
})

expose({
  add(data) {
    index.addAll(data)
  },
  search(query) {
    return index.search(query)
  }
})