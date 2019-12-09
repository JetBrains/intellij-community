// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export function loadJson(url: string, processed: (() => void) | null, notificationManager: any): Promise<any> {
  console.log("load", url)

  function showError(reason: string) {
    notificationManager.error({
      title: "Error",
      message: `Cannot load data from "${url}": ${reason}`,
    })
  }

  let isCancelledByTimeout = false
  const controller = new AbortController()
  const signal = controller.signal
  const timeoutId = setTimeout(() => {
    isCancelledByTimeout = true
    controller.abort()
    showError("8 seconds timeout")
  }, 8000)

  return fetch(url, {credentials: "omit", signal})
    .then(it => it.json())
    .then(data => {
      clearTimeout(timeoutId)

      if (processed != null) {
        processed()
      }

      if (data == null) {
        showError("Server returns empty result")
        return null
      }

      return data
    })
    .catch(e => {
      clearTimeout(timeoutId)

      if (processed != null) {
        processed()
      }

      if (!isCancelledByTimeout) {
        showError(e.toString())
      }

      console.warn(`Cannot load data from "${url}"`, e)
    })
}