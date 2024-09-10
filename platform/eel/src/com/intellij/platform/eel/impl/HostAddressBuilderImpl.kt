// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.platform.eel.EelTunnelsApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class HostAddressBuilderImpl(
  var port: UShort = 0u,
  var hostname: String = "localhost",
  var protocolPreference: IpProtocolPreferenceImpl = IpProtocolPreferenceImpl.USE_SYSTEM_DEFAULT,
  var timeout: Duration = 10.seconds,
) : EelTunnelsApi.HostAddress.Builder, EelTunnelsApi.HostAddress {
  override fun hostname(hostname: String): EelTunnelsApi.HostAddress.Builder = apply { this.hostname = hostname }

  override fun preferIPv4(): EelTunnelsApi.HostAddress.Builder = apply { this.protocolPreference = IpProtocolPreferenceImpl.PREFER_V4 }

  override fun preferIPv6(): EelTunnelsApi.HostAddress.Builder = apply { this.protocolPreference = IpProtocolPreferenceImpl.PREFER_V6 }

  override fun preferOSDefault(): EelTunnelsApi.HostAddress.Builder = this.apply { this.protocolPreference = IpProtocolPreferenceImpl.USE_SYSTEM_DEFAULT }

  override fun connectionTimeout(timeout: Duration): EelTunnelsApi.HostAddress.Builder = apply { this.timeout = timeout }

  override fun build(): EelTunnelsApi.HostAddress = this.copy()
}