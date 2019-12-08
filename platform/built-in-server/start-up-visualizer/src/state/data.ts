// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export interface Item extends CommonItem {
  readonly description?: string

  readonly start: number
  readonly end: number

  readonly duration: number

  readonly thread: string
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
  readonly appComponents?: Array<Item>
  readonly projectComponents?: Array<Item>
  readonly moduleComponents?: Array<Item>

  readonly appServices?: Array<Item>
  readonly projectServices?: Array<Item>
  readonly moduleServices?: Array<Item>
}

export interface InputData {
  readonly traceEvents: Array<TraceEvent>

  readonly version: string

  readonly stats: Stats

  readonly icons?: Array<{ [key: string]: IconData; }>

  readonly items: Array<Item>

  readonly prepareAppInitActivities: Array<Item>

  readonly appExtensions?: Array<Item>
  readonly projectExtensions?: Array<Item>
  readonly moduleExtensions?: Array<Item>

  readonly preloadActivities?: Array<Item>
  readonly appOptionsTopHitProviders?: Array<Item>
  readonly projectOptionsTopHitProviders?: Array<Item>

  readonly projectPostStartupActivities?: Array<Item>

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