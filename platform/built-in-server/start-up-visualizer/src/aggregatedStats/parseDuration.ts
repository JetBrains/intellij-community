// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
const duration = /(-?\d*\.?\d+(?:e[-+]?\d+)?)\s*([a-zμ]*)/ig

export interface TimeRangeModel {
  l: string
  k: string
}

export const timeRanges: Array<TimeRangeModel> = [{l: "All", k: "1y"}, {l: "Last 3 months", k: "3M"}, {l: "Last month", k: "1M"}]

const units: Array<UnitDescriptor> = [
  {
    subtractFunction: "subtractDays",
    apply(value: number, result: DurationParseResult): void {
      result.days = value
    },
    getValue: result => result.days,
  },
  {
    subtractFunction: "subtractWeeks",
    apply(value: number, result: DurationParseResult): void {
      result.weeks = value
    },
    getValue: result => result.weeks,
  },
  {
    subtractFunction: "subtractMonths",
    apply(value: number, result: DurationParseResult): void {
      result.months = value
    },
    getValue: result => result.months,
  },
  {
    subtractFunction: "subtractYears",
    apply(value: number, result: DurationParseResult): void {
      result.years = value
    },
    getValue: result => result.years,
  },
]

const unitToDescriptor: { [key: string]: UnitDescriptor; } = {
  "d": units[0],
  "w": units[1],
  "M": units[2],
  "month": units[2],
  "y": units[3],
}

interface UnitDescriptor {
  readonly subtractFunction: string

  apply(value: number, result: DurationParseResult): void

  getValue(result: DurationParseResult): number | undefined
}

export interface DurationParseResult {
  days?: number
  weeks?: number
  months?: number
  years?: number
}

export function toClickhouseSql(duration: DurationParseResult): string {
  let result = ""
  for (const unit of units) {
    const value = unit.getValue(duration)
    if (value == undefined) {
      continue
    }

    let expression = `${unit.subtractFunction}(`
    if (result.length === 0) {
      expression += "now()"
    }
    else {
      expression += result
    }
    expression += `, ${value})`
    result = expression
  }
  return result
}

export function parseTimeRange(s: string | null): DurationParseResult {
  let timeRange = s
  if (timeRange == null || timeRange == "all") {
    timeRange = "1y"
  }
  else if (timeRange === "lastMonth") {
    timeRange = "1M"
  }
  return parseDuration(timeRange)
}

export function parseDuration(s: string): DurationParseResult {
  const result: DurationParseResult = {}
  // ignore commas
  s = s.replace(/(\d),(\d)/g, '$1$2')
  s.replace(duration, (_, ...args: any[]) => {
    const n = args[0]
    const unit = args[1]
    const unitDescriptor = unitToDescriptor[unit]
      || unitToDescriptor[unit.replace(/s$/, "")]
      || null
    if (unitDescriptor == null) {
      console.error(`unknown unit: ${unit}`)
    }
    else {
      unitDescriptor.apply(parseInt(n, 10), result)
    }
    return ""
  })
  return result
}