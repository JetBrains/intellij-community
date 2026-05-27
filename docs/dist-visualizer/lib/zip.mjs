import { readFileSync } from "node:fs"
import { inflateRawSync } from "node:zlib"

const EOCD_SIG = 0x06054b50
const CDR_SIG = 0x02014b50
const LFH_SIG = 0x04034b50

export function readZip(path) {
  const buf = readFileSync(path)
  const eocd = findEocd(buf)
  const cdrOffset = buf.readUInt32LE(eocd + 16)
  const totalEntries = buf.readUInt16LE(eocd + 10)
  const entries = new Map()
  let p = cdrOffset
  for (let i = 0; i < totalEntries; i++) {
    if (buf.readUInt32LE(p) !== CDR_SIG) throw new Error(`bad CDR at ${p}`)
    const method = buf.readUInt16LE(p + 10)
    const compSize = buf.readUInt32LE(p + 20)
    const uncSize = buf.readUInt32LE(p + 24)
    const nameLen = buf.readUInt16LE(p + 28)
    const extraLen = buf.readUInt16LE(p + 30)
    const commentLen = buf.readUInt16LE(p + 32)
    const lfhOffset = buf.readUInt32LE(p + 42)
    const name = buf.slice(p + 46, p + 46 + nameLen).toString("utf8")
    entries.set(name, { method, compSize, uncSize, lfhOffset })
    p += 46 + nameLen + extraLen + commentLen
  }
  return {
    names: () => entries.keys(),
    read: (name) => {
      const e = entries.get(name)
      if (!e) return null
      return extract(buf, e)
    },
    readAllText: (filterFn) => {
      const out = new Map()
      for (const [name, e] of entries) {
        if (filterFn && !filterFn(name)) continue
        const data = extract(buf, e)
        out.set(name, data.toString("utf8"))
      }
      return out
    },
  }
}

function extract(buf, e) {
  if (buf.readUInt32LE(e.lfhOffset) !== LFH_SIG) throw new Error("bad LFH")
  const nameLen = buf.readUInt16LE(e.lfhOffset + 26)
  const extraLen = buf.readUInt16LE(e.lfhOffset + 28)
  const dataStart = e.lfhOffset + 30 + nameLen + extraLen
  const raw = buf.slice(dataStart, dataStart + e.compSize)
  if (e.method === 0) return raw
  if (e.method === 8) return inflateRawSync(raw)
  throw new Error(`unsupported compression method ${e.method}`)
}

function findEocd(buf) {
  const max = Math.min(buf.length, 65536 + 22)
  for (let i = buf.length - 22; i >= buf.length - max; i--) {
    if (i < 0) break
    if (buf.readUInt32LE(i) === EOCD_SIG) return i
  }
  throw new Error("EOCD not found")
}
