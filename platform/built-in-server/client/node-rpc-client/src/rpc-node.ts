import * as net from "net"
import { JsonRpc, Transport } from "./rpc"
const debug = require("debug")("rpc")

export function connect(port: number = 63342, domains: { [domainName:string]: { [methodName:string]:Function; }; } = null): JsonRpc {
  const transport = new SocketTransport()
  const server = new JsonRpc(transport, domains)
  transport.connect(port)
  return server
}

export class SocketTransport implements Transport {
  opened: () => void
  messageReceived: (message: Array<any>) => void

  constructor(private socket: net.Socket = new net.Socket()) {
  }

  connect(port: number = 63342) {
    this.socket.connect(port, null, ()=> {
      debug("Connected to %s", port)
      const opened = this.opened
      if (opened != null) {
        opened()
      }
    })
    this.socket.on("error", (e: Error) => {
      console.error(e)
    })

    const messageReceived = this.messageReceived
    if (messageReceived == null) {
      console.warn("messageReceived is not specified, input will be ignored")
    }
    else {
      const messageDecoder = new MessageDecoder(messageReceived)
      this.socket.on("data", messageDecoder.messageReceived.bind(messageDecoder))
    }

    this.socket.write(new Buffer([67, 72, 105, -107, 126, -21, -81, -72, 64, 54, -87, -88, 0, -46, -48, 34, -7, -67]))
  }

  send(id: number, domain: string, command: string, params: any[] = null): void {
    const encodedParams = params == null || params.length === 0 ? null : JSON.stringify(params)
    const header = '[' + (id == -1 ? '' : (id + ', ')) + '"' + domain + '", "' + command + '"'
    const headerBuffer = new Buffer(4)
    headerBuffer.writeUInt32BE(header.length + (encodedParams == null ? 0 : Buffer.byteLength(encodedParams)) + 1 /* ] symbol*/, 0)
    this.socket.write(headerBuffer)

    debug("out: %s%s]", header, encodedParams || "")

    this.socket.write(header)
    if (encodedParams != null) {
      this.socket.write(encodedParams)
    }
    this.socket.write(']')
  }

  sendResult(id: number, result: any): void {
    this.sendResultOrError(id, result, false)
  }

  sendError(id: number, error: any): void {
    this.sendResultOrError(id, error, true)
  }

  private sendResultOrError(id: number, result: any, isError: boolean): void {
    var encodedResult = JSON.stringify(result)
    var header = id + ', "' + (isError ? 'e' : 'r') + '"'
    const headerBuffer = new Buffer(4)
    headerBuffer.writeUInt32BE(Buffer.byteLength(encodedResult) + header.length, 0)
    this.socket.write(headerBuffer)
    this.socket.write(encodedResult)
  }
}

const enum State {LENGTH, CONTENT}

class MessageDecoder {
  private state = State.LENGTH
  private contentLength = 0

  private buffers: Array<Buffer> = []
  private messageBuffer: Buffer

  private readableByteCount = 0

  private messageBufferOffset = 0

  constructor(private messageProcessor: (message: any)=>void) {
  }

  private concatBuffer(buffer: Buffer): Buffer {
    if (this.buffers.length === 0) {
      return buffer
    }

    this.buffers.push(buffer)
    let totalBuffer = Buffer.concat(this.buffers, this.readableByteCount)
    this.buffers.length = 0
    return totalBuffer
  }

  messageReceived(buffer: Buffer) {
    let offset = 0
    this.readableByteCount += buffer.length
    while (true) {
      //noinspection FallThroughInSwitchStatementJS
      switch (this.state) {
        case State.LENGTH: {
          if (this.readableByteCount < 4) {
            if (offset != 0) {
              buffer = buffer.slice(offset)
            }
            this.buffers.push(buffer)
            return
          }

          buffer = this.concatBuffer(buffer)
          this.state = State.CONTENT
          this.contentLength = buffer.readUInt32BE(offset)
          offset += 4

          if ((buffer.length - offset) < this.contentLength) {
            this.messageBuffer = new Buffer(this.contentLength)
            buffer.copy(this.messageBuffer, 0, offset)
            this.messageBufferOffset = 0
            this.readableByteCount = 0
            return
          }

          this.readableByteCount = buffer.length - offset
        }

        case State.CONTENT: {
          let rawMessage: string
          if (this.messageBuffer == null) {
            rawMessage = buffer.toString("utf8", offset, offset + this.contentLength)
            offset += this.contentLength
            this.readableByteCount = buffer.length - offset
          }
          else {
            const requiredByteCount = this.messageBuffer.length - this.messageBufferOffset
            if (requiredByteCount > this.readableByteCount) {
              buffer.copy(this.messageBuffer, this.messageBufferOffset, offset)
              this.messageBufferOffset += this.readableByteCount
              this.readableByteCount = 0
              return
            }
            else {
              const newOffset = offset + requiredByteCount
              buffer.copy(this.messageBuffer, this.messageBufferOffset, offset, newOffset)
              offset = newOffset
              this.readableByteCount = buffer.length - offset
              rawMessage = this.messageBuffer.toString("utf8")
            }
          }

          debug("in: %s", rawMessage)
          try {
            this.state = State.LENGTH
            this.contentLength = 0

            this.messageProcessor(JSON.parse(rawMessage))
          }
          catch (e) {
            console.error("Error: %s,\nInput: %s", e, rawMessage)
          }
        }
      }
    }
  }
}