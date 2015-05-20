/// <reference path="../typings/node/node.d.ts" />
/// <reference path="../typings/bluebird/bluebird.d.ts" />
"use strict"

import Promise = require("bluebird")

class PromiseCallback {
  constructor(public resolve:(value?:any) => void, public reject:(error?:any) => void) {
  }
}

export interface Transport {
  send(id:number, domain:string, command:string, params:any[]):void

  sendResult(id:number, result:any):void

  sendError(id:number, error:any):void
}

export class JsonRpc {
  private messageIdCounter = 0
  private callbacks:Map<number, PromiseCallback> = new Map<number, PromiseCallback>()
  private domains:Map<string, any> = new Map<string, any>()

  constructor(private transport:Transport) {
  }

  public call<T>(domain:string, command:string, ...params: any[]):Promise<T> {
    return new Promise((resolve:(value:T) => void, reject:(error?:any) => void) => {
      var id = this.messageIdCounter++;
      this.callbacks.set(id, new PromiseCallback(resolve, reject))
      this.transport.send(id, domain, command, params)
    })
  }

  messageReceived(message:Array<any>) {
    if (message.length === 1 || (message.length === 2 && !(typeof message[1] === 'string'))) {
      var promiseCallback = this.callbacks.get(message[0])
      var singletonArray = safeGet(message, 1)
      if (singletonArray == null) {
        promiseCallback.resolve()
      }
      else {
        promiseCallback.resolve(singletonArray[0])
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

      var onRejected = id === -1 ? null : (error:any) => this.transport.sendError(id, error)
      try {
        var object = this.domains.get(message[offset])
        var method = object[message[offset + 1]]
        var result:any
        var args = safeGet(message, offset + 2)
        if (args === null) {
          result = method.call(object)
        }
        else {
          result = method.apply(object, args)
        }

        if (id !== -1) {
          var onFulfilled = (result:any) => this.transport.sendResult(id, result)
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

function safeGet(a:any[], index:number):Array<any> {
  return index < a.length ? a[index] : null
}