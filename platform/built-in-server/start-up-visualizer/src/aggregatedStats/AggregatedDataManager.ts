// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

const hiddenMetricsByDefault = new Set(["moduleLoading"])

export interface MetricDescriptor {
  readonly key: string
  readonly name: string
  readonly hiddenByDefault: boolean
}

export class AggregatedDataManager {
  readonly durationMetricsNames: Array<MetricDescriptor>
  readonly instantMetricsNames: Array<MetricDescriptor>

  get metrics() {
    // data for first machine
    return this.data[0].metrics
  }

  constructor(private readonly data: Array<MachineMetrics>) {
    const metrics = data[0].metrics

    this.durationMetricsNames = []
    this.instantMetricsNames = []

    // do not sort, use as is
    for (const key of getSortedMetricNames(metrics[0])) {
      if (key === "t") {
        continue
      }

      const hiddenByDefault = hiddenMetricsByDefault.has(key)
      if (key.startsWith("i_")) {
        this.instantMetricsNames.push({
          key,
          name: key.substring(2, key.length),
          hiddenByDefault,
        })
      }
      else {
        this.durationMetricsNames.push({
          key,
          name: key,
          hiddenByDefault,
        })
      }
    }

    // amcharts doesn't like empty or -1 values, so, filter it out
    // must be after we compute set of existing metrics
    for (const nameToValue of metrics) {
      for (const name of Object.keys(nameToValue)) {
        if (nameToValue[name] <= 0) {
          nameToValue[name] = undefined as any
        }
      }
    }
  }
}

const metricNameOrder = new Map<string, number>()

function initMetricNameOrder() {
  let order = 0
  metricNameOrder.set("bootstrap", order++)
  metricNameOrder.set("appInitPreparation", order++)
  metricNameOrder.set("appInitPreparation", order++)
  metricNameOrder.set("appInit", order++)
  metricNameOrder.set("pluginDescriptorLoading", order++)
  metricNameOrder.set("projectComponentCreation", order++)
  metricNameOrder.set("appComponentCreation", order++)
  metricNameOrder.set("moduleLoading", order++)
}

initMetricNameOrder()

function getSortedMetricNames(metrics: any): Array<string> {
  const result = Object.keys(metrics)
  result.sort((a, b) => getWeight(a) - getWeight(b))
  return result
}

function getWeight(s: string) {
  let result = metricNameOrder.get(s)
  return result === undefined ? metricNameOrder.size : result
}
