// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.ide.plugins

import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.platform.plugins.parser.impl.ScopedElementsContainer
import com.intellij.platform.plugins.parser.impl.elements.*
import com.intellij.platform.plugins.parser.impl.elements.OS.*
import com.intellij.platform.plugins.parser.impl.elements.PreloadMode.*
import com.intellij.util.messages.ListenerDescriptor
import org.jetbrains.annotations.ApiStatus
import com.intellij.platform.plugins.parser.impl.elements.ClientKind as ClientKindElement
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule as ModuleLoadingRuleElement

fun ScopedElementsContainer.convert(): ContainerDescriptor = ContainerDescriptor(
  services = services.map { it.convert() },
  components = components.map { it.convert() },
  listeners = listeners.map { it.convert() },
  extensionPoints = extensionPoints.map { it.convert() },
)

fun ClientKindElement.convert(): ClientKind = when (this) {
  ClientKindElement.LOCAL -> ClientKind.LOCAL
  ClientKindElement.FRONTEND -> ClientKind.FRONTEND
  ClientKindElement.CONTROLLER -> ClientKind.CONTROLLER
  ClientKindElement.GUEST -> ClientKind.GUEST
  ClientKindElement.OWNER -> ClientKind.OWNER
  ClientKindElement.REMOTE -> ClientKind.REMOTE
  ClientKindElement.ALL -> ClientKind.ALL
}

fun ComponentElement.convert(): ComponentConfig = ComponentConfig(
  interfaceClass,
  implementationClass,
  headlessImplementationClass,
  loadForDefaultProject,
  os?.convert(),
  overrides,
  options.takeIf { it.isNotEmpty() }
)

fun ExtensionPointElement.convert(): ExtensionPointDescriptor = ExtensionPointDescriptor(
  name = qualifiedName ?: name!!,
  isNameQualified = qualifiedName != null,
  className = `interface` ?: beanClass!!,
  isBean = `interface` == null,
  hasAttributes = hasAttributes,
  isDynamic = isDynamic,
)

fun ListenerElement.convert(): ListenerDescriptor = ListenerDescriptor(
  os?.convert(),
  listenerClassName,
  topicClassName,
  activeInTestMode,
  activeInHeadlessMode,
)

fun OS.convert(): ExtensionDescriptor.Os = when (this) {
  MAC -> ExtensionDescriptor.Os.mac
  LINUX -> ExtensionDescriptor.Os.linux
  WINDOWS -> ExtensionDescriptor.Os.windows
  UNIX -> ExtensionDescriptor.Os.unix
  FREEBSD -> ExtensionDescriptor.Os.freebsd
}

fun ServiceElement.convert(): ServiceDescriptor = ServiceDescriptor(
  serviceInterface,
  serviceImplementation,
  testServiceImplementation,
  headlessImplementation,
  overrides,
  configurationSchemaKey,
  preload.convert(),
  client?.convert(),
  os?.convert()
)

fun PreloadMode.convert(): ServiceDescriptor.PreloadMode = when (this) {
  TRUE -> ServiceDescriptor.PreloadMode.TRUE
  FALSE -> ServiceDescriptor.PreloadMode.FALSE
  AWAIT -> ServiceDescriptor.PreloadMode.AWAIT
  NOT_HEADLESS -> ServiceDescriptor.PreloadMode.NOT_HEADLESS
  NOT_LIGHT_EDIT -> ServiceDescriptor.PreloadMode.NOT_LIGHT_EDIT
}

fun ModuleLoadingRuleElement.convert(): ModuleLoadingRule = when (this) {
  ModuleLoadingRuleElement.REQUIRED -> ModuleLoadingRule.REQUIRED
  ModuleLoadingRuleElement.EMBEDDED -> ModuleLoadingRule.EMBEDDED
  ModuleLoadingRuleElement.OPTIONAL -> ModuleLoadingRule.OPTIONAL
  ModuleLoadingRuleElement.ON_DEMAND -> ModuleLoadingRule.ON_DEMAND
  else -> throw IllegalArgumentException("Unknown module loading rule: ${this}")
}