// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {strictEqual} from 'node:assert/strict'
import type {ToolArgs, UpstreamToolCaller} from '../types'

interface ToolCall {
  name: string
  args: ToolArgs
}

type Responder = ((args: ToolArgs) => Promise<unknown> | unknown) | unknown

type ResponderMap = Record<string, Responder>

export function createMockToolCaller(responders: ResponderMap = {}): {
  callUpstreamTool: UpstreamToolCaller
  calls: ToolCall[]
} {
  const calls: ToolCall[] = []
  const callUpstreamTool: UpstreamToolCaller = async (name, args) => {
    calls.push({name, args})
    const responder = responders[name]
    if (typeof responder === 'function') {
      return await responder(args)
    }
    if (responder !== undefined) {
      return responder
    }
    return {text: '{}'}
  }

  return {callUpstreamTool, calls}
}

export function assertSingleCall(calls: ToolCall[]): ToolCall {
  strictEqual(calls.length, 1)
  return calls[0]
}

export function createSeededRng(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0
    return state / 0x100000000
  }
}

export function randInt(rng: () => number, min: number, max: number): number {
  return Math.floor(rng() * (max - min + 1)) + min
}

export function randString(rng: () => number, length: number): string {
  const alphabet = 'abcdefghijklmnopqrstuvwxyz'
  let out = ''
  for (let i = 0; i < length; i += 1) {
    out += alphabet[randInt(rng, 0, alphabet.length - 1)]
  }
  return out
}
