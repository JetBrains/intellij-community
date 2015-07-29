/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.unscramble

import junit.framework.TestCase

/**
 * @author peter
 */
class ThreadDumpParserTest extends TestCase {
  public void "test waiting threads are not locking"() {
    String text = """
"1" daemon prio=10 tid=0x00002b5bf8065000 nid=0x4294 waiting for monitor entry [0x00002b5aadb5d000]
   java.lang.Thread.State: BLOCKED (on object monitor)
    at bitronix.tm.resource.common.XAPool.findXAResourceHolder(XAPool.java:213)
    - waiting to lock <0x0000000092daa138> (a bitronix.tm.resource.common.XAPool)
    at bitronix.tm.resource.jdbc.PoolingDataSource.findXAResourceHolder(PoolingDataSource.java:345)
    at bitronix.tm.resource.ResourceRegistrar.findXAResourceHolder(ResourceRegistrar.java:124)
    at bitronix.tm.BitronixTransaction.enlistResource(BitronixTransaction.java:120)

"2" daemon prio=10 tid=0x00002b5bf806f000 nid=0xfbd7 waiting for monitor entry [0x00002b5aaf87a000]
   java.lang.Thread.State: BLOCKED (on object monitor)
    at java.lang.Object.wait(Native Method)
    at bitronix.tm.resource.common.XAPool.getConnectionHandle(XAPool.java:150)
    - locked <0x0000000092daa138> (a bitronix.tm.resource.common.XAPool)
    at bitronix.tm.resource.common.XAPool.getConnectionHandle(XAPool.java:91)
    - locked <0x0000000092daa138> (a bitronix.tm.resource.common.XAPool)

"3" daemon prio=10 tid=0x00002b5bf8025000 nid=0x4283 waiting for monitor entry [0x00002b5aaef71000]
   java.lang.Thread.State: BLOCKED (on object monitor)
    at java.lang.Object.wait(Native Method)
    at bitronix.tm.resource.common.XAPool.getConnectionHandle(XAPool.java:150)
    - locked <0x0000000092daa138> (a bitronix.tm.resource.common.XAPool)
    at bitronix.tm.resource.common.XAPool.getConnectionHandle(XAPool.java:91)
    - locked <0x0000000092daa138> (a bitronix.tm.resource.common.XAPool)

"0" daemon prio=10 tid=0x00002b5bf8006000 nid=0xfbb1 waiting for monitor entry [0x00002b5aae365000]
   java.lang.Thread.State: BLOCKED (on object monitor)
    at oracle.jdbc.driver.OracleStatement.close(OracleStatement.java:1563)
    - waiting to lock <0x00000000c2976880> (a oracle.jdbc.driver.T4CConnection)
    at oracle.jdbc.driver.OracleStatementWrapper.close(OracleStatementWrapper.java:94)
    at oracle.jdbc.driver.OraclePreparedStatementWrapper.close(OraclePreparedStatementWrapper.java:80)
    at bitronix.tm.resource.jdbc.JdbcPooledConnection\\\\\$1.onEviction(JdbcPooledConnection.java:95)
    at bitronix.tm.resource.jdbc.LruStatementCache.fireEvictionEvent(LruStatementCache.java:205)
    at bitronix.tm.resource.jdbc.LruStatementCache.clear(LruStatementCache.java:169)
    - locked <0x00000000c2b3bd50> (a java.util.LinkedHashMap)
    at bitronix.tm.resource.jdbc.JdbcPooledConnection.close(JdbcPooledConnection.java:172)
    at bitronix.tm.resource.common.XAPool.getConnectionHandle(XAPool.java:139)
    - locked <0x0000000092daa138> (a bitronix.tm.resource.common.XAPool)
    at bitronix.tm.resource.common.XAPool.getConnectionHandle(XAPool.java:91)
    - locked <0x0000000092daa138> (a bitronix.tm.resource.common.XAPool)

"4" daemon prio=10 tid=0x00002b5c54001000 nid=0x4b8d runnable [0x00002b5aae66a000]
   java.lang.Thread.State: RUNNABLE
    at java.net.SocketInputStream.socketRead0(Native Method)
    at java.net.SocketInputStream.read(SocketInputStream.java:150)
    at java.net.SocketInputStream.read(SocketInputStream.java:121)
    at oracle.net.ns.Packet.receive(Packet.java:300)
    at oracle.net.ns.DataPacket.receive(DataPacket.java:106)
    at oracle.net.ns.NetInputStream.getNextPacket(NetInputStream.java:315)
    at oracle.net.ns.NetInputStream.read(NetInputStream.java:260)
    at oracle.net.ns.NetInputStream.read(NetInputStream.java:185)
    at oracle.net.ns.NetInputStream.read(NetInputStream.java:102)
    at oracle.jdbc.driver.T4CSocketInputStreamWrapper.readNextPacket(T4CSocketInputStreamWrapper.java:124)
    at oracle.jdbc.driver.T4CSocketInputStreamWrapper.read(T4CSocketInputStreamWrapper.java:80)
    at oracle.jdbc.driver.T4CMAREngine.unmarshalUB1(T4CMAREngine.java:1137)
    at oracle.jdbc.driver.T4CTTIfun.receive(T4CTTIfun.java:290)
    at oracle.jdbc.driver.T4CTTIfun.doRPC(T4CTTIfun.java:192)
    at oracle.jdbc.driver.T4CTTIoping.doOPING(T4CTTIoping.java:52)
    at oracle.jdbc.driver.T4CConnection.doPingDatabase(T4CConnection.java:4008)
    - locked <0x00000000c2976880> (a oracle.jdbc.driver.T4CConnection)
    at oracle.jdbc.driver.PhysicalConnection\$3.run(PhysicalConnection.java:7868)
    at java.lang.Thread.run(Thread.java:722)
    """

    def threads = ThreadDumpParser.parse(text)
    assert threads.size() == 5
    for (i in 0..4) {
      assert threads[i].name == String.valueOf(i)
    }

    assert threads[4].isAwaitedBy(threads[0])
    assert threads[0].isAwaitedBy(threads[1])
  }


}
