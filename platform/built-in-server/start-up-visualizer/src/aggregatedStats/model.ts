// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export type Metrics = { [key: string]: number; }

export interface InfoResponse {
  readonly productNames: Array<string>
  readonly productToMachine: { [key: string]: Array<MachineGroup>; }
  readonly productToProjects: { [key: string]: Array<string>; }
}

export interface MachineGroup {
  name: string
  children: Array<Machine>
}

export interface Machine {
  readonly name: string
}

export interface GroupedMetricResponse {
  groupNames: Array<string>
  readonly data: Array<{ [key: string]: Array<string | number>; }>
}

export interface DataQuery {
  db: string

  fields?: Array<string | DataQueryDimension>

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
  value?: number | string | Array<string>
  sql?: string
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
  db: string
  product: string
  project: string
  machine: Array<string>
}

export function getFilters(request: DataRequest): Array<DataQueryFilter> {
  const result: Array<DataQueryFilter> = []
  if (request.db == "ij") {
    result.push({field: "product", value: request.product})
  }
  result.push({field: "project", value: request.project})
  result.push({field: "machine", value: request.machine})
  return result
}

const rison: { encode: (o: any) => string } = require("rison-node")

export function encodeQuery(query: DataQuery): string {
  return rison.encode(query)
}

export function expandMachineAsFilterValue(product: string, machine: Array<string>, infoResponse: InfoResponse): Array<string> {
  if (machine.length > 1) {
    return machine
  }

  const groupName = machine[0]
  for (const machineGroup of infoResponse.productToMachine[product]) {
    if (machineGroup.name === groupName) {
      return machineGroup.children.map(it => it.name)
    }
  }
  return [groupName]
}