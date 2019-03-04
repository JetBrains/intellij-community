// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export interface Item {
  name: string
  description?: string

  start: number
  end: number

  duration: number
}

export interface InputData {
  version: string

  items: Array<Item>

  appComponents?: Array<Item>
  projectComponents?: Array<Item>

  appServices?: Array<Item>
  projectServices?: Array<Item>

  preloadActivities?: Array<Item>
  appOptionsTopHitProviders?: Array<Item>
  projectOptionsTopHitProviders?: Array<Item>

  totalDurationComputed: number
  totalDurationActual: number
}
