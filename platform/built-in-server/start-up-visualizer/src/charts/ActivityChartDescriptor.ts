// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

export type ActivityChartType = "prepareAppInitActivity" | "components" | "services" | "extensions" | "topHitProviders"

export interface ActivityChartDescriptor {
  readonly label: string
  readonly name: ActivityChartType
}

// not as part of ItemChartManager.ts to reduce scope of changes on change
// (make sure that hot reloading will not reload all modules where `chartDescriptors` is used - especially `router`)
export const chartDescriptors: Array<ActivityChartDescriptor> = [
  {label: "Components", name: "components"},
  {label: "Services", name: "services"},
  {label: "Extensions", name: "extensions"},
  {label: "Options Top Hit Providers", name: "topHitProviders"},
  {label: "Prepare App Init", name: "prepareAppInitActivity"},
]
