// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export interface Item {
  readonly name: string
  readonly description?: string

  readonly start: number
  readonly end: number

  readonly duration: number

  readonly thread: string
}

export interface InputData {
  readonly version: string

  readonly stats: Stats

  readonly icons?: Array<{ [key: string]: IconData; }>

  readonly items: Array<Item>

  readonly prepareAppInitActivities: Array<Item>

  readonly appComponents?: Array<Item>
  readonly projectComponents?: Array<Item>
  readonly moduleComponents?: Array<Item>

  readonly appServices?: Array<Item>
  readonly projectServices?: Array<Item>
  readonly moduleServices?: Array<Item>

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