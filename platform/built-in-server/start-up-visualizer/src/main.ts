// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4core from "@amcharts/amcharts4/core"

import am4themes_animated from "@amcharts/amcharts4/themes/animated"
import {ComponentsChartManager, TopHitProviderChart} from "./ItemChartManager"
import {TimelineChartManager} from "./TimeLineChartManager"
import {ChartManager, getButtonElement, getInputElement, InputData} from "./core"

const storageKeyPort = "ijPort"
const storageKeyData = "inputIjFormat"

export function main(): void {
  am4core.useTheme(am4themes_animated)

  const chartManagers: Array<ChartManager> = [
    new TimelineChartManager(document.getElementById("visualization")!!),
    new ComponentsChartManager(document.getElementById("componentChart")!!),
    new TopHitProviderChart(document.getElementById("optionsTopHitProviderChart")!!),
  ]

  const global = window as any
  global.timelineChart = chartManagers[0]
  global.componentsChart = chartManagers[1]

  new InputFormManager(data => {
    for (const chartManager of chartManagers) {
      chartManager.render(data)
    }
  })
}

class InputFormManager {
  constructor(private dataListener: (data: InputData) => void) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", () => {
        this.configureElements()
      })
    }
    else {
      this.configureElements()
    }
  }

  private callListener(rawData: string) {
    this.dataListener(JSON.parse(rawData))
  }

  private setInput(rawData: string | null) {
    if (rawData != null && rawData.length !== 0) {
      getInputElement("ijInput").value = rawData
      this.callListener(rawData)
    }
  }

  private configureElements(): void {
    const inputElement = getInputElement("ijInput")

    getPortInputElement().value = localStorage.getItem(storageKeyPort) || "63342"
    this.setInput(localStorage.getItem(storageKeyData))

    getButtonElement("grabButton").addEventListener("click", () => {
      // use parseInt to validate input
      let port = getPortInputElement().value
      if (port.length === 0) {
        port = "63342"
      } else if (!/^\d+$/.test(port)) {
        throw new Error("Port number value is not numeric")
      }

      localStorage.setItem(storageKeyPort, port)
      this.grabFromRunningInstance(port)
    })

    getButtonElement("grabDevButton").addEventListener("click", () => {
      this.grabFromRunningInstance("63343")
    })

    inputElement.addEventListener("input", () => {
      const rawData = inputElement.value.trim()
      localStorage.setItem(storageKeyData, rawData)
      this.callListener(rawData)
    })
  }

  private grabFromRunningInstance(port: string) {
    const host = `localhost:${port}`

    function showError(reason: any) {
      alert(`Cannot load data from "${host}": ${reason}`)
    }

    const controller = new AbortController()
    const signal = controller.signal
    const timeoutId = setTimeout(() => {
      controller.abort()
      showError("8 seconds timeout")
    }, 8000)

    fetch(`http://${host}/api/about/?startUpMeasurement`, {credentials: "omit", signal})
      .then(it => it.json())
      .then(json => {
        clearTimeout(timeoutId)

        const data = json.startUpMeasurement
        if (data == null) {
          const message = "IntelliJ Platform IDE didn't report startup measurement result"
          console.error(message, json)
          alert(message)
          return
        }

        const rawData = JSON.stringify(data, null, 2)
        localStorage.setItem(storageKeyData, rawData)
        this.setInput(rawData)
      })
      .catch(e => {
        clearTimeout(timeoutId)
        console.error(e)
        if (!(e instanceof (window as any).AbortError)) {
          showError(e)
        }
      })
  }
}

function getPortInputElement() {
  return getInputElement("ijPort")
}

main()