// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {BaseChartManager, configureCursor} from "@/charts/ChartManager"
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"

export abstract class BaseTreeMapChartManager extends BaseChartManager<am4charts.TreeMap> {
  protected constructor(container: HTMLElement) {
    super(am4core.create(container, am4charts.TreeMap))

    configureCursor(this.chart)
  }

  protected enableZoom() {
    const chart = this.chart
    chart.mouseWheelBehavior = "zoomX"
    chart.scrollbarX = new am4core.Scrollbar()
    chart.mouseWheelBehavior = "zoomXY"
    // cursor tooltip is distracting
    chart.xAxis.cursorTooltipEnabled = false
    chart.yAxis.cursorTooltipEnabled = false
  }

  protected configureLabelBullet(bullet: am4charts.LabelBullet) {
    bullet.locationY = 0.5
    bullet.locationX = 0.5
    bullet.label.fill = am4core.color("#fff")
  }
}