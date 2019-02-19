// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4core from "@amcharts/amcharts4/core"

import am4themes_animated from "@amcharts/amcharts4/themes/animated"
import {ComponentsChart} from "./components"
import {TimelineChartManager} from "./timeline"
import {ChartManager, InputData} from "./core"

const storageKeyData = "inputIjFormat"
const storageKeyPort = "ijPort"

export function main(): void {
  am4core.useTheme(am4themes_animated)

  const chartManagers: Array<ChartManager> = [
    new TimelineChartManager(document.getElementById("visualization")!!),
    new ComponentsChart(document.getElementById("componentsVisualization")!!),
  ]
  // debug
  const global = window as any
  global.componentsChart = chartManagers[1]

  configureInput(document.getElementById("ijInput")!! as HTMLTextAreaElement, data => {
    global.lastData = data
    for (const chartManager of chartManagers) {
      chartManager.render(data)
    }
  })
}

function configureInput(inputElement: HTMLTextAreaElement, dataListener: (data: InputData) => void): void {
  function callListener(rawData: string) {
    dataListener(JSON.parse(rawData))
  }

  function setInput(rawData: string | null) {
    if (rawData != null && rawData.length !== 0) {
      inputElement.value = rawData
      callListener(rawData)
    }
  }

  const portInputElement = document.getElementById("ijPort") as HTMLInputElement

  window.addEventListener("load", () => {
    portInputElement.value = localStorage.getItem(storageKeyPort) || "63342"
    setInput(localStorage.getItem(storageKeyData))
  })

  function grabFromRunningInstance(port: string) {
    fetch(`http://localhost:${port}/api/about/?startUpMeasurement`, {credentials: "omit"})
      .then(it => it.json())
      .then(json => setInput(JSON.stringify(json.startUpMeasurement || {items: []}, null, 2)))
  }

  getButton("grabButton").addEventListener("click", () => {
    // use parseInt to validate input
    let port = portInputElement.value
    if (port.length === 0) {
      port = "63342"
    } else if (!/^\d+$/.test(port)) {
      throw new Error("Port number value is not numeric")
    }

    localStorage.setItem(storageKeyPort, port)
    grabFromRunningInstance(port)
  })

  getButton("grabDevButton").addEventListener("click", () => {
    grabFromRunningInstance("63343")
  })

  inputElement.addEventListener("input", () => {
    const rawData = inputElement.value.trim()
    localStorage.setItem(storageKeyData, rawData)
    callListener(rawData)
  })
}

function getButton(id: string): HTMLButtonElement {
  return document.getElementById(id) as HTMLButtonElement
}