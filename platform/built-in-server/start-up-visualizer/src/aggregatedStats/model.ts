// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export type Metrics = { [key: string]: number; }

export interface InfoResponse {
  readonly productNames: Array<string>
  readonly productToMachine: { [key: string]: Array<MachineGroup>; }

  readonly durationMetricNames: Array<string>
  readonly instantMetricNames: Array<string>
}

export interface MachineGroup {
  name: string
  children: Array<Machine>
}

export interface Machine {
  readonly name: string
}

export interface GroupedMetricResponse {
  readonly groupNames: Array<string>
  readonly data: Array<{ [key: string]: Array<string | number>; }>
}