// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.ApiStatus;

/** @deprecated override {@link ApplicationStarter} instead */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
public abstract class ApplicationStarterEx implements ApplicationStarter { }