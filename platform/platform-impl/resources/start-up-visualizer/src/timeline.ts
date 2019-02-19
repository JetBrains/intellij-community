// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import vis = require("vis")
import {ChartManager, InputData, Item} from "./core"

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// https://github.com/almende/vis/blob/master/examples/timeline/dataHandling/dataSerialization.html
// do not group because it makes hard to understand results
// (executed sequentially, so, we need to see it sequentially from left to right)
const isCreateGroups = false
const groups = isCreateGroups ? [
  {id: "application components"},
  {id: "project components"},
] : null

const timelineOptions: any = {
  // http://visjs.org/examples/timeline/items/itemOrdering.html
  // strange, but order allows to have consistent ordering but still some items not stacked correctly (e.g. "project components initialization between registration and creation even if indices are correct)
  order: (o1: any, o2: any) => {
    // if (o1.startRaw <= o2.startRaw && o1.endRaw >= o2.endRaw) {
    //   return -1
    // }
    return o1.rawIndex - o2.rawIndex
  },
}

export class TimelineChartManager implements ChartManager {
  private readonly dataSet = new vis.DataSet()
  private readonly timeline: vis.Timeline

  private lastData: InputData | null = null

  constructor(container: HTMLElement) {
    this.timeline = new vis.Timeline(container, this.dataSet, timelineOptions)

    document.getElementById("isUseRealTime")!!.addEventListener("click", () => {
      if (this.lastData != null) {
        this.render(this.lastData)
      }
    })
  }

  render(ijData: InputData) {
    this.lastData = ijData

    const data = transformIjToVisJsFormat(ijData, isCreateGroups)
    const dataSet = this.dataSet
    dataSet.clear()
    const items = data.items
    if (data.groups != null) {
      this.timeline.setGroups(data.groups)
    }
    dataSet.add(items)

    timelineOptions.min = items[0].start
    timelineOptions.max = items[items.length - 1].end + 1000 /* add 1 second as padding */
    this.timeline.setOptions(timelineOptions)
    this.timeline.fit()
  }
}

function computeTitle(item: any, _index: number) {
  let result = item.name + (item.description == null ? "" : `<br/>${item.description}`) + `<br/>${item.duration} ms`
  // debug
  // result += `<br/>${index}`
  return result
}

function transformIjToVisJsFormat(input: InputData, isCreateGroups: boolean) {
  const isUseRealTime = (document.getElementById("isUseRealTime") as HTMLInputElement).checked

  const moment: any = (vis as any).moment

  // noinspection ES6ModulesDependencies
  const firstStart = moment(input.items[0].start)
  // hack to force timeline to start from 0
  const timeOffset = isUseRealTime ? 0 : (firstStart.seconds() * 1000) + firstStart.milliseconds()
  const transformer = (item: Item, index: number) => {
    // noinspection ES6ModulesDependencies
    const vItem: any = {
      id: item.name,
      title: computeTitle(item, index),
      content: item.name,
      start: moment(item.start - timeOffset),
      // startRaw: item.start,
      end: moment(item.end - timeOffset),
      // endRaw: item.end,
      type: "range",
      rawIndex: index,
    }

    if (isCreateGroups) {
      for (const group of groups!!) {
        if (item.name.startsWith(group.id)) {
          vItem.group = group.id
          break
        }
      }
    }
    return vItem
  }
  const items = input.items.map(transformer)
  // a lot of components - makes timeline not readable
  // if (input.components != null) {
  //   items = items.concat(input.components.map(transformer))
  // }

  return {
    groups: groups,
    items,
  }
}