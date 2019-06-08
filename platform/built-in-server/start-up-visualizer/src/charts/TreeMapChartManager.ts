// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import {DataManager} from "@/state/DataManager"
import {IconData, Item} from "@/state/data"
import {getShortName} from "@/charts/ActivityChartDescriptor"
import {BaseTreeMapChartManager} from "@/charts/BaseTreeMapChartManager"

export class TreeMapChartManager extends BaseTreeMapChartManager {
  constructor(container: HTMLElement) {
    super(container)

    // enableZoom is not called because for this chart it doesn't work correctly and not really required

    const chart = this.chart
    chart.dataFields.value = "duration"
    chart.dataFields.name = "name"
    chart.dataFields.children = "children"

    chart.legend = new am4charts.Legend()

    chart.homeText = "home"
    chart.navigationBar = new am4charts.NavigationBar()
    chart.maxLevels = 1

    const level1 = chart.seriesTemplates.create("0")
    const level1Bullet = level1.bullets.push(new am4charts.LabelBullet())
    this.configureLabelBullet(level1Bullet)
    level1Bullet.label.text = "{name} ({duration} ms, count={count})"

    const level2 = chart.seriesTemplates.create("1")
    const level2Bullet = level2.bullets.push(new am4charts.LabelBullet())
    this.configureLabelBullet(level2Bullet)
    level2Bullet.label.text = "{name}"

    chart.seriesTemplates.create("2").bullets.push(level2Bullet)
    chart.seriesTemplates.create("3").bullets.push(level2Bullet)
  }

  render(data: DataManager): void {
    const items: Array<any> = []

    this.addServicesOrComponents(data, items, "component", "appComponents", "projectComponents")
    this.addServicesOrComponents(data, items, "service", "appServices", "projectServices")
    this.addIcons(data, items)

    this.chart.data = items
  }

  private addServicesOrComponents(data: DataManager, items: Array<any>, statName: "component" | "service", appFieldName: "appServices" | "appComponents", projectFieldName: "projectComponents" | "projectServices") {
    const components: Array<any> = []
    components.push({
      name: "app",
      children: toTreeMapItem(data.data[appFieldName]),
    })
    components.push({
      name: "project",
      children: toTreeMapItem(data.data[projectFieldName]),
    })

    let duration = 0
    const durationComputer = (it: Item) => duration += it.duration
    const v = data.data[appFieldName]
    if (v != null) {
      v.forEach(durationComputer)
    }

    const p = data.data[projectFieldName]
    if (p != null) {
      p.forEach(durationComputer)
    }

    const stats = data.data.stats[statName]
    items.push({
      name: statName + "s",
      children: components,
      count: stats.app + stats.project + stats.module,
      duration,
    })
  }

  private addIcons(data: DataManager, items: Array<any>) {
    const icons = data.data.icons
    if (icons != null) {
      const iconList: Array<any> = []

      let count = 0
      let duration = 0
      for (const [key, value] of Object.entries(icons)) {
        // @ts-ignore
        const info = value as IconData
        count += info.count
        duration += info.loading
        iconList.push({
          name: key,
          duration: info.loading,
          ...info,
          children: [
            {name: "searching", duration: info.loading - info.decoding},
            {name: "decoding", duration: info.decoding},
          ],
        })
      }

      items.push({
        name: "icons",
        children: iconList,
        count,
        duration,
      })
    }
  }
}

function toTreeMapItem(items: Array<Item> | null | undefined) {
  return items == null ? [] : items.map(it => {
    return {...it, name: getShortName(it)}
  })
}