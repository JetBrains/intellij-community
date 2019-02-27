// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4core from "@amcharts/amcharts4/core"

import am4themes_animated from "@amcharts/amcharts4/themes/animated"
import {ComponentChartManager, ServiceChartManager, TopHitProviderChart} from "./ItemChartManager"
import {TimelineChartManager} from "./TimeLineChartManager"
import {ChartManager, getButtonElement, getInputElement, InputData} from "./core"

const storageKeyPort = "ijPort"
const storageKeyData = "inputIjFormat"

function renderDataForCharts(chartManagers: Array<ChartManager>, lastData: InputData, offset: number = 0) {
  for (const chartManager of (offset == 0 ? chartManagers : chartManagers.slice(offset))) {
    chartManager.render(lastData)
  }
}

function main(): void {
  am4core.useTheme(am4themes_animated)

  const chartManagers: Array<ChartManager> = []
  createTimeLineChart(chartManagers)
  createItemChartManagers(chartManagers)

  const global = window as any
  global.chartManagers = chartManagers

  let lastData: InputData | null = null
  new InputFormManager(data => {
    lastData = data
    for (const chartManager of chartManagers) {
      chartManager.render(data)
    }
  })

  if (module != null && module.hot != null) {
    module.hot.accept("./ItemChartManager", function() {
      chartManagers.length = 1
      createItemChartManagers(chartManagers)
      if (lastData != null) {
        renderDataForCharts(chartManagers, lastData, 1)
      }
    })
    module.hot.accept("./core", function() {
      // reload all charts (and only charts, ignore)
      chartManagers.length = 0
      createTimeLineChart(chartManagers)
      createItemChartManagers(chartManagers)
      if (lastData != null) {
        renderDataForCharts(chartManagers, lastData)
      }
    })

    // https://webpack.js.org/api/hot-module-replacement/#accept-self
    module.hot.accept()
  }
}

function createTimeLineChart(chartManagers: Array<ChartManager>): void {
  chartManagers.push(new TimelineChartManager(document.getElementById("visualization")!!))
}

function createItemChartManagers(chartManagers: Array<ChartManager>): void {
  chartManagers.push(new ComponentChartManager(document.getElementById("componentChart")!!))
  chartManagers.push(new ServiceChartManager(document.getElementById("serviceChart")!!))
  chartManagers.push(new TopHitProviderChart(document.getElementById("optionsTopHitProviderChart")!!))
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

    fetch(`http://${host}/api/startUpMeasurement`, {credentials: "omit", signal})
      .then(it => it.json())
      .then(data => {
        clearTimeout(timeoutId)

        if (data == null) {
          const message = "IntelliJ Platform IDE didn't report startup measurement result"
          console.error(message)
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