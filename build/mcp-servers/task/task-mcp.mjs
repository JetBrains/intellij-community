#!/usr/bin/env node
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {createMcpServer} from '../shared/mcp-rpc.mjs'
import {tools, toolHandlers} from './task-core.mjs'

createMcpServer({
  serverInfo: {name: 'task', version: '3.0.0'},
  tools,
  toolHandlers
})
