// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import {DataManager} from "@/state/DataManager"
import {BaseTreeMapChartManager} from "@/charts/BaseTreeMapChartManager"

export class PluginClassCountTreeMapChartManager extends BaseTreeMapChartManager {
  constructor(container: HTMLElement) {
    super(container)

    const chart = this.chart
    chart.dataFields.value = "count"
    chart.dataFields.name = "name"

    this.enableZoom()

    const level1 = chart.seriesTemplates.create("0")
    const level1Bullet = level1.bullets.push(new am4charts.LabelBullet())
    this.configureLabelBullet(level1Bullet)
    level1Bullet.label.text = "{abbreviatedName} ({count})"
  }

  render(data: DataManager): void {
    const items: Array<any> = []

    const loadedClasses = data.data.stats.loadedClasses
    if (loadedClasses != null) {
      for (const name of Object.keys(loadedClasses)) {
        items.push({
          name,
          abbreviatedName: getAbbreviatedName(name),
          count: loadedClasses[name],
        })
      }
    }

    this.chart.data = items
  }
}

function getAbbreviatedName(name: string): string {
  if (!name.includes(".")) {
    return name
  }

  let abbreviatedName = ""
  const names = name.split(".")
  for (let i = 0; i < names.length; i++) {
    const unqualifiedName = names[i]
    if (i == (names.length - 1)) {
      abbreviatedName += unqualifiedName
    } else {
      abbreviatedName += unqualifiedName.substring(0, 1) + "."
    }
  }
  return abbreviatedName
}