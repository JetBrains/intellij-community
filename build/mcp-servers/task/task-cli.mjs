// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#!/usr/bin/env
node

/* global process */
import {toolHandlers, tools} from './task-core.mjs'

function printUsage() {
  const toolList = tools.map(tool => tool.name).sort().join(', ')
  console.error('Usage:')
  console.error('  node community/build/mcp-servers/task/task-cli.mjs tools')
  console.error('  node community/build/mcp-servers/task/task-cli.mjs call <tool> <json>')
  console.error('')
  console.error('Examples:')
  console.error('  node community/build/mcp-servers/task/task-cli.mjs tools')
  console.error('  node community/build/mcp-servers/task/task-cli.mjs call task_status "{}"')
  console.error('  node community/build/mcp-servers/task/task-cli.mjs call task_status "{\"id\":\"idea-2-xyz\"}"')
  console.error('')
  console.error(`Available tools: ${toolList}`)
}

function parseArgs(rawArgs) {
  if (!rawArgs || rawArgs.length === 0) {
    return {}
  }
  const joined = rawArgs.join(' ').trim()
  if (!joined) {
    return {}
  }
  try {
    const parsed = JSON.parse(joined)
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('arguments must be a JSON object')
    }
    return parsed
  } catch (error) {
    throw new Error(`Invalid JSON arguments: ${error.message}`)
  }
}

async function main() {
  const [command, toolName, ...rest] = process['argv'].slice(2)

  if (!command) {
    printUsage()
    process.exit(1)
  }

  if (command === 'tools') {
    console.log(JSON.stringify(tools, null, 2))
    return
  }

  if (command === 'call') {
    if (!toolName) {
      console.error('Missing tool name.')
      printUsage()
      process.exit(1)
    }

    const handler = toolHandlers[toolName]
    if (!handler) {
      console.error(`Unknown tool: ${toolName}`)
      printUsage()
      process.exit(1)
    }

    const args = parseArgs(rest)
    const result = await handler(args, {})
    console.log(JSON.stringify(result, null, 2))
    return
  }

  console.error(`Unknown command: ${command}`)
  printUsage()
  process.exit(1)
}

main().catch(error => {
  console.error(error.message || String(error))
  process.exit(1)
})
