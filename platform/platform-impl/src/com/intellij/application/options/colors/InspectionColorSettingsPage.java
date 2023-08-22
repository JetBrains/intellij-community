// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.application.options.colors;

/**
 * Marker interface for pages capable of editing the colors for inspection problems.
 * The first page implementing this interface gets the inspection attribute descriptors
 * added to its attribute descriptors list automatically. 
 */
public interface InspectionColorSettingsPage {
}
