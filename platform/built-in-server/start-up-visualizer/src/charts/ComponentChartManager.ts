// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {DataManager} from "@/state/DataManager"
import {ActivityChartManager} from "@/charts/ActivityChartManager"
import {ActivityChartDescriptor} from "@/charts/ActivityChartDescriptor"

export class ComponentChartManager extends ActivityChartManager {
  constructor(container: HTMLElement, sourceNames: Array<string>, descriptor: ActivityChartDescriptor) {
    super(container, sourceNames, descriptor)
  }

  // doesn't make sense for components - cannot be outside of ready, and app initialized is clear
  // because color for app/project bars is different
  protected computeRangeMarkers(_data: DataManager) {
  }
}