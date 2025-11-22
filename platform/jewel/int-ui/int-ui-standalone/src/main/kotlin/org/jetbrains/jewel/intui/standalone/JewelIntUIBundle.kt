// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.intui.standalone.bundle.DynamicBundle

/**
 * Loads the JewelBundle messages with required messages by Jewel.
 *
 * The counterpart of this in the LaF is the [com.intellij.ide.IdeBundle], which provides the necessary strings.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public object JewelIntUIBundle : DynamicBundle(JewelIntUIBundle::class.java, "messages.JewelIntUIBundle")
