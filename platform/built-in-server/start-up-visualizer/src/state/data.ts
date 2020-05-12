// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export interface ItemV0 extends CommonItem {
  readonly name: string
  readonly description?: string

  readonly start: number
  readonly end: number

  readonly duration: number

  readonly thread: string
}

export interface ItemV20 {
  readonly s: number
  readonly d: number
  // own duration is specified only if differs from duration
  readonly od?: number
  readonly n: string
  readonly t: string
  readonly p: string
}

export interface CommonItem {
  readonly name: string
}

export interface TraceEvent extends CommonItem {
  readonly name: string
  readonly ph: "i" | "X"
  // timestamp in microseconds
  readonly ts: number

  readonly tid: string

  readonly cat?: string
  readonly args?: TraceEventArgs
}

export interface TraceEventArgs {
  // our extension for services
  readonly ownDur: number
  readonly plugin?: string
}

export interface CompleteTraceEvent extends TraceEvent {
  // duration in microseconds
  readonly dur: number
}

export interface InputDataV11AndLess extends InputData {
  readonly appComponents?: Array<ItemV0>
  readonly projectComponents?: Array<ItemV0>
  readonly moduleComponents?: Array<ItemV0>

  readonly appServices?: Array<ItemV0>
  readonly projectServices?: Array<ItemV0>
  readonly moduleServices?: Array<ItemV0>
}

export interface InputDataV20 extends InputData {
  readonly appComponents?: Array<ItemV20>
  readonly projectComponents?: Array<ItemV20>
  readonly moduleComponents?: Array<ItemV20>

  readonly appServices?: Array<ItemV20>
  readonly projectServices?: Array<ItemV20>
  readonly moduleServices?: Array<ItemV20>

  readonly serviceWaiting?: Array<ItemV20>
}

export interface InputData {
  readonly traceEvents: Array<TraceEvent>

  readonly version: string

  readonly stats: Stats

  readonly icons?: Array<{ [key: string]: IconData; }>

  readonly items: Array<ItemV0>

  readonly prepareAppInitActivities: Array<ItemV0>

  readonly appExtensions?: Array<ItemV0>
  readonly projectExtensions?: Array<ItemV0>
  readonly moduleExtensions?: Array<ItemV0>

  readonly preloadActivities?: Array<ItemV0>
  readonly appOptionsTopHitProviders?: Array<ItemV0>
  readonly projectOptionsTopHitProviders?: Array<ItemV0>

  readonly projectPostStartupActivities?: Array<ItemV0>

  readonly totalDurationComputed: number
  readonly totalDurationActual: number
}

export interface Stats {
  readonly plugin: number

  readonly component: StatItem
  readonly service: StatItem

  readonly loadedClasses: { [key: string]: number; }
}

export interface StatItem {
  readonly app: number
  readonly project: number
  readonly module: number
}

export interface IconData {
  readonly count: number

  readonly loading: number
  readonly decoding: number
}