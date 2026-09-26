// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import isPortReachable from 'is-port-reachable'

export interface ReachablePort {
  port: number
  url: string
}

export interface PortScanOptions {
  preferredPorts: number[]
  portScanStart: number
  portScanLimit: number
  scanTimeoutMs: number
  buildUrl: (port: number) => string
  probeHost?: string
  warn?: (msg: string) => void
}

function buildCandidateList(
  preferredPorts: number[],
  portScanStart: number,
  portScanLimit: number
): number[] {
  const seen = new Set<number>()
  const candidates: number[] = []

  for (const port of preferredPorts) {
    if (!Number.isFinite(port) || port <= 0 || seen.has(port)) continue
    seen.add(port)
    candidates.push(port)
  }

  const limit = Number.isFinite(portScanLimit) && portScanLimit > 0 ? portScanLimit : 0
  const start = Number.isFinite(portScanStart) && portScanStart > 0 ? portScanStart : 0
  for (let i = 0; i < limit; i++) {
    const port = start + i
    if (port <= 0 || seen.has(port)) continue
    seen.add(port)
    candidates.push(port)
  }

  return candidates
}

export async function findReachablePorts(options: PortScanOptions): Promise<ReachablePort[]> {
  const {preferredPorts, portScanStart, portScanLimit, scanTimeoutMs, buildUrl, probeHost = '127.0.0.1', warn} = options
  const candidates = buildCandidateList(preferredPorts, portScanStart, portScanLimit)
  if (candidates.length === 0) return []

  // Probe all ports in parallel with short timeout (discovery, not connection)
  const probeResults = await Promise.allSettled(
    candidates.map(async (port) => {
      const reachable = await isPortReachable(port, {
        host: probeHost,
        timeout: scanTimeoutMs > 0 ? scanTimeoutMs : undefined
      })
      return {port, reachable}
    })
  )

  const result: ReachablePort[] = []
  for (const probeResult of probeResults) {
    if (probeResult.status === 'fulfilled' && probeResult.value.reachable) {
      const port = probeResult.value.port
      result.push({port, url: buildUrl(port)})
    }
  }

  if (result.length === 0 && warn) {
    warn(`No reachable MCP stream ports found. Probed: ${candidates.join(', ')}`)
  }

  return result
}
