// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import fleet.util.logging.FleetSlf4jServiceProvider;
import org.slf4j.spi.SLF4JServiceProvider;

module fleet.util.logging.api {
  exports fleet.util.logging;
  exports fleet.util.logging.slf4j;

  requires kotlin.stdlib;
  requires transitive org.slf4j;
  requires transitive kotlinx.coroutines.slf4j;

  provides SLF4JServiceProvider with FleetSlf4jServiceProvider;
  uses SLF4JServiceProvider;
  uses fleet.util.logging.KLoggerFactory;
}