// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4core from "@amcharts/amcharts4/core"

import am4themes_animated from "@amcharts/amcharts4/themes/animated"
import {ComponentsChartManager} from "./ComponentsChartManager"
import {TimelineChartManager} from "./timeline"
import {ChartManager, InputData} from "./core"

const storageKeyData = "inputIjFormat"
const storageKeyPort = "ijPort"

export function main(): void {
  am4core.useTheme(am4themes_animated)

  const chartManagers: Array<ChartManager> = [
    new TimelineChartManager(document.getElementById("visualization")!!),
    new ComponentsChartManager(document.getElementById("componentsVisualization")!!),
  ]
  // debug
  const global = window as any
  global.timelineChart = chartManagers[0]
  global.componentsChart = chartManagers[1]

  configureInput(data => {
    global.lastData = data
    for (const chartManager of chartManagers) {
      chartManager.render(data)
    }
  })
}

function configureInput(dataListener: (data: InputData) => void): void {
  const inputElement = getInputElement("ijInput")

  function callListener(rawData: string) {
    dataListener(JSON.parse(rawData))
  }

  function setInput(rawData: string | null) {
    if (rawData != null && rawData.length !== 0) {
      inputElement.value = rawData
      callListener(rawData)
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    getPortInputElement().value = localStorage.getItem(storageKeyPort) || "63342"
    setInput(localStorage.getItem(storageKeyData))
  })

  function grabFromRunningInstance(port: string) {
    fetch(`http://localhost:${port}/api/about/?startUpMeasurement`, {credentials: "omit"})
      .then(it => it.json())
      .then(json => {
        const data = json.startUpMeasurement
        if (data == null) {
          const message = "IntelliJ Platform IDE didn't report startup measurement result"
          console.error(message, json)
          alert(message)
          return
        }

        const rawData = JSON.stringify(data, null, 2)
        localStorage.setItem(storageKeyData, rawData)
        setInput(rawData)
      })
  }

  getButtonElement("grabButton").addEventListener("click", () => {
    // use parseInt to validate input
    let port = getPortInputElement().value
    if (port.length === 0) {
      port = "63342"
    } else if (!/^\d+$/.test(port)) {
      throw new Error("Port number value is not numeric")
    }

    localStorage.setItem(storageKeyPort, port)
    grabFromRunningInstance(port)
  })

  getButtonElement("grabDevButton").addEventListener("click", () => {
    grabFromRunningInstance("63343")
  })

  inputElement.addEventListener("input", () => {
    const rawData = inputElement.value.trim()
    localStorage.setItem(storageKeyData, rawData)
    callListener(rawData)
  })
}

function getPortInputElement() {
  return getInputElement("ijPort")
}

function getInputElement(id: string): HTMLInputElement {
  return document.getElementById(id) as HTMLInputElement
}

function getButtonElement(id: string): HTMLButtonElement {
  return document.getElementById(id) as HTMLButtonElement
}