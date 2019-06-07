// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {ChartManager} from "@/charts/ChartManager"
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {DataManager} from "@/state/DataManager"
import {IconData} from "@/state/data"

export class TreeMapChartManager implements ChartManager {
  private readonly chart: am4charts.TreeMap

  constructor(container: HTMLElement) {
    const chart = am4core.create(container, am4charts.TreeMap)
    this.chart = chart
    chart.dataFields.value = "duration"
    chart.dataFields.name = "name"
    chart.dataFields.children = "children"

    const level1 = chart.seriesTemplates.create("0")
    const level1Bullet = level1.bullets.push(new am4charts.LabelBullet())
    level1Bullet.locationY = 0.5
    level1Bullet.locationX = 0.5
    level1Bullet.label.text = "{name} ({duration} ms, count={count})"
    level1Bullet.label.fill = am4core.color("#fff")
  }

  render(data: DataManager): void {
    const icons = data.data.icons
    if (icons == null) {
      this.chart.data = []
      return
    }

    const items: Array<any> = []
    for (const [key, value] of Object.entries(icons)) {
      // @ts-ignore
      const info = value as IconData
      items.push({
        name: key,
        duration: info.loading,
        ...info,
        children: [
          {name: "searching", duration: info.loading - info.decoding},
          {name: "decoding", duration: info.decoding},
        ],
      })
    }

    this.chart.data = items
  }

  dispose(): void {
    this.chart.dispose()
  }
}