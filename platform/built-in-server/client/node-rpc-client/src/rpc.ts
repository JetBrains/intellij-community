import Promise = require("bluebird")

class PromiseCallback {
  constructor(public resolve: (value?: any) => void, public reject: (error?: any) => void) {
  }
}

export interface Transport {
  opened?: () => void

  /**
   * Internal use only (JsonRpc configure it).
   */
  messageReceived: (message: Array<any>) => void

  connect(port: number): void

  send(id: number, domain: string, command: string, params: any[]): void

  sendResult(id: number, result: any):void

  sendError(id: number, error: any):void
}

export class JsonRpc {
  private messageIdCounter = 0
  private callbacks: Map<number, PromiseCallback> = new Map<number, PromiseCallback>()
  private domains: Map<string, any> = new Map<string, any>()

  constructor(private transport: Transport) {
    transport.messageReceived = this.messageReceived.bind(this)
  }

  public call<T>(domain: string, command: string, ...params: any[]): Promise<T> {
    return new Promise((resolve: (value: T) => void, reject: (error?: any) => void) => {
      const id = this.messageIdCounter++
      this.callbacks.set(id, new PromiseCallback(resolve, reject))
      this.transport.send(id, domain, command, params)
    })
  }

  private messageReceived(message: Array<any>) {
    if (message.length === 1 || (message.length === 2 && !(typeof message[1] === "string"))) {
      const promiseCallback = this.callbacks.get(message[0])
      const singletonArray = safeGet(message, 1)
      if (singletonArray == null) {
        promiseCallback.resolve()
      }
      else {
        promiseCallback.resolve(singletonArray[0])
      }
    }
    else {
      let id: number
      let offset: number
      if (typeof message[0] === "string") {
        id = -1
        offset = 0
      }
      else {
        id = message[0]
        offset = 1
      }

      const domainName = message[offset]
      if (domainName === "e") {
        console.assert(id != -1)
        console.assert(message.length === 3)
        this.callbacks.get(id).reject(message[2])
        return
      }

      const onRejected = id === -1 ? null : (error: any) => this.transport.sendError(id, error)
      try {
        const object = this.domains.get(domainName)
        const method = object[message[offset + 1]]
        const args = safeGet(message, offset + 2)
        const result: any = (args === null) ? method.call(object) : method.apply(object, args)
        if (id !== -1) {
          const onFulfilled = (result: any) => this.transport.sendResult(id, result)
          if (result instanceof Promise) {
            (<Promise<any>>result).done(onFulfilled, onRejected)
          }
          else {
            onFulfilled(result)
          }
        }
      }
      catch (e) {
        console.error(e)
        if (onRejected != null) {
          onRejected(e)
        }
      }
    }
  }
}

function safeGet(a: any[], index: number): Array<any> {
  return index < a.length ? a[index] : null
}