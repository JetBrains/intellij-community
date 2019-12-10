// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {InfoResponse, Metrics} from "@/aggregatedStats/model"

const hiddenMetricsByDefault = new Set(["moduleLoading", "pluginDescriptorLoading"])

export interface MetricDescriptor {
  readonly key: string
  readonly name: string
  readonly hiddenByDefault: boolean
}

export class LineChartDataManager {
  readonly metricDescriptors: Array<MetricDescriptor>

  constructor(readonly metrics: Array<Metrics>, infoResponse: InfoResponse, isInstant: boolean, readonly reportUrlPrefix: string) {
    this.metricDescriptors = []

    if (isInstant) {
      for (const key of infoResponse.instantMetricNames) {
        this.metricDescriptors.push({
          key,
          name: key,
          hiddenByDefault: false,
        })
      }
    }
    else {
      for (const key of infoResponse.durationMetricNames) {
        const hiddenByDefault = hiddenMetricsByDefault.has(key)
        this.metricDescriptors.push({
          key,
          name: key,
          hiddenByDefault,
        })
      }
    }
  }
}