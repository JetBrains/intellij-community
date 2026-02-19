// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starters.shared

import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.AbstractObservableProperty
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.projectRoots.Sdk

@Deprecated("Use the ProjectWizardJdkIntent property instead")
class SdkPropertyBridge(
  private val propertyGraph: PropertyGraph,
  private val sdkIntentProperty: GraphProperty<ProjectWizardJdkIntent>,
) : GraphProperty<Sdk?>, AbstractObservableProperty<Sdk?>() {

  override fun set(value: Sdk?) {
    sdkIntentProperty.set(
      when (value) {
        null -> ProjectWizardJdkIntent.NoJdk
        else -> ProjectWizardJdkIntent.ExistingJdk(value)
      }
    )
  }

  override fun get(): Sdk? = when (val intent = sdkIntentProperty.get()) {
    is ProjectWizardJdkIntent.ExistingJdk -> intent.jdk
    else -> null
  }

  override fun afterChange(parentDisposable: Disposable?, listener: (Sdk?) -> Unit) {
    sdkIntentProperty.afterChange(parentDisposable) { intent ->
      when (intent) {
        is ProjectWizardJdkIntent.ExistingJdk -> listener(intent.jdk)
        else -> listener(null)
      }
    }
  }

  override fun afterChange(listener: (Sdk?) -> Unit) {
    sdkIntentProperty.afterChange { intent ->
      when (intent) {
        is ProjectWizardJdkIntent.ExistingJdk -> listener(intent.jdk)
        else -> listener(null)
      }
    }
  }

  @Deprecated("Use instead afterChange with other order of listener and disposable", replaceWith = ReplaceWith("afterChange(parentDisposable, listener)"))
  override fun afterChange(listener: (Sdk?) -> Unit, parentDisposable: Disposable) {
    afterChange(parentDisposable, listener)
  }

  //@formatter:off
  override fun dependsOn(parent: ObservableProperty<*>, update: () -> Sdk?): Unit =
    propertyGraph.dependsOn(this, parent, update = update)
  override fun dependsOn(parent: ObservableProperty<*>, deleteWhenModified: Boolean, update: () -> Sdk?): Unit =
    propertyGraph.dependsOn(this, parent, deleteWhenModified, update = update)
  override fun afterPropagation(listener: () -> Unit): Unit =
    propertyGraph.afterPropagation(listener)
  override fun afterPropagation(parentDisposable: Disposable?, listener: () -> Unit): Unit =
    propertyGraph.afterPropagation(parentDisposable, listener = listener)
  //@formatter:on
}