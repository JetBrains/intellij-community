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

export interface DataQuery {
  fields: Array<string | DataQueryDimension>

  filters?: Array<DataQueryFilter>
  order?: Array<string>

  // used only for grouped query
  aggregator?: string
  // cube.js term (group by)
  dimensions?: Array<DataQueryDimension>

  // used only for grouped query
  timeDimensionFormat?: string
}

export interface DataQueryFilter {
  field: string
  value: number | string | Array<string>
  operator?: ">"
}

export interface DataQueryDimension {
  name: string
  sql?: string
}

export interface MetricDescriptor {
  readonly key: string
  readonly name: string
  readonly hiddenByDefault: boolean
}

export interface DataRequest {
  product: string
  machine: Array<string>
  infoResponse: InfoResponse
}

export function expandMachine(request: DataRequest): string {
  if (request.machine.length > 1) {
    return request.machine.join(",")
  }

  const groupName = request.machine[0]
  const infoResponse = request.infoResponse
  for (const machineGroup of infoResponse.productToMachine[request.product]) {
    if (machineGroup.name === groupName) {
      return machineGroup.children.map(it => it.name).join(",")
    }
  }
  return groupName
}

export function expandMachineAsFilterValue(request: DataRequest): string | Array<string> {
  if (request.machine.length > 1) {
    return request.machine
  }

  const groupName = request.machine[0]
  const infoResponse = request.infoResponse
  for (const machineGroup of infoResponse.productToMachine[request.product]) {
    if (machineGroup.name === groupName) {
      return machineGroup.children.map(it => it.name)
    }
  }
  return groupName
}