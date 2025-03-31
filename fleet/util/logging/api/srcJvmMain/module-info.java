// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
module fleet.util.logging.api {
  exports fleet.util.logging;

  requires transitive kotlin.stdlib;
  requires transitive kotlinx.coroutines.core;
  requires static fleet.util.multiplatform;

  uses fleet.util.logging.KLoggerFactory;
}