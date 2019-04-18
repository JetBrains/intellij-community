// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export type ActivityChartType = "prepareAppInitActivities" | "components" | "services" | "extensions" | "topHitProviders" | "projectPostStartupActivities"

export interface ActivityChartDescriptor {
  readonly label: string
  readonly id: ActivityChartType

  readonly sourceNames?: Array<string>
}

// not as part of ItemChartManager.ts to reduce scope of changes on change
// (make sure that hot reloading will not reload all modules where `chartDescriptors` is used - especially `router`)
export const chartDescriptors: Array<ActivityChartDescriptor> = [
  {
    label: "Components",
    id: "components",
    sourceNames: ["appComponents", "projectComponents", "moduleComponents"],
  },
  {
    label: "Services",
    id: "services",
    sourceNames: ["appServices", "projectServices", "moduleServices"],
  },
  {
    label: "Extensions",
    id: "extensions",
    sourceNames: ["appExtensions", "projectExtensions", "moduleExtensions"],
  },
  {
    label: "Options Top Hit Providers",
    id: "topHitProviders",
    sourceNames: ["appOptionsTopHitProviders", "projectOptionsTopHitProviders"],
  },
  {
    label: "Prepare App Init",
    id: "prepareAppInitActivities",
  },
  {
    label: "Project Post-Startup",
    id: "projectPostStartupActivities",
  },
]
