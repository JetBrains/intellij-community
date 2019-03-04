// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

export type ItemChartType = "components" | "services" | "topHitProviders"

export interface ItemChartDescriptor {
  readonly label: string
  readonly name: ItemChartType
}

// not as part of ItemChartManager.ts to reduce scope of changes on change
// (makes sure that hot reloading to not reload all modules where `chartDescriptors` is used - especially `router`)
export const chartDescriptors: Array<ItemChartDescriptor> = [
  {label: "Components", name: "components"},
  {label: "Services", name: "services"},
  {label: "Options Top Hit Providers", name: "topHitProviders"},
]
