package com.intellij.ide

import com.intellij.openapi.project.Project

/**
 * Allows plugins to add additional details, which we want to see in Help -> About popup. The implementation of this interface
 * must be registered as an extension of 'com.intellij.aboutInfoProvider' extension point.
 */
interface AboutPopupDescriptionProvider  {
    /**
     * Return additional info which should be shown in About popup.
     */
    fun getDescription() : String?
}