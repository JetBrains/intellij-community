// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@ApiStatus.Internal
public interface FLSConfigurationInfoService {

  record FLSInfo(@NotNull String url) {}

  record AuthData(@NotNull FLSInfo config, @NotNull Map<String, String> authHeaders) {}

  sealed interface TicketAvailabilityResult permits TicketAvailabilityResult.TicketAvailable,
                                                    TicketAvailabilityResult.TicketUnavailable,
                                                    TicketAvailabilityResult.RequestFailed,
                                                    TicketAvailabilityResult.AuthRequired {
    record TicketAvailable(@Nullable String pluginsAllowed) implements TicketAvailabilityResult {}

    record TicketUnavailable(@NotNull String message) implements TicketAvailabilityResult {}

    record RequestFailed(@NotNull String errorMessage) implements TicketAvailabilityResult {}

    record AuthRequired(@Nullable String message, @Nullable String authorizationUrl, @Nullable String authorizationClientId,
                        @Nullable String codeExchangeUrl) implements TicketAvailabilityResult {}
  }

  /**
   * Provides FLS descriptors used currently by the IDE to obtain license tickets
   *
   * @return a list of descriptor structures for every FLS resource currently used
   */
  @NotNull
  Iterable<FLSInfo> getFLSConfiguration();

  /**
   * @param config the FLS descriptor obtained from {@link #getFLSConfiguration()}
   * @return a future, providing authentication data for the given FLS resource. If no authentication data is available, null is returned by the Future
   */
  @NotNull
  Future<@Nullable AuthData> lookupAuthData(@NotNull FLSInfo config);

  /**
   * @param config the FLS descriptor obtained from {@link #getFLSConfiguration()} or {@link FLSConfigurationProvider#getUrl()}
   * @return a future, providing ticket availability information for the given FLS resource
   */
  @NotNull
  CompletableFuture<@Nullable TicketAvailabilityResult> checkTicketAvailability(@NotNull FLSInfo config);

  static FLSConfigurationInfoService getInstance() {
    return FLSConfigurationInfoServiceHolder.INSTANCE;
  }
}
