// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import "./main.css"
import {TreemapChart} from "echarts/charts"
import {CanvasRenderer} from "echarts/renderers"
import {init as initChart, throttle, use} from "echarts/core"
import {ToolboxComponent, TooltipComponent} from "echarts/components"
import prettyBytes from "pretty-bytes"

use([TooltipComponent, CanvasRenderer, TreemapChart, ToolboxComponent])

async function init() {
  const fileInput = document.getElementById("fileInput")
  fileInput.addEventListener("change", event => {
    handleFile(event.target.files[0])
  })

  const dropbox = document.body
  dropbox.addEventListener("dragenter", preventDefault)
  dropbox.addEventListener("dragover", preventDefault)
  dropbox.addEventListener("drop", event => {
    event.stopPropagation()
    event.preventDefault()

    const file = event.dataTransfer.files[0]
    // noinspection JSCheckFunctionSignatures
    const timeFormat = new Intl.DateTimeFormat("default", {
      timeStyle: "medium",
    })
    document.title = `${file.name} (${timeFormat.format(new Date())})`
    handleFile(file)
  })

  const data = localStorage.getItem("data")
  if (data != null) {
    updateChart(JSON.parse(data))
  }
}

let chart

function createChart() {
  const chartManager = new ChartManagerHelper(document.getElementById("app"))
  chart = chartManager.chart
  chart.setOption({
    toolbox: {
      feature: {
        saveAsImage: {},
      },
    },
    tooltip: {
      formatter: info => {
        let result = `${info.marker} ${info.name}`
        return result + `<span style="float: right; margin-left: 20px; font-weight: 900;">${prettyBytes(info.value, {binary: true})}</span>`
      },
    },
  })
}

function preventDefault(e) {
  e.stopPropagation();
  e.preventDefault();
}

function handleFile(selectedFile) {
  selectedFile.text().then(it => {
    localStorage.setItem("data", it)
    updateChart(JSON.parse(it))
  })
}

function updateChart(data) {
  if (chart == null) {
    createChart()
  }

  chart.setOption({
    series: [{
      type: "treemap",
      data,
      levels: [
        {},
        {
          colorSaturation: [0.35, 0.5],
          itemStyle: {
            borderWidth: 5,
            gapWidth: 1,
            borderColorSaturation: 0.6,
          },
          upperLabel: {show: true},
        },
      ],
      leafDepth: 2,
      label: {
        // formatter(data) {
        //   return `${(data.data as ItemExtraInfo).abbreviatedName} (${numberFormat.format(data["value"] as number)})`
        // }
      }
    }],
  })
}

class ChartManagerHelper {
 constructor(container) {
   this.chart = initChart(container)

   this.resizeObserver = new ResizeObserver(throttle(() => {
     this.chart.resize()
   }, 300))
   this.resizeObserver.observe(this.chart.getDom())
 }

 dispose() {
   this.resizeObserver.unobserve(this.chart.getDom())
   this.chart.dispose()
 }
}

init().catch(e => {
  console.error(e)
})