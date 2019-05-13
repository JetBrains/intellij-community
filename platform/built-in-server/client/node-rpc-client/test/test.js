const assertThat = require("should/as-function")

const rpcNode = require("../out/rpc-node")
const rpc = require("../out/rpc")

describe("RPC", function () {
  it("connect", function (done) {
    const transport = new rpcNode.SocketTransport()
    transport.opened = function () {
      done()
    }
    transport.connect()
  })

  it("call method", function () {
    const rpcServer = rpcNode.connect(63343)
    return assertThat(rpcServer.call("Ide", "about")).fulfilled()
  })
})