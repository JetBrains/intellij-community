// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export function createMockToolCaller(responders = {}) {
  const calls = []
  async function callUpstreamTool(name, args) {
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

export function createSeededRng(seed) {
  let state = seed >>> 0
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0
    return state / 0x100000000
  }
}

export function randInt(rng, min, max) {
  return Math.floor(rng() * (max - min + 1)) + min
}

export function randString(rng, length) {
  const alphabet = 'abcdefghijklmnopqrstuvwxyz'
  let out = ''
  for (let i = 0; i < length; i += 1) {
    out += alphabet[randInt(rng, 0, alphabet.length - 1)]
  }
  return out
}
