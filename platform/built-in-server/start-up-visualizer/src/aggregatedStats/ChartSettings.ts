// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export class ChartSettings {
  serverUrl: string = "http://127.0.0.1:9044"

  selectedProduct: string = ""
  selectedMachine: string = ""

  showScrollbarXPreview: boolean = false
}

export interface InfoResponse {
  readonly productNames: Array<string>
  readonly productToMachineNames: { [key: string]: Array<string>; }

  readonly durationMetricsNames: Array<string>
  readonly instantMetricsNames: Array<string>
}