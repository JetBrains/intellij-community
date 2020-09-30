// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.util.NlsContexts.DetailedDescription

/**
 * Allows plugins to add additional details, which we want to see in Help -> About popup. The implementation of this interface
 * must be registered as an extension of 'com.intellij.aboutInfoProvider' extension point.
 */
interface AboutPopupDescriptionProvider  {
    /**
     * Return additional info which should be shown in About popup.
     */
    fun getDescription() : @DetailedDescription String?
}