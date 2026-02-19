// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging.slf4j

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider
import java.util.ServiceLoader

class FleetSlf4jServiceProvider : SLF4JServiceProvider {
  companion object {
    var delegate: SLF4JServiceProvider? = ServiceLoader.load(FleetSlf4jServiceProvider::class.java.module.layer,
                                                             SLF4JServiceProvider::class.java)
      .firstOrNull { it !is FleetSlf4jServiceProvider }
  }

  override fun getLoggerFactory(): ILoggerFactory {
    return delegate!!.loggerFactory
  }

  override fun getMarkerFactory(): IMarkerFactory {
    return delegate!!.markerFactory
  }

  override fun getMDCAdapter(): MDCAdapter {
    return delegate!!.mdcAdapter
  }

  override fun getRequestedApiVersion(): String {
    return delegate!!.requestedApiVersion
  }

  override fun initialize() {
    delegate!!.initialize()
  }
}