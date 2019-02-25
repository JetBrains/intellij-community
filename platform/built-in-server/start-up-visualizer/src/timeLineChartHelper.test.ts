// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {computeLevels, TimeLineItem} from "./timeLineChartHelper"

test("sort", () => {
  const items = [
    {
      "name": "default project components creation",
      "duration": 13,
      "start": 1550854187320,
      "end": 1550854187333
    },
    {
      "name": "default project components registration",
      "isSubItem": true,
      "duration": 0,
      "start": 1550854187320,
      "end": 1550854187320
    },
    {
      "name": "default project components initialization",
      "description": "component count: 24",
      "isSubItem": true,
      "duration": 13,
      "start": 1550854187320,
      "end": 1550854187333
    },
  ]
  computeLevels(items)
  expect(items.map(it => {
    return {name: it.name, level: (it as TimeLineItem).level}
  })).toMatchObject([
    {
      "name": "default project components creation",
      "level": 0,
    },
    {
      "name": "default project components registration",
      "level": 1,
    },
    {
      "name": "default project components initialization",
      "level": 1,
    },
  ])
})