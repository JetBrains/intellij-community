// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export interface Item {
  name: string

  start: number
  end: number

  duration: number

  // added data
  shortName: string
  // relativeStart: number
}

export interface InputData {
  items: Array<Item>
  components?: Array<Item>
}

export interface ChartManager {
  render(data: InputData): void
}