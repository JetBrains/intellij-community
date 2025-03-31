import fleet.util.logging.KLoggerFactory;
import fleet.util.logging.slf4j.FleetSlf4jServiceProvider;
import fleet.util.logging.slf4j.Slf4jKLoggerFactory;
import org.slf4j.spi.SLF4JServiceProvider;

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
module fleet.util.logging.slf4j {
  exports fleet.util.logging.slf4j;

  requires kotlin.stdlib;
  requires transitive org.slf4j;
  requires fleet.util.logging.api;

  provides KLoggerFactory with Slf4jKLoggerFactory;
  provides SLF4JServiceProvider with FleetSlf4jServiceProvider;
  uses SLF4JServiceProvider;
  uses KLoggerFactory;
}