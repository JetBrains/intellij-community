// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.progress

import com.intellij.build.BuildDescriptor
import com.intellij.build.events.BuildEventsNls.Title
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class BuildProgressDescriptorImpl(
  private val title: @Title String,
  private val buildDescriptor: BuildDescriptor,
) : BuildProgressDescriptor {

  constructor(buildDescriptor: BuildDescriptor) :
    this(buildDescriptor.title, buildDescriptor)

  override fun getTitle(): @Title String = title

  override fun getBuildDescriptor(): BuildDescriptor = buildDescriptor
}