/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.testFramework.PlatformTestUtil
import groovy.transform.CompileStatic
import junit.framework.TestCase
@CompileStatic
class ThreadDumpParserTest extends TestCase {
  void "test waiting threads are not locking"() {
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

  void "test YourKit format"() {
    def text = """
Stacks at 2017-05-03 01:07:25 PM (uptime 4h 21m 28s) Threads shown: 38 of 46

ApplicationImpl pooled thread 228 [WAITING]
java.lang.Thread.run() Thread.java:745

ApplicationImpl pooled thread 234 [WAITING] [DAEMON]
java.lang.Thread.run() Thread.java:745

ApplicationImpl pooled thread 6 [RUNNABLE, IN_NATIVE]
java.net.DatagramSocket.receive(DatagramPacket) DatagramSocket.java:812
com.intellij.a.f.a.c.a() c.java:60
com.intellij.a.f.a.d.run() d.java:20
java.lang.Thread.run() Thread.java:745
"""
    def threads = ThreadDumpParser.parse(text)
    assert threads.collect { it.name } == ['ApplicationImpl pooled thread 228', 'ApplicationImpl pooled thread 234', 'ApplicationImpl pooled thread 6']
    assert threads.collect { it.state } == ['WAITING', 'WAITING', 'RUNNABLE, IN_NATIVE']
    assert threads.collect { it.daemon } == [false, true, false]
    assert threads.collect { it.stackTrace.readLines().size() } == [2, 2, 5] // thread name is included into stack trace
  }
  
  void "test YourKit 2017 format"() {
    def text = '''
Stacks at 2017-06-08 12:56:31 PM. Uptime is 23m 47s 200ms.

thread 23 State: RUNNABLE CPU usage on sample: 968ms
com.intellij.openapi.util.io.win32.IdeaWin32.listChildren0(String) IdeaWin32.java (native)
com.intellij.openapi.util.io.win32.IdeaWin32.listChildren(String) IdeaWin32.java:136
com.intellij.openapi.vfs.impl.win32.Win32FsCache.list(VirtualFile) Win32FsCache.java:58
com.intellij.openapi.vfs.impl.win32.Win32LocalFileSystem.list(VirtualFile) Win32LocalFileSystem.java:57
com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker.partialDirRefresh(NewVirtualFileSystem, TObjectHashingStrategy, VirtualDirectoryImpl) RefreshWorker.java:272
com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker.processQueue(NewVirtualFileSystem, PersistentFS) RefreshWorker.java:124
com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker.scan() RefreshWorker.java:85
com.intellij.openapi.vfs.newvfs.RefreshSessionImpl.scan() RefreshSessionImpl.java:147
com.intellij.openapi.vfs.newvfs.RefreshQueueImpl.doScan(RefreshSessionImpl) RefreshQueueImpl.java:91
com.intellij.openapi.vfs.newvfs.RefreshQueueImpl.lambda$queueSession$1(RefreshSessionImpl, TransactionId, ModalityState) RefreshQueueImpl.java:74
com.intellij.openapi.vfs.newvfs.RefreshQueueImpl$$Lambda$242.run()
java.util.concurrent.Executors$RunnableAdapter.call() Executors.java:511
java.util.concurrent.FutureTask.run() FutureTask.java:266
com.intellij.util.concurrency.BoundedTaskExecutor$2.run() BoundedTaskExecutor.java:212
java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor$Worker) ThreadPoolExecutor.java:1142
java.util.concurrent.ThreadPoolExecutor$Worker.run() ThreadPoolExecutor.java:617
java.lang.Thread.run() Thread.java:745

thread 24 State: WAITING CPU usage on sample: 0ms
sun.misc.Unsafe.park(boolean, long) Unsafe.java (native)
java.util.concurrent.locks.LockSupport.parkNanos(Object, long) LockSupport.java:215
java.util.concurrent.SynchronousQueue$TransferStack.awaitFulfill(SynchronousQueue$TransferStack$SNode, boolean, long) SynchronousQueue.java:460
java.util.concurrent.SynchronousQueue$TransferStack.transfer(Object, boolean, long) SynchronousQueue.java:362
java.util.concurrent.SynchronousQueue.poll(long, TimeUnit) SynchronousQueue.java:941
java.util.concurrent.ThreadPoolExecutor.getTask() ThreadPoolExecutor.java:1066
java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor$Worker) ThreadPoolExecutor.java:1127
java.util.concurrent.ThreadPoolExecutor$Worker.run() ThreadPoolExecutor.java:617
java.lang.Thread.run() Thread.java:745

thread 25 State: WAITING CPU usage on sample: 0ms
sun.misc.Unsafe.park(boolean, long) Unsafe.java (native)
java.util.concurrent.locks.LockSupport.parkNanos(Object, long) LockSupport.java:215
java.util.concurrent.SynchronousQueue$TransferStack.awaitFulfill(SynchronousQueue$TransferStack$SNode, boolean, long) SynchronousQueue.java:460
java.util.concurrent.SynchronousQueue$TransferStack.transfer(Object, boolean, long) SynchronousQueue.java:362
java.util.concurrent.SynchronousQueue.poll(long, TimeUnit) SynchronousQueue.java:941

Swing-Shell [DAEMON] State: WAITING CPU usage on sample: 0ms
sun.misc.Unsafe.park(boolean, long) Unsafe.java (native)
java.util.concurrent.locks.LockSupport.park(Object) LockSupport.java:175
''' 
    def threads = ThreadDumpParser.parse(text)
    assert threads.collect { it.name } == ['thread 23', 'thread 24', 'thread 25', 'Swing-Shell']
    assert threads.collect { it.daemon } == [false, false, false, true]
  }

  void "test YourKit 2018 format"() {
    def text = '''
ApplicationImpl pooled thread 81  Waiting CPU usage on sample: 0ms
  sun.misc.Unsafe.park(boolean, long) Unsafe.java (native)
  java.util.concurrent.locks.LockSupport.parkNanos(Object, long) LockSupport.java:215
  java.util.concurrent.SynchronousQueue$TransferStack.awaitFulfill(SynchronousQueue$TransferStack$SNode, boolean, long) SynchronousQueue.java:460
  java.util.concurrent.SynchronousQueue$TransferStack.transfer(Object, boolean, long) SynchronousQueue.java:362
  java.util.concurrent.SynchronousQueue.poll(long, TimeUnit) SynchronousQueue.java:941
  java.util.concurrent.ThreadPoolExecutor.getTask() ThreadPoolExecutor.java:1066
  java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor$Worker) ThreadPoolExecutor.java:1127
  java.util.concurrent.ThreadPoolExecutor$Worker.run() ThreadPoolExecutor.java:617
  java.lang.Thread.run() Thread.java:745

ApplicationImpl pooled thread 82  Waiting CPU usage on sample: 0ms
  sun.misc.Unsafe.park(boolean, long) Unsafe.java (native)
  java.util.concurrent.locks.LockSupport.parkNanos(Object, long) LockSupport.java:215
  java.util.concurrent.SynchronousQueue$TransferStack.awaitFulfill(SynchronousQueue$TransferStack$SNode, boolean, long) SynchronousQueue.java:460
  java.util.concurrent.SynchronousQueue$TransferStack.transfer(Object, boolean, long) SynchronousQueue.java:362
  java.util.concurrent.SynchronousQueue.poll(long, TimeUnit) SynchronousQueue.java:941
  java.util.concurrent.ThreadPoolExecutor.getTask() ThreadPoolExecutor.java:1066
  java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor$Worker) ThreadPoolExecutor.java:1127
  java.util.concurrent.ThreadPoolExecutor$Worker.run() ThreadPoolExecutor.java:617
  java.lang.Thread.run() Thread.java:745

ApplicationImpl pooled thread 90  Waiting CPU usage on sample: 0ms
  sun.misc.Unsafe.park(boolean, long) Unsafe.java (native)
  java.util.concurrent.locks.LockSupport.parkNanos(Object, long) LockSupport.java:215
  java.util.concurrent.SynchronousQueue$TransferStack.awaitFulfill(SynchronousQueue$TransferStack$SNode, boolean, long) SynchronousQueue.java:460
  java.util.concurrent.SynchronousQueue$TransferStack.transfer(Object, boolean, long) SynchronousQueue.java:362
  java.util.concurrent.SynchronousQueue.poll(long, TimeUnit) SynchronousQueue.java:941
  java.util.concurrent.ThreadPoolExecutor.getTask() ThreadPoolExecutor.java:1066
  java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor$Worker) ThreadPoolExecutor.java:1127
  java.util.concurrent.ThreadPoolExecutor$Worker.run() ThreadPoolExecutor.java:617
  java.lang.Thread.run() Thread.java:745
'''
    def threads = ThreadDumpParser.parse(text)
    assert threads.collect { it.name } == ['ApplicationImpl pooled thread 81', 'ApplicationImpl pooled thread 82', 'ApplicationImpl pooled thread 90']
  }

  void "test log is not a thread dump"() {
    def threads = ThreadDumpParser.parse("""\
2017-05-11 15:37:22,031 [100664612]   INFO - krasa.visualvm.VisualVMContext - saving context: VisualVMContext{appId=322303893654749} 
2017-05-11 15:53:08,057 [101610638]   INFO - krasa.visualvm.VisualVMContext - saving context: VisualVMContext{appId=323249981117880} 
2017-05-11 16:37:01,448 [104244029]   INFO - krasa.visualvm.VisualVMContext - saving context: VisualVMContext{appId=325883542423831} 
2017-05-11 16:45:50,763 [104773344]   INFO - ij.compiler.impl.CompileDriver - COMPILATION STARTED (BUILD PROCESS) 
2017-05-11 16:45:50,769 [104773350]   INFO - j.compiler.server.BuildManager - Using preloaded build process to compile /Users/ycx622/git/ropeengine 
""")
    assert threads.size() <= 1
  }

  void "test trace with trailing jar names is not a thread dump"() {
    def threads = ThreadDumpParser.parse('''
Jun 27 02:58:45.222 WARN  [][Atomikos:2]  Error while retrieving xids from resource - will retry later... (com.atomikos.recovery.xa.XaResourceRecoveryManager:40) 
javax.transaction.xa.XAException
\tat oracle.jdbc.xa.OracleXAResource.recover(OracleXAResource.java:730) ~[ojdbc-12.1.0.2.jar.8754835619381084897.jar:12.1.0.2.0]
\tat com.atomikos.datasource.xa.RecoveryScan.recoverXids(RecoveryScan.java:32) ~[transactions-jta-4.0.4.jar.3905881887605215235.jar:?]
\tat com.atomikos.recovery.xa.XaResourceRecoveryManager.retrievePreparedXidsFromXaResource(XaResourceRecoveryManager.java:158) [transactions-jta-4.0.4.jar.3905881887605215235.jar:?]
\tat com.atomikos.recovery.xa.XaResourceRecoveryManager.recover(XaResourceRecoveryManager.java:67) [transactions-jta-4.0.4.jar.3905881887605215235.jar:?]
\tat com.atomikos.datasource.xa.XATransactionalResource.recover(XATransactionalResource.java:451) [transactions-jta-4.0.4.jar.3905881887605215235.jar:?]
\tat com.atomikos.icatch.imp.TransactionServiceImp.performRecovery(TransactionServiceImp.java:490) [transactions-4.0.4.jar.3144743539643303549.jar:?]
\tat com.atomikos.icatch.imp.TransactionServiceImp.access$000(TransactionServiceImp.java:56) [transactions-4.0.4.jar.3144743539643303549.jar:?]
\tat com.atomikos.icatch.imp.TransactionServiceImp$1.alarm(TransactionServiceImp.java:471) [transactions-4.0.4.jar.3144743539643303549.jar:?]
\tat com.atomikos.timing.PooledAlarmTimer.notifyListeners(PooledAlarmTimer.java:95) [atomikos-util-4.0.4.jar.3934559012129936607.jar:?]
\tat com.atomikos.timing.PooledAlarmTimer.run(PooledAlarmTimer.java:82) [atomikos-util-4.0.4.jar.3934559012129936607.jar:?]
\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142) [?:1.8.0_131]
\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617) [?:1.8.0_131]
\tat java.lang.Thread.run(Thread.java:748) [?:1.8.0_131]
''')
    assert threads.size() <= 1
  }

  void "test yourkit threads with indented names"() { 
    String text = '''


 Stacks at 2017-07-13 07:15:35 AM (uptime 1d 2h 59m 6 sec) Threads shown: 3 of 55


 ApplicationImpl pooled thread 1007 [RUNNABLE] [DAEMON]
org.iq80.snappy.SnappyDecompressor.decompressAllTags(byte[], int, int, byte[], int) SnappyDecompressor.java:182
org.iq80.snappy.SnappyDecompressor.uncompress(byte[], int, int) SnappyDecompressor.java:47
org.iq80.snappy.Snappy.uncompress(byte[], int, int) Snappy.java:85
com.intellij.util.CompressionUtil.readCompressedWithoutOriginalBufferLength(DataInput) CompressionUtil.java:111


 AWT-EventQueue-0 2017.3#IC-173.SNAPSHOT IDEA, eap:true, os:Linux 3.13.0-117-generic, java-version:JetBrains s.r.o 1.8.0_152-release-867-b1 [WAITING]
java.util.concurrent.locks.LockSupport.parkNanos(Object, long) LockSupport.java:215
com.intellij.openapi.application.impl.ReadMostlyRWLock.writeLock() ReadMostlyRWLock.java:192
com.intellij.openapi.application.impl.ApplicationImpl.startWrite(Class) ApplicationImpl.java:1219
com.intellij.openapi.application.impl.ApplicationImpl.runWriteAction(Runnable) ApplicationImpl.java:1027


'''
    def threads = ThreadDumpParser.parse(text)
    assert threads.collect { it.name } == ['ApplicationImpl pooled thread 1007', 
                                           'AWT-EventQueue-0 2017.3#IC-173.SNAPSHOT IDEA, eap:true, os:Linux 3.13.0-117-generic, java-version:JetBrains s.r.o 1.8.0_152-release-867-b1']
  }

  void "test jstack -F format"() {
    String text = '''
Attaching to process ID 7370, please wait...
Debugger attached successfully.
Server compiler detected.
JVM version is 25.161-b12
Deadlock Detection:

No deadlocks found.

Thread 8393: (state = BLOCKED)
 - sun.misc.Unsafe.park(boolean, long) @bci=0 (Compiled frame; information may be imprecise)
 - java.util.concurrent.locks.LockSupport.parkNanos(java.lang.Object, long) @bci=63, line=215 (Compiled frame)
 - java.util.concurrent.SynchronousQueue$TransferStack.awaitFulfill(java.util.concurrent.SynchronousQueue$TransferStack$SNode, boolean, long) @bci=283, line=460 (Compiled frame)
 - java.util.concurrent.SynchronousQueue$TransferStack.transfer(java.lang.Object, boolean, long) @bci=175, line=362 (Compiled frame)
 - java.util.concurrent.SynchronousQueue.poll(long, java.util.concurrent.TimeUnit) @bci=49, line=941 (Compiled frame)
 - java.util.concurrent.ThreadPoolExecutor.getTask() @bci=247, line=1073 (Compiled frame)
 - java.util.concurrent.ThreadPoolExecutor.runWorker(java.util.concurrent.ThreadPoolExecutor$Worker) @bci=74, line=1134 (Interpreted frame)
 - java.util.concurrent.ThreadPoolExecutor$Worker.run() @bci=28, line=624 (Interpreted frame)
 - java.lang.Thread.run() @bci=34, line=748 (Interpreted frame)


Thread 7399: (state = IN_NATIVE)
 - sun.awt.X11.XToolkit.$$YJP$$waitForEvents(long) @bci=0 (Compiled frame; information may be imprecise)
 - sun.awt.X11.XToolkit.waitForEvents(long) @bci=14 (Compiled frame)
 - sun.awt.X11.XToolkit.run(boolean) @bci=298, line=568 (Interpreted frame)
 - sun.awt.X11.XToolkit.run() @bci=38, line=532 (Interpreted frame)
 - java.lang.Thread.run() @bci=34, line=748 (Interpreted frame)


Thread 7381: (state = BLOCKED)
'''
    def threads = ThreadDumpParser.parse(text)
    assert threads.collect { it.name } == ['8393', '7399', '7381']
    assert threads[0].stackTrace.contains('ThreadPoolExecutor')
    assert threads[1].stackTrace.contains('XToolkit')
    assert threads[2].emptyStackTrace
    
  }

  void 'test jdk 11 format'() {
    String text = '''
"main" #1 prio=5 os_prio=0 cpu=171.88ms elapsed=101.93s tid=0x0000026392746000 nid=0x3bc4 runnable  [0x000000d7ed0fe000]
   java.lang.Thread.State: RUNNABLE
\tat java.io.FileInputStream.readBytes(java.base@11.0.2/Native Method)
\tat java.io.FileInputStream.read(java.base@11.0.2/FileInputStream.java:279)
\tat java.io.BufferedInputStream.fill(java.base@11.0.2/BufferedInputStream.java:252)
\tat java.io.BufferedInputStream.read(java.base@11.0.2/BufferedInputStream.java:271)
\t- locked <0x000000062181e298> (a java.io.BufferedInputStream)
\tat my.Endless.main(Endless.java:13)
\tat jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(java.base@11.0.2/Native Method)
\tat jdk.internal.reflect.NativeMethodAccessorImpl.invoke(java.base@11.0.2/NativeMethodAccessorImpl.java:62)
\tat jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(java.base@11.0.2/DelegatingMethodAccessorImpl.java:43)
\tat java.lang.reflect.Method.invoke(java.base@11.0.2/Method.java:566)
\tat com.intellij.rt.execution.application.AppMainV2.main(AppMainV2.java:131)

"Reference Handler" #2 daemon prio=10 os_prio=2 cpu=0.00ms elapsed=101.90s tid=0x00000263c01b5000 nid=0x34e4 waiting on condition  [0x000000d7ed7fe000]
   java.lang.Thread.State: RUNNABLE
\tat java.lang.ref.Reference.waitForReferencePendingList(java.base@11.0.2/Native Method)
\tat java.lang.ref.Reference.processPendingReferences(java.base@11.0.2/Reference.java:241)
\tat java.lang.ref.Reference$ReferenceHandler.run(java.base@11.0.2/Reference.java:213)

"Finalizer" #3 daemon prio=8 os_prio=1 cpu=0.00ms elapsed=101.90s tid=0x00000263c01df000 nid=0x3ae4 in Object.wait()  [0x000000d7ed8ff000]
   java.lang.Thread.State: WAITING (on object monitor)
\tat java.lang.Object.wait(java.base@11.0.2/Native Method)
\t- waiting on <0x0000000621808f10> (a java.lang.ref.ReferenceQueue$Lock)
\tat java.lang.ref.ReferenceQueue.remove(java.base@11.0.2/ReferenceQueue.java:155)
\t- waiting to re-lock in wait() <0x0000000621808f10> (a java.lang.ref.ReferenceQueue$Lock)
\tat java.lang.ref.ReferenceQueue.remove(java.base@11.0.2/ReferenceQueue.java:176)
\tat java.lang.ref.Finalizer$FinalizerThread.run(java.base@11.0.2/Finalizer.java:170)

"Signal Dispatcher" #4 daemon prio=9 os_prio=2 cpu=0.00ms elapsed=101.89s tid=0x00000263c0236800 nid=0x3cac runnable  [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE
'''

    def threads = ThreadDumpParser.parse(text)
    assert threads.size() == 4
  }

  @CompileStatic
  void "test very long line parsing performance"() {
    def spaces = ' ' * 1_000_000
    def letters = 'a' * 1_000_000
    PlatformTestUtil.startPerformanceTest('parsing spaces', 100, {
      def threads = ThreadDumpParser.parse(spaces)
      assert threads.empty
    }).assertTiming()
    PlatformTestUtil.startPerformanceTest('parsing letters', 100, {
      def threads = ThreadDumpParser.parse(letters)
      assert threads.empty
    }).assertTiming()
  }

}
