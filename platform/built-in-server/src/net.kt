package com.intellij.util.net

import java.net.InetAddress
import java.net.InetSocketAddress

@JvmOverloads
fun loopbackSocketAddress(port: Int = -1) = InetSocketAddress(InetAddress.getLoopbackAddress(), if (port == -1) NetUtils.findAvailableSocketPort() else port)