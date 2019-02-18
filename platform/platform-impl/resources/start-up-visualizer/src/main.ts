// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4core from "@amcharts/amcharts4/core"

import am4themes_animated from "@amcharts/amcharts4/themes/animated"
import {ComponentsChart} from "./components"
import {TimelineChart} from "./timeline"

export function main() {
  am4core.useTheme(am4themes_animated)

  const g = window as any
  const dataListeners = configureInput(document.getElementById("ijInput")!! as HTMLTextAreaElement)

  const timeLineChart = new TimelineChart(document.getElementById("visualization")!!)

  const componentsChart = new ComponentsChart(document.getElementById("componentsVisualization")!!)
  // debug
  g.componentsChart = componentsChart

  dataListeners.push((data: any) => {
    g.lastData = data
    timeLineChart.render(data)
    componentsChart.render(data)
  })
}

export interface Item {
  name: string

  start: number
  end: number

  duration: number

  // added data
  shortName: string
  // relativeStart: number
}

export interface InputData {
  items: Array<Item>
  components?: Array<Item>
}

function configureInput(inputElement: HTMLTextAreaElement): Array<(data: InputData) => void> {
  const dataListeners: Array<(data: InputData) => void> = []
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

  function callListeners(rawData: string) {
    const data = JSON.parse(rawData)
    for (const dataListener of dataListeners) {
      dataListener(data)
    }
  }

  return dataListeners
}