// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export const DEFAULT_AGGREGATION_OPERATOR = "median"

export class ChartSettings {
  serverUrl: string = "https://ij-perf.labs.jb.gg"

  selectedProduct: string = ""
  selectedMachine: number | null = null

  aggregationOperator: string = DEFAULT_AGGREGATION_OPERATOR

  showScrollbarXPreview: boolean = false
}