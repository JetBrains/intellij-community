// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {createParser} from './vendor/eventsource-parser/index.js'

export const PROBE_MESSAGE_ID = '__mcp_proxy_probe__'

const MAX_PROBE_MESSAGE_BYTES = 64 * 1024

export function createStreamTransport({
  explicitUrl,
  preferredPorts,
  portScanStart,
  portScanLimit,
  connectTimeoutMs,
  scanTimeoutMs,
  queueLimit,
  retryAttempts,
  retryBaseDelayMs,
  buildUrl,
  note,
  warn,
  exit,
  onMessage
}) {
  const logNote = typeof note === 'function' ? note : () => {}
  const logWarn = typeof warn === 'function' ? warn : () => {}
  const doExit = typeof exit === 'function' ? exit : () => {}
  const handleMessage = typeof onMessage === 'function' ? onMessage : () => {}
  const attempts = Math.max(1, retryAttempts ?? 1)
  const preferredPortList = normalizePreferredPorts(preferredPorts)

  let postUrl = null
  let sessionId = null
  let protocolVersionHeader = null
  let serverRetryMs = null
  let resolvePromise = null
  let serverEventStreamStarted = false
  const queuedMessages = []

  function buildRequestHeaders() {
    const headers = {
      'Content-Type': 'application/json',
      Accept: 'application/json, text/event-stream'
    }
    if (sessionId) headers['Mcp-Session-Id'] = sessionId
    if (protocolVersionHeader) headers['MCP-Protocol-Version'] = protocolVersionHeader
    return headers
  }

  function buildEventStreamHeaders() {
    const headers = { Accept: 'text/event-stream' }
    if (sessionId) headers['Mcp-Session-Id'] = sessionId
    if (protocolVersionHeader) headers['MCP-Protocol-Version'] = protocolVersionHeader
    return headers
  }

  function updateStreamHeaders(response) {
    const nextSessionId = response.headers.get('mcp-session-id')
    if (nextSessionId) sessionId = nextSessionId
    const nextProtocol = response.headers.get('mcp-protocol-version')
    if (nextProtocol) protocolVersionHeader = nextProtocol
  }

  function resetConnectionState(reason) {
    if (reason) {
      logWarn(`Resetting MCP stream connection (${reason})`)
    }
    postUrl = null
    sessionId = null
    protocolVersionHeader = null
    serverRetryMs = null
    serverEventStreamStarted = false
  }

  function getStreamReconnectDelayMs(attempt) {
    if (Number.isFinite(serverRetryMs)) return serverRetryMs
    return computeBackoffDelayMs(attempt)
  }

  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms))
  }

  function computeBackoffDelayMs(attempt) {
    const jitter = 0.5 + Math.random()
    const baseDelay = retryBaseDelayMs * Math.pow(2, attempt - 1)
    return Math.round(baseDelay * jitter)
  }

  function normalizePreferredPorts(ports) {
    if (!Array.isArray(ports)) return []
    const unique = new Set()
    for (const value of ports) {
      const port = Number.parseInt(value, 10)
      if (!Number.isFinite(port) || port <= 0) continue
      unique.add(port)
    }
    return [...unique]
  }

  async function handleStreamResponse(response, url) {
    const contentType = response.headers.get('content-type') || ''
    if (contentType.includes('text/event-stream')) {
      await readEventStream(response, url)
      return
    }

    let text
    try {
      text = await response.text()
    } catch (error) {
      logWarn(`Failed to read MCP response body from ${url}: ${error.message}`)
      return
    }

    if (!text) return

    try {
      const parsed = JSON.parse(text)
      handleMessage(parsed)
    } catch (error) {
      logWarn(`Invalid JSON response from MCP (${url}): ${error.message}`)
    }
  }

  function handleStreamEvent(eventName, eventData, contextLabel) {
    const name = eventName || 'message'

    if (name === 'message') {
      try {
        const parsed = JSON.parse(eventData)
        handleMessage(parsed)
      } catch (error) {
        logWarn(`Invalid JSON message from MCP stream (${contextLabel}): ${error.message}`)
      }
      return
    }

    if (name === 'error') {
      logWarn(`MCP stream error (${contextLabel}): ${eventData}`)
    }
  }

  async function readEventStream(response, contextLabel) {
    if (!response.body) return

    serverRetryMs = null
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    const parser = createParser({
      onEvent: (event) => {
        handleStreamEvent(event.event, event.data, contextLabel)
      },
      onRetry: (retry) => {
        serverRetryMs = retry
      }
    })

    while (true) {
      const { value, done } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value, { stream: true })
      if (chunk) {
        parser.feed(chunk)
      }
    }

    const tail = decoder.decode()
    if (tail) {
      parser.feed(tail)
    }
    parser.feed('\n')
  }

  async function openEventStream(url, timeoutMs = connectTimeoutMs) {
    let response
    const controller = timeoutMs > 0 ? new AbortController() : null
    const timeout = controller ? setTimeout(() => controller.abort(), timeoutMs) : null
    try {
      response = await fetch(url, {
        headers: buildEventStreamHeaders(),
        signal: controller ? controller.signal : undefined
      })
    } catch (error) {
      const reason = error && error.name === 'AbortError'
        ? `Timeout after ${timeoutMs}ms`
        : error.message
      return { ok: false, error: reason }
    } finally {
      if (timeout) clearTimeout(timeout)
    }

    if (!response.ok || !response.body) {
      return { ok: false, error: `HTTP ${response.status}` }
    }

    const contentType = response.headers.get('content-type')
    if (contentType && !contentType.includes('text/event-stream')) {
      await response.body.cancel()
      return { ok: false, error: `Unexpected content-type ${contentType}` }
    }

    updateStreamHeaders(response)
    return { ok: true, response }
  }

  async function startServerEventStream(url) {
    let attempt = 1

    while (postUrl === url) {
      const result = await openEventStream(url, connectTimeoutMs)
      if (!result.ok) {
        logWarn(`MCP stream connection failed (${attempt}/${attempts}) at ${url}: ${result.error}`)
      } else {
        await readEventStream(result.response, url)
        logWarn(`MCP stream closed (${attempt}/${attempts}) at ${url}`)
      }

      if (postUrl !== url) break

      if (attempt >= attempts) {
        logWarn(`MCP stream reconnect attempts exhausted (${attempts}) at ${url}`)
        resetConnectionState('stream_reconnect_exhausted')
        if (queuedMessages.length > 0) {
          ensureStreamConnection()
        }
        break
      }

      const delay = getStreamReconnectDelayMs(attempt)
      await sleep(delay)
      attempt += 1
    }

    serverEventStreamStarted = false
  }

  function maybeStartServerEventStream() {
    if (!postUrl || serverEventStreamStarted) return
    serverEventStreamStarted = true
    void startServerEventStream(postUrl)
  }

  async function sendStreamMessageOnce(url, message, timeoutMs) {
    let response
    const controller = timeoutMs > 0 ? new AbortController() : null
    const timeout = controller ? setTimeout(() => controller.abort(), timeoutMs) : null
    try {
      response = await fetch(url, {
        method: 'POST',
        headers: buildRequestHeaders(),
        body: JSON.stringify(message),
        signal: controller ? controller.signal : undefined
      })
    } catch (error) {
      const reason = error && error.name === 'AbortError'
        ? `Timeout after ${timeoutMs}ms`
        : error.message
      return { ok: false, error: reason }
    } finally {
      if (timeout) clearTimeout(timeout)
    }

    if (!response.ok) {
      let text
      try {
        text = await response.text()
      } catch {
        text = ''
      }
      const suffix = text ? `: ${text}` : ''
      return { ok: false, error: `HTTP ${response.status}${suffix}` }
    }

    updateStreamHeaders(response)
    void handleStreamResponse(response, url)
    return { ok: true }
  }

  async function sendStreamMessageWithRetry(url, message, timeoutMs) {
    let lastError = 'Unknown error'

    for (let attempt = 1; attempt <= attempts; attempt += 1) {
      const result = await sendStreamMessageOnce(url, message, timeoutMs)
      if (result.ok) return result
      lastError = result.error
      if (attempt < attempts) {
        await sleep(computeBackoffDelayMs(attempt))
      }
    }

    return { ok: false, error: lastError }
  }

  function queueMessage(message) {
    if (queueLimit > 0 && queuedMessages.length >= queueLimit) {
      return false
    }
    queuedMessages.push(message)
    return true
  }

  function flushQueue() {
    const pending = queuedMessages.splice(0, queuedMessages.length)
    for (const message of pending) {
      void send(message)
    }
  }

  function ensureStreamConnection() {
    if (resolvePromise) return
    resolvePromise = resolveStreamConnection().finally(() => {
      resolvePromise = null
    })
  }

  function buildProbeMessage(candidate) {
    if (candidate && typeof candidate === 'object') {
      try {
        const size = Buffer.byteLength(JSON.stringify(candidate), 'utf8')
        if (size <= MAX_PROBE_MESSAGE_BYTES) {
          return {message: candidate, consumeFirst: true}
        }
      } catch {
        // Fall through to lightweight probe.
      }
    }
    return {
      message: {
        jsonrpc: '2.0',
        id: PROBE_MESSAGE_ID,
        method: 'tools/list',
        params: {}
      },
      consumeFirst: false
    }
  }

  async function resolveStreamConnection() {
    if (postUrl) {
      flushQueue()
      return
    }
    if (queuedMessages.length === 0) return

    const probe = buildProbeMessage(queuedMessages[0])

    const applySuccessfulConnect = (url) => {
      if (probe.consumeFirst) {
        queuedMessages.shift()
      }
      postUrl = url
      logNote(`Connected to JetBrains MCP stream at ${url}`)
      maybeStartServerEventStream()
      flushQueue()
    }

    const connectOnce = async (url, timeoutMs) => {
      const result = await sendStreamMessageOnce(url, probe.message, timeoutMs)
      if (result.ok) {
        applySuccessfulConnect(url)
        return true
      }
      logWarn(`Stream probe failed at ${url}: ${result.error}`)
      return false
    }

    const connectWithRetry = async (url, timeoutMs) => {
      const result = await sendStreamMessageWithRetry(url, probe.message, timeoutMs)
      if (result.ok) {
        applySuccessfulConnect(url)
        return true
      }
      logWarn(`Stream probe failed at ${url}: ${result.error}`)
      return false
    }

    const attemptConnect = async () => {
      const triedPorts = new Set()

      const tryPort = async (port, timeoutMs, useRetry = true) => {
        if (triedPorts.has(port)) return false
        triedPorts.add(port)
        const connectFn = useRetry ? connectWithRetry : connectOnce
        return await connectFn(buildUrl(port), timeoutMs)
      }

      if (explicitUrl) {
        logNote(`Connecting to JetBrains MCP stream at ${explicitUrl}`)
        const ok = await connectWithRetry(explicitUrl, connectTimeoutMs)
        if (!ok) {
          logWarn(`Failed to connect to stream endpoint ${explicitUrl}`)
        }
        return ok
      }

      for (const port of preferredPortList) {
        if (await tryPort(port, connectTimeoutMs, false)) return true
      }

      const startPort = portScanStart
      const limit = Math.max(1, portScanLimit)
      const endPort = startPort + limit - 1

      if (await tryPort(startPort, connectTimeoutMs)) return true

      if (limit === 1) {
        logWarn(`Failed to find JetBrains MCP stream on ports ${startPort}-${endPort}`)
        return false
      }

      const scanStart = startPort + 1
      logNote(`Scanning for JetBrains MCP stream on ports ${scanStart}-${endPort} with timeout ${scanTimeoutMs}ms`)

      for (let port = scanStart; port <= endPort; port += 1) {
        if (await tryPort(port, scanTimeoutMs)) return true
      }

      logWarn(`Failed to find JetBrains MCP stream on ports ${startPort}-${endPort}`)
      return false
    }

    let attempt = 1
    while (!postUrl && queuedMessages.length > 0) {
      const ok = await attemptConnect()
      if (ok) return

      const delay = computeBackoffDelayMs(attempt)
      logWarn(`Retrying MCP stream connection in ${delay}ms (attempt ${attempt})`)
      await sleep(delay)
      attempt += 1
    }
  }

  async function send(message) {
    if (!postUrl) {
      if (!queueMessage(message)) return {accepted: false, reason: 'queue_limit'}
      ensureStreamConnection()
      return {accepted: true}
    }

    const result = await sendStreamMessageWithRetry(postUrl, message, connectTimeoutMs)
    if (!result.ok) {
      const error = `Failed to send MCP message after ${attempts} attempts: ${result.error}`
      logWarn(error)
      const queued = queueMessage(message)
      resetConnectionState('send_failed')
      if (queued) {
        ensureStreamConnection()
        return {accepted: true, reason: 'requeued', error}
      }
      return {accepted: false, reason: 'send_failed', error}
    }
    return {accepted: true}
  }

  return {send}
}
