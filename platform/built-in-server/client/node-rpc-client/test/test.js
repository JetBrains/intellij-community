const should = require("should")
const rpcClient = require("../out/rpc-client")
const rpc = require("../out/rpc")

describe("RPC", function () {
  it("connect", function (done) {
    const transport = new rpcClient.SocketTransport()
    transport.opened = function () {
      done()
    }
    transport.connect()
  })
  it("connect", function (done) {
    this.timeout(5000000)

    const transport = new rpcClient.SocketTransport()
    transport.opened = function () {
    }
    transport.connect(63343)

    const rpcServer = new rpc.JsonRpc(transport)
    rpcServer.call("Ide", "about")
        .then(function (r) {
          console.log(r)
          done()
        }, function (e) {
          throw e
        })
  })
})