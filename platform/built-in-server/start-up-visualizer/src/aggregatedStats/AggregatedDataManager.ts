// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Metrics} from "@/aggregatedStats/model"
import {InfoResponse} from "@/aggregatedStats/ChartSettings"

const hiddenMetricsByDefault = new Set(["moduleLoading", "pluginDescriptorLoading"])

export interface MetricDescriptor {
  readonly key: string
  readonly name: string
  readonly hiddenByDefault: boolean
}

export class AggregatedDataManager {
  readonly durationMetricDescriptors: Array<MetricDescriptor>
  readonly instantMetricDescriptors: Array<MetricDescriptor>

  constructor(readonly metrics: Array<Metrics>, infoResponse: InfoResponse) {
    this.durationMetricDescriptors = []
    this.instantMetricDescriptors = []

    for (const key of infoResponse.durationMetricsNames) {
      const hiddenByDefault = hiddenMetricsByDefault.has(key)
      this.durationMetricDescriptors.push({
        key,
        name: key,
        hiddenByDefault,
      })
    }

    for (const key of infoResponse.instantMetricsNames) {
      this.instantMetricDescriptors.push({
        key,
        name: key,
        hiddenByDefault: false,
      })
    }

    // amcharts doesn't like empty or -1 values, so, filter it out
    for (const nameToValue of metrics) {
      for (const name of Object.keys(nameToValue)) {
        if (nameToValue[name] <= 0) {
          nameToValue[name] = undefined as any
        }
      }
    }
  }
}