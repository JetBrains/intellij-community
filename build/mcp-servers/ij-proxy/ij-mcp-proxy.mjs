#!/usr/bin/env node
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/// <reference types="node" />

import path from 'node:path'
import readline from 'node:readline'
import {cwd, env, exit, stdin, stdout} from 'node:process'
import {clearLogFile, logProgress, logToFile} from '../shared/mcp-rpc.mjs'
import {createProjectPathManager} from './project-path.mjs'
import {createStreamTransport, PROBE_MESSAGE_ID} from './stream-transport.mjs'
import {createProxyTooling, resolveToolMode, TOOL_MODES} from './proxy-tools/tooling.mjs'
import {extractTextFromResult} from './proxy-tools/shared.mjs'

// Proxy JetBrains MCP Streamable HTTP to stdio and inject the cwd as project_path.
const explicitMcpUrl = env.JETBRAINS_MCP_STREAM_URL
  || env.MCP_STREAM_URL
  || env.JETBRAINS_MCP_URL
  || env.MCP_URL
const defaultHost = '127.0.0.1'
const defaultPort = 64342
const defaultPath = '/stream'
const defaultScanLimit = 10
const portScanStartEnv = env.JETBRAINS_MCP_PORT_START
const portScanStart = parseEnvInt('JETBRAINS_MCP_PORT_START', defaultPort)
const portScanLimit = parseEnvInt('JETBRAINS_MCP_PORT_SCAN_LIMIT', defaultScanLimit)
const preferredPorts = portScanStartEnv ? [portScanStart] : [defaultPort, 64344]
const connectTimeoutMs = parseEnvSeconds('JETBRAINS_MCP_CONNECT_TIMEOUT_S', 10)
const scanTimeoutMs = parseEnvSeconds('JETBRAINS_MCP_SCAN_TIMEOUT_S', 1)
const queueLimit = parseEnvNonNegativeInt('JETBRAINS_MCP_QUEUE_LIMIT', 100)
const STREAM_RETRY_ATTEMPTS = 3
const STREAM_RETRY_BASE_DELAY_MS = 200

function parseEnvInt(name, fallback) {
  const raw = env[name]
  if (!raw) return fallback
  const parsed = Number.parseInt(raw, 10)
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback
  return parsed
}

function parseEnvNonNegativeInt(name, fallback) {
  const raw = env[name]
  if (raw === undefined || raw === null || raw === '') return fallback
  const parsed = Number.parseInt(raw, 10)
  if (!Number.isFinite(parsed) || parsed < 0) return fallback
  return parsed
}

function parseEnvSeconds(name, fallbackSeconds) {
  const seconds = parseEnvNonNegativeInt(name, fallbackSeconds)
  return seconds * 1000
}

function buildStreamUrl(port) {
  // noinspection HttpUrlsUsage
  return `http://${defaultHost}:${port}${defaultPath}`
}

const projectPath = path.resolve(cwd())
const defaultProjectPathKey = 'project_path'
const projectPathManager = createProjectPathManager({projectPath, defaultProjectPathKey})

const pendingMethods = new Map()
const pendingProxyCalls = new Map()
let proxyCallCounter = 0

const toolModeInfo = resolveToolMode(env.JETBRAINS_MCP_TOOL_MODE)

const BLOCKED_TOOL_NAMES = new Set(['create_new_file'])
const REPLACED_TOOL_NAMES = new Set([
  'get_file_text_by_path',
  'replace_text_in_file',
  'find_files_by_name_keyword',
  'find_files_by_glob',
  'search_in_files_by_regex',
  'search_in_files_by_text',
  'list_directory_tree',
  'execute_terminal_command'
])

function blockedToolMessage(toolName) {
  if (toolModeInfo.mode === TOOL_MODES.CC) {
    return `Tool '${toolName}' is not exposed by ij-proxy. Use 'write' instead.`
  }
  return `Tool '${toolName}' is not exposed by ij-proxy. Use 'apply_patch' instead.`
}

const {proxyToolSpecs, proxyToolNames, runProxyToolCall} = createProxyTooling({
  projectPath,
  callUpstreamTool,
  toolMode: toolModeInfo.mode
})

function note(message) {
  logToFile(message)
  logProgress(message)
}

function warn(message) {
  logToFile(message)
  logProgress(message)
}

void clearLogFile()

if (toolModeInfo.warning) {
  warn(toolModeInfo.warning)
}

const streamTransport = createStreamTransport({
  explicitUrl: explicitMcpUrl,
  preferredPorts,
  portScanStart,
  portScanLimit,
  connectTimeoutMs,
  scanTimeoutMs,
  queueLimit,
  retryAttempts: STREAM_RETRY_ATTEMPTS,
  retryBaseDelayMs: STREAM_RETRY_BASE_DELAY_MS,
  buildUrl: buildStreamUrl,
  note,
  warn,
  exit,
  onMessage: handleUpstreamMessage
})

function queueLimitMessage() {
  return `MCP proxy queue limit (${queueLimit}) reached before stream connection`
}

function formatSendError(sendResult) {
  if (sendResult?.reason === 'queue_limit') return queueLimitMessage()
  if (sendResult?.error) return sendResult.error
  return 'Failed to send MCP message'
}

const outputQueue = []
let outputQueueStart = 0
let outputDrainPending = false

function mergeToolLists(proxyTools, upstreamTools, blockedNames) {
  const blocked = new Set(blockedNames || [])
  const result = []
  const seen = new Set()

  for (const tool of proxyTools || []) {
    if (!tool || typeof tool.name !== 'string') continue
    if (seen.has(tool.name)) continue
    seen.add(tool.name)
    result.push(tool)
  }

  if (Array.isArray(upstreamTools)) {
    for (const tool of upstreamTools) {
      const name = tool?.name
      if (typeof name !== 'string' || !name) continue
      if (blocked.has(name)) continue
      if (seen.has(name)) continue
      seen.add(name)
      result.push(tool)
    }
  }

  return result
}

function outputMessage(message) {
  outputQueue.push(`${JSON.stringify(message)}\n`)
  flushOutputQueue()
}

function flushOutputQueue() {
  if (outputDrainPending) return

  while (outputQueueStart < outputQueue.length) {
    const chunk = outputQueue[outputQueueStart]
    if (!stdout.write(chunk)) {
      outputDrainPending = true
      stdout.once('drain', () => {
        outputDrainPending = false
        flushOutputQueue()
      })
      return
    }
    outputQueueStart += 1
    if (outputQueueStart > 2048 && outputQueueStart * 2 > outputQueue.length) {
      outputQueue.splice(0, outputQueueStart)
      outputQueueStart = 0
    }
  }

  if (outputQueueStart > 0) {
    outputQueue.length = 0
    outputQueueStart = 0
  }
}

function makeToolResponse(id, text, isError) {
  const message = {
    jsonrpc: '2.0',
    id,
    result: {
      content: [
        {
          type: 'text',
          text: String(text)
        }
      ]
    }
  }
  if (isError) {
    message.result.isError = true
  }
  return message
}

async function handleProxyToolCall(message) {
  const id = message.id
  const params = message.params ?? {}
  const rawArgs = params['arguments']
  const args = rawArgs && typeof rawArgs === 'object' ? rawArgs : {}
  const toolName = typeof params.name === 'string' ? params.name : ''

  try {
    const output = await runProxyToolCall(toolName, args)
    outputMessage(makeToolResponse(id, output, false))
  } catch (error) {
    const messageText = error instanceof Error ? error.message : String(error)
    outputMessage(makeToolResponse(id, messageText, true))
  }
}

async function callUpstreamTool(toolName, args) {
  const id = `proxy-${++proxyCallCounter}`
  const callArgs = {...args}
  projectPathManager.injectProjectPathArgs(toolName, callArgs)
  const message = {
    jsonrpc: '2.0',
    id,
    method: 'tools/call',
    params: {
      name: toolName,
      arguments: callArgs
    }
  }

  const response = await new Promise((resolve, reject) => {
    pendingProxyCalls.set(id, {resolve, reject})
    void streamTransport.send(message).then((sendResult) => {
      if (!sendResult.accepted) {
        pendingProxyCalls.delete(id)
        reject(new Error(formatSendError(sendResult)))
      }
    })
  })

  if (response.error) {
    throw new Error(response.error.message || 'Upstream tool error')
  }

  const result = response.result ?? response
  if (result?.isError) {
    throw new Error(extractTextFromResult(result) || 'Upstream tool error')
  }
  return result
}

function handleUpstreamMessage(message) {
  if (Array.isArray(message)) {
    for (const entry of message) handleUpstreamMessage(entry)
    return
  }

  if (!message || typeof message !== 'object') return

  if (message.id === PROBE_MESSAGE_ID) {
    return
  }

  if (message.id !== undefined && pendingProxyCalls.has(message.id)) {
    const pending = pendingProxyCalls.get(message.id)
    pendingProxyCalls.delete(message.id)
    pending.resolve(message)
    return
  }

  if (message.id !== undefined) {
    const method = pendingMethods.get(message.id)
    if (method) pendingMethods.delete(message.id)
  }

  if (message.result && Array.isArray(message.result.tools)) {
    const upstreamTools = message.result.tools
    projectPathManager.updateProjectPathKeys(upstreamTools)
    projectPathManager.stripProjectPathFromTools(upstreamTools)
    const blocked = new Set([...BLOCKED_TOOL_NAMES, ...REPLACED_TOOL_NAMES])
    message.result.tools = mergeToolLists(proxyToolSpecs, upstreamTools, blocked)
  }

  outputMessage(message)
}

function handleClientMessage(message) {
  if (Array.isArray(message)) {
    for (const entry of message) handleClientMessage(entry)
    return
  }

  if (!message || typeof message !== 'object') return

  if (message.method) {
    if (message.id !== undefined && message.id !== null) {
      pendingMethods.set(message.id, message.method)
    }

    if (message.method === 'tools/call') {
      const params = message.params ?? {}
      const rawArgs = params['arguments']
      const args = rawArgs && typeof rawArgs === 'object' ? rawArgs : {}
      const toolName = typeof params.name === 'string' ? params.name : null

      if (toolName && BLOCKED_TOOL_NAMES.has(toolName)) {
        if (message.id !== undefined && message.id !== null) {
          outputMessage(makeToolResponse(message.id, blockedToolMessage(toolName), true))
        }
        return
      }

      if (toolName && proxyToolNames.has(toolName)) {
        void handleProxyToolCall(message)
        return
      }

      projectPathManager.injectProjectPathArgs(toolName, args)

      params['arguments'] = args
      message.params = params
    }
  }

  void streamTransport.send(message).then((sendResult) => {
    if (!sendResult.accepted && message.id !== undefined && message.id !== null) {
      outputMessage(makeToolResponse(message.id, formatSendError(sendResult), true))
    }
  })
}

function startStdio() {
  const rl = readline.createInterface({ input: stdin, crlfDelay: Infinity })

  rl.on('line', (line) => {
    const trimmed = line.trim()
    if (!trimmed) return

    try {
      const message = JSON.parse(trimmed)
      handleClientMessage(message)
    } catch (error) {
      warn(`Invalid JSON from stdin: ${error.message}`)
    }
  })
}

startStdio()
