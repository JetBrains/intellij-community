/// <reference path="../typings/node/node.d.ts" />

import net = require("net")

export class RpcClient {
  connect(port:Number = 63342) {
    var socket = net.connect({port: port}, function () {
      console.log('Connected to IJ RPC server localhost:' + port)
    });

    var jsonRpc = new JsonRpc(new Socket(socket))
    var decoder:MessageDecoder = new MessageDecoder(jsonRpc.messageReceived)
    socket.on('data', decoder.messageReceived)
  }
}

enum State {LENGTH, CONTENT}

class Socket {
  private uint32Buffer = new Buffer(4)

  constructor(private socket:net.Socket) {
  }

  send(message:string) {
    var encoded = new Buffer(message)
    this.uint32Buffer.writeUInt32BE(Buffer.byteLength(message), 0)
    this.socket.write(this.uint32Buffer)
    this.socket.write(message, 'utf-8')
  }
}

class JsonRpc {
  private messageIdCounter = 0
  private callbacks:Map<number, (result?:any)=>void> = new Map<number, (result:any)=>void>()
  private domains:Map<string, any> = new Map<string, any>();

  constructor(private socket:Socket) {
  }

  messageReceived(message:Array<any>) {
    if (message.length === 1 || (message.length === 2 && !(typeof message[1] === 'string'))) {
      var f = this.callbacks.get(message[0])
      var singletonArray = safeGet(message, 1)
      if (singletonArray == null) {
        f()
      }
      else {
        f(singletonArray[0])
      }
    }
    else {
      var id:number
      var offset:number
      if (typeof message[0] === 'string') {
        id = -1
        offset = 0
      }
      else {
        id = message[0]
        offset = 1
      }

      var args = safeGet(message, offset + 2)
      var errorCallback:(message:String)=>void
      if (id === -1) {
        errorCallback = null
      }
      else {
        var resultCallback = (result:any) => this.socket.send("[" + id + ", \"r\", " + JSON.stringify(result) + "]")
        errorCallback = (error:any) => this.socket.send("[$id, \"e\", " + JSON.stringify(error) + "]")
        if (args == null) {
          args = [resultCallback, errorCallback]
        }
        else {
          var regularArgs = args
          args = regularArgs.concat(errorCallback, resultCallback)
        }
      }

      try {
        var o = this.domains.get(message[offset]);
        o[message[offset + 1]].apply(o, args);
      }
      catch (e) {
        console.error(e)
        if (errorCallback != null) {
          errorCallback(e)
        }
      }
    }
  }
}

function safeGet(a:Array<any>, index:number):Array<any> {
  return index < a.length ? a[index] : null
}

class MessageDecoder {
  private state:State = State.LENGTH
  private contentLength:number = 0

  private buffers:Array<Buffer> = []
  private totalBufferLength:number = 0
  private offset:number = 0

  constructor(private messageProcessor:(message:any)=>void) {
  }

  private byteConsumed(count:number) {
    this.offset += count
    this.totalBufferLength -= count
  }

  messageReceived(buffer:Buffer) {
    this.totalBufferLength += buffer.length

    while (true) {
      //noinspection FallThroughInSwitchStatementJS
      switch (this.state) {
        case State.LENGTH: {
          if (this.totalBufferLength < 4) {
            this.buffers.push(buffer)
            return
          }

          var totalBuffer:Buffer
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

          var totalBuffer:Buffer
          if (this.buffers.length === 0) {
            totalBuffer = buffer
          }
          else {
            this.buffers.push(buffer)
            totalBuffer = Buffer.concat(this.buffers, this.totalBufferLength)
            this.buffers.length = 0
          }

          var message = JSON.parse(totalBuffer.toString('utf8', this.offset, this.contentLength));
          this.state = State.LENGTH
          this.byteConsumed(this.contentLength)
          this.contentLength = 0
          buffer = totalBuffer

          this.messageProcessor(message)
        }
      }
    }
  }
}