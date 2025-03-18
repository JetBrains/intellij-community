// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
module fleet.util.logging.api {
  exports fleet.util.logging;

  requires kotlin.stdlib;
  requires kotlinx.coroutines.core;

  uses fleet.util.logging.KLoggerFactory;
}