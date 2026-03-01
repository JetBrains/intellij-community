// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import isPortReachable from 'is-port-reachable'
import pRetry from 'p-retry'
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp.js'

type TransportMessage = unknown
type TransportSendOptions = Record<string, unknown> | undefined

const SESSION_NOT_FOUND_RE = /session not found/i

interface PortCandidate {
  port: number
  kind: 'preferred' | 'scan'
}

export interface StreamTransportOptions {
  explicitUrl?: string
  preferredPorts?: number[]
  portScanStart: number
  portScanLimit: number
  connectTimeoutMs: number
  scanTimeoutMs: number
  queueLimit: number
  queueWaitTimeoutMs: number
  retryAttempts: number
  retryBaseDelayMs: number
  buildUrl: (port: number) => string
  note?: (message: string) => void
  warn?: (message: string) => void
  probeHost?: string
}

export interface McpStreamTransport {
  sessionId: string | undefined
  onmessage?: (message: TransportMessage, extra?: unknown) => void
  onerror?: (error: Error) => void
  onclose?: () => void
  start: () => Promise<void>
  send: (message: TransportMessage, options?: TransportSendOptions) => Promise<void>
  close: () => Promise<void>
  setProtocolVersion: (version: string) => void
  resetTransport: (reason: unknown) => Promise<void>
}

interface QueueEntry {
  message: TransportMessage
  options: TransportSendOptions
  resolve: () => void
  reject: (error: unknown) => void
  timeout: NodeJS.Timeout | null
}

function resolveTimeout(timeoutMs: number | null | undefined): number | undefined {
  if (timeoutMs === undefined || timeoutMs === null) return undefined
  return timeoutMs > 0 ? timeoutMs : undefined
}

function isSessionNotFoundError(error: unknown): boolean {
  if (!error) return false
  const message = error instanceof Error ? error.message : String(error)
  if (!SESSION_NOT_FOUND_RE.test(message)) return false
  const code = (error as {code?: unknown}).code
  if (typeof code === 'number') {
    return code === -32000 || code === 400 || code === 404 || code === 410
  }
  return true
}

function normalizePortList(
  preferredPorts: number[] | undefined,
  portScanStart: number,
  portScanLimit: number
): PortCandidate[] {
  const seen = new Set()
  const candidates: PortCandidate[] = []

  for (const port of preferredPorts || []) {
    if (!Number.isFinite(port) || port <= 0) continue
    if (seen.has(port)) continue
    seen.add(port)
    candidates.push({port, kind: 'preferred'})
  }

  const limit = Number.isFinite(portScanLimit) && portScanLimit > 0 ? portScanLimit : 0
  const start = Number.isFinite(portScanStart) && portScanStart > 0 ? portScanStart : 0
  for (let i = 0; i < limit; i += 1) {
    const port = start + i
    if (port <= 0 || seen.has(port)) continue
    seen.add(port)
    candidates.push({port, kind: 'scan'})
  }

  return candidates
}

function formatProbedPortList(candidates: PortCandidate[]): string {
  return candidates.map((candidate) => String(candidate.port)).join(', ')
}

function buildEndpointNotFoundMessage(candidates: PortCandidate[]): string {
  return `Failed to locate MCP stream endpoint. Probed ports: ${formatProbedPortList(candidates)}. Install the "MCP Server" plugin and ensure it is enabled in Settings | Tools | MCP Server.`
}

class StreamTransportImpl implements McpStreamTransport {
  _options: StreamTransportOptions
  _queue: QueueEntry[]
  _connectPromise: Promise<void> | null
  _transport: StreamableHTTPClientTransport | null
  _protocolVersion: string | null
  _closed: boolean
  _closeNotified: boolean
  sessionId: string | undefined
  onmessage?: (message: TransportMessage, extra?: unknown) => void
  onerror?: (error: Error) => void
  onclose?: () => void

  constructor(options: StreamTransportOptions) {
    this._options = options
    this._queue = []
    this._connectPromise = null
    this._transport = null
    this._protocolVersion = null
    this._closed = false
    this._closeNotified = false
    this.sessionId = undefined
  }

  async start(): Promise<void> {
    await this._ensureConnected()
  }

  async send(message: TransportMessage, options?: TransportSendOptions): Promise<void> {
    if (this._closed) {
      throw new Error('Transport is closed')
    }

    if (this._transport) {
      await this._sendDirect(message, options)
      return
    }

    await this._enqueue(message, options)
  }

  async close(): Promise<void> {
    if (this._closed) return
    this._closed = true

    if (this._transport) {
      await this._transport.close()
      this._transport = null
    }

    this._rejectQueue(new Error('Transport closed'))
    this._emitClose()
  }

  setProtocolVersion(version: string): void {
    this._protocolVersion = version
    if (this._transport?.setProtocolVersion) {
      this._transport.setProtocolVersion(version)
    }
  }

  async resetTransport(reason: unknown): Promise<void> {
    const warn = this._options.warn
    const message = reason instanceof Error ? reason.message : String(reason)
    if (warn) warn(`MCP stream session invalid; reconnecting. ${message}`)
    const transport = this._transport
    this._transport = null
    this.sessionId = undefined
    if (transport) {
      try {
        await transport.close()
      } catch (error) {
        const closeMessage = error instanceof Error ? error.message : String(error)
        if (warn) warn(`Failed to close stale MCP transport: ${closeMessage}`)
      }
    }
  }

