// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export class AggregatedDataManager {
  readonly metricsNames: Array<string>

  get metrics() {
    // data for first machine
    return this.data[0].metrics
  }

  constructor(private readonly data: Array<MachineMetrics>) {
    const metrics = data[0].metrics

    // do not sort, use as is
    this.metricsNames = Object.keys(metrics[0])
  }
}