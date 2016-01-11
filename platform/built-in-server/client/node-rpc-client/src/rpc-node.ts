import * as net from "net"
import { JsonRpc, Transport } from "./rpc"

export function connect(port: number = 63342): JsonRpc {
  const transport = new SocketTransport()
  const server = new JsonRpc(transport)
  transport.connect(63343)
  return server
}

export class SocketTransport implements Transport {
  opened: () => void
  messageReceived: (message: Array<any>) => void

  constructor(private socket: net.Socket = new net.Socket()) {
  }

  connect(port: number = 63342) {
    this.socket.connect(port, null, ()=> {
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
  private totalBufferLength = 0
  private offset = 0

  constructor(private messageProcessor: (message: any)=>void) {
  }

  private byteConsumed(count: number) {
    this.offset += count
    this.totalBufferLength -= count
  }

  messageReceived(buffer: Buffer) {
    this.totalBufferLength += buffer.length

    while (true) {
      //noinspection FallThroughInSwitchStatementJS
      switch (this.state) {
        case State.LENGTH: {
          if (this.totalBufferLength < 4) {
            this.buffers.push(buffer)
            return
          }

          var totalBuffer: Buffer
          if (this.buffers.length === 0) {
            totalBuffer = buffer
          }
          else {
            this.buffers.push(buffer)
            totalBuffer = Buffer.concat(this.buffers, this.totalBufferLength)
            this.buffers.length = 0
          }

          this.state = State.CONTENT
          this.contentLength = totalBuffer.readUInt32BE(this.offset)
          this.byteConsumed(4)
          buffer = totalBuffer
        }

        case State.CONTENT: {
          if (this.totalBufferLength < this.contentLength) {
            this.buffers.push(buffer)
            return
          }

          var totalBuffer: Buffer
          if (this.buffers.length === 0) {
            totalBuffer = buffer
          }
          else {
            this.buffers.push(buffer)
            totalBuffer = Buffer.concat(this.buffers, this.totalBufferLength)
            this.buffers.length = 0
          }

          const rawMessage = totalBuffer.toString("utf8", this.offset, this.offset + this.contentLength)
          try {
            this.state = State.LENGTH
            this.byteConsumed(this.contentLength)
            this.contentLength = 0
            buffer = totalBuffer

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