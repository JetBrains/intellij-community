// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
"use strict"

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
function configureInput(inputElement) {
  const dataListeners = []
  const storageKey = "inputIjFormat"

  function restoreOldData() {
    let oldData = localStorage.getItem(storageKey)
    if (oldData != null && oldData.length > 0) {
      inputElement.value = oldData
      callListeners(oldData)
    }
  }

  window.addEventListener("load", event => {
    restoreOldData()
  })

  inputElement.addEventListener("input", () => {
    const rawString = inputElement.value.trim()
    localStorage.setItem(storageKey, rawString)
    callListeners(rawString)
  })

  function callListeners(rawData) {
    const data = JSON.parse(rawData)
    for (const dataListener of dataListeners) {
      dataListener(data)
    }
  }

  return dataListeners
}