// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Item} from "@/state/data"
import {ChartManager} from "./ChartManager"

export interface ActivityChartDescriptor {
  readonly label: string
  readonly id: string

  readonly isInfoChart?: boolean

  readonly sourceNames?: Array<string>

  readonly rotatedLabels?: boolean
  readonly groupByThread?: boolean
  readonly sourceHasPluginInformation?: boolean

  readonly chartManagerProducer?: (container: HTMLElement, sourceNames: Array<string>, descriptor: ActivityChartDescriptor) => Promise<ChartManager>
  readonly shortNameProducer?: (item: Item) => string
}

export function getShortName(item: Item): string {
  const lastDotIndex = item.name.lastIndexOf(".")
  return lastDotIndex < 0 ? item.name : item.name.substring(lastDotIndex + 1)
}

// not as part of ItemChartManager.ts to reduce scope of changes on change
// (make sure that hot reloading will not reload all modules where `chartDescriptors` is used - especially `router`)
export const chartDescriptors: Array<ActivityChartDescriptor> = [
  {
    label: "Components",
    id: "components",
    sourceNames: ["appComponents", "projectComponents", "moduleComponents"],
    shortNameProducer: getShortName,
    chartManagerProducer: async (container, sourceNames, descriptor) => new (await import(/* webpackMode: "eager" */ "./ComponentChartManager")).ComponentChartManager(container, sourceNames!!, descriptor)
  },
  {
    label: "Services",
    id: "services",
    sourceNames: ["appServices", "projectServices", "moduleServices"],
    shortNameProducer: getShortName,
  },
  {
    label: "Extensions",
    id: "extensions",
    sourceNames: ["appExtensions", "projectExtensions", "moduleExtensions"],
    shortNameProducer: getShortName,
  },
  {
    label: "Prepare App Init",
    id: "prepareAppInitActivities",
    groupByThread: true,
    sourceHasPluginInformation: false,
  },
  {
    label: "Options Top Hit Providers",
    id: "topHitProviders",
    sourceNames: ["appOptionsTopHitProviders", "projectOptionsTopHitProviders"],
    shortNameProducer: getShortName,
  },
  {
    label: "Project Post-Startup",
    id: "projectPostStartupActivities",
    shortNameProducer: getShortName,
  },
  {
    label: "Reopening Editors",
    id: "reopeningEditors",
    sourceHasPluginInformation: false,
  },
  {
    label: "GCs",
    id: "GCs",
    rotatedLabels: false,
  },
  {
    label: "Time Distribution",
    isInfoChart: true,
    id: "timeDistribution",
    sourceNames: [],
    chartManagerProducer: async (container, _sourceNames, _descriptor) => new (await import(/* webpackMode: "eager" */ "./TreeMapChartManager")).TreeMapChartManager(container),
  },
  {
    label: "Plugin Classes",
    isInfoChart: true,
    id: "pluginClassCount",
    sourceNames: [],
    chartManagerProducer: async (container, _sourceNames, _descriptor) => new (await import(/* webpackMode: "eager" */ "./PluginClassCountTreeMapChartManager")).PluginClassCountTreeMapChartManager(container),
  },
]