  async _sendDirect(message: TransportMessage, options?: TransportSendOptions): Promise<void> {
    let retried = false
    while (true) {
      try {
        if (!this._transport) {
          await this._ensureConnected()
        }
        await this._transport!.send(message, options)
        this.sessionId = this._transport!.sessionId
        return
      } catch (error) {
        const err = error instanceof Error ? error : new Error(String(error))
        if (!retried && isSessionNotFoundError(err)) {
          retried = true
          await this.resetTransport(err)
          continue
        }
        if (this.onerror) this.onerror(err)
        throw err
      }
    }
  }

  async _enqueue(message: TransportMessage, options?: TransportSendOptions): Promise<void> {
    const limit = this._options.queueLimit
    if (limit > 0 && this._queue.length >= limit) {
      throw new Error(`MCP proxy queue limit (${limit}) reached before stream connection`)
    }

    await new Promise<void>((resolve, reject) => {
      const entry: QueueEntry = {
        message,
        options,
        resolve,
        reject,
        timeout: null
      }

      if (this._options.queueWaitTimeoutMs > 0) {
        entry.timeout = setTimeout(() => {
          this._removeQueueEntry(entry)
          reject(new Error(`Upstream tool call timed out before it was sent after ${this._options.queueWaitTimeoutMs}ms`))
        }, this._options.queueWaitTimeoutMs)
      }

      this._queue.push(entry)
      void this._ensureConnected().catch((error) => {
        this._removeQueueEntry(entry)
        reject(error)
      })
    })
  }

  async _ensureConnected(): Promise<void> {
    if (this._closed) throw new Error('Transport is closed')
    if (this._transport) return
    if (this._connectPromise) return this._connectPromise

    this._connectPromise = pRetry(
      async () => {
        const {explicitUrl, note, warn, preferredPorts, portScanStart, portScanLimit, buildUrl, probeHost} = this._options

        let targetUrl = explicitUrl
        if (!targetUrl) {
          const candidates = normalizePortList(preferredPorts, portScanStart, portScanLimit)
          if (candidates.length === 0) {
            throw new Error('No MCP stream ports configured')
          }

          for (const candidate of candidates) {
            const timeoutMs = candidate.kind === 'preferred'
              ? this._options.connectTimeoutMs
              : this._options.scanTimeoutMs
            const reachable = await isPortReachable(candidate.port, {
              host: probeHost,
              timeout: resolveTimeout(timeoutMs)
            })
            if (reachable) {
              targetUrl = buildUrl(candidate.port)
              break
            }
          }

          if (!targetUrl) {
            if (warn) {
              warn(`No reachable MCP stream ports found during scan. Probed ports: ${formatProbedPortList(candidates)}`)
            }
            throw new Error(buildEndpointNotFoundMessage(candidates))
          }
        }

        if (note) note(`Connecting to MCP stream ${targetUrl}`)
        const transport = new StreamableHTTPClientTransport(targetUrl)
        transport.onmessage = (message, extra) => {
          if (this.onmessage) this.onmessage(message, extra)
        }
        transport.onerror = (error) => {
          if (this.onerror) this.onerror(error)
        }
        transport.onclose = () => {
          this._transport = null
          this.sessionId = undefined
          this._emitClose()
        }
        if (this._protocolVersion && transport.setProtocolVersion) {
          transport.setProtocolVersion(this._protocolVersion)
        }

        await transport.start()
        this._transport = transport
        this.sessionId = transport.sessionId
        this._closeNotified = false
        await this._flushQueue()
      },
      {
        retries: Math.max(this._options.retryAttempts - 1, 0),
        minTimeout: this._options.retryBaseDelayMs,
        onFailedAttempt: (error) => {
          if (this._options.warn) {
            this._options.warn(`MCP stream connection attempt failed (${error.attemptNumber}/${error.retriesLeft + error.attemptNumber}): ${error.message}`)
          }
        }
      }
    ).finally(() => {
      this._connectPromise = null
    })

    return this._connectPromise
  }

  async _flushQueue(): Promise<void> {
    if (!this._transport || this._queue.length === 0) return
    const queued = this._queue.slice()
    this._queue.length = 0

    for (const entry of queued) {
      if (entry.timeout) {
        clearTimeout(entry.timeout)
        entry.timeout = null
      }
      try {
        await this._sendDirect(entry.message, entry.options)
        entry.resolve()
      } catch (error) {
        entry.reject(error)
      }
    }
  }

  _removeQueueEntry(entry: QueueEntry): void {
    const index = this._queue.indexOf(entry)
    if (index >= 0) {
      this._queue.splice(index, 1)
    }
    if (entry.timeout) {
      clearTimeout(entry.timeout)
      entry.timeout = null
    }
  }

  _rejectQueue(error: unknown): void {
    const queued = this._queue.slice()
    this._queue.length = 0
    for (const entry of queued) {
      if (entry.timeout) {
        clearTimeout(entry.timeout)
        entry.timeout = null
      }
      entry.reject(error)
    }
  }

  _emitClose(): void {
    if (this._closeNotified) return
    this._closeNotified = true
    if (this.onclose) this.onclose()
  }
}

export function createStreamTransport({
  explicitUrl,
  preferredPorts,
  portScanStart,
  portScanLimit,
  connectTimeoutMs,
  scanTimeoutMs,
  queueLimit,
  queueWaitTimeoutMs,
  retryAttempts,
  retryBaseDelayMs,
  buildUrl,
  note,
  warn,
  probeHost = '127.0.0.1'
}: StreamTransportOptions): McpStreamTransport {
  return new StreamTransportImpl({
    explicitUrl,
    preferredPorts,
    portScanStart,
    portScanLimit,
    connectTimeoutMs,
    scanTimeoutMs,
    queueLimit,
    queueWaitTimeoutMs,
    retryAttempts,
    retryBaseDelayMs,
    buildUrl,
    note,
    warn,
    probeHost
  })
}
