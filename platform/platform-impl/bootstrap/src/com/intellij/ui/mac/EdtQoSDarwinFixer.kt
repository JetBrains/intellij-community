// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import com.intellij.openapi.diagnostic.logger
import com.sun.jna.Library
import com.sun.jna.Native

/**
 * The set of QoS classes supported by macOS.
 *
 * Original location:
 * - `/Library/Developer/CommandLineTools/SDKs/Mac0SX$VERSION$.sdk/usr/include/sys/qos.h`
 * - https://github.com/apple-oss-distributions/libpthread/blob/c032e0b076700a0a47db75528a282b8d3a06531a/include/sys/qos.h#L130-L143
 */
private enum class QosClass(internal val priority: Int) {
  UserInteractive(0x21),
  UserInitiated(0x19),
  Default(0x15),
  Utility(0x11),
  Background(0x09),
  Unspecified(0x00),
}

/**
 * Bridging JNA interface for `/usr/lib/system/libsystem_pthread.dylib` library.
 */
private interface DarwinPThread : Library {
  companion object {
    val log = logger<DarwinPThread>()
    val instance: DarwinPThread by lazy {
      // Because this library is reexported by `libSystem.B.dylib` which is linked automatically by macOS,
      // we don't need to load any .dylib.
      Native.load(DarwinPThread::class.java)
    }
  }

  /**
   * Sets the QoS class for a current thread.
   *
   * @see <a href="https://github.com/apple-oss-distributions/libpthread/blob/c032e0b076700a0a47db75528a282b8d3a06531a/include/pthread/qos.h#L118-L156">Darwin libpthread documentation</a>
   */
  fun pthread_set_qos_class_self_np(qosClass: Int, relPriority: Int): Int
}

fun setUserInteractiveQosClassForCurrentThread() {
  runCatching {
    val qos = QosClass.UserInteractive
    val ret = DarwinPThread.instance.pthread_set_qos_class_self_np(qos.priority, 0)
    if (ret != 0) {
      val self = Thread.currentThread()
      DarwinPThread.log.warn("Unable to set QoS class ${qos.name} for thread #${self.id} (${self.name}) (error code: ${ret})")
    }
  }.onFailure {
    DarwinPThread.log.error(it)
  }
}