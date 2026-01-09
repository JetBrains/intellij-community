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
import com.intellij.platform.plugins.parser.impl.elements.OSValue.*
import com.intellij.platform.plugins.parser.impl.elements.PreloadModeValue.*
import com.intellij.util.messages.ListenerDescriptor
import org.jetbrains.annotations.ApiStatus

fun ScopedElementsContainer.convert(): ContainerDescriptor = ContainerDescriptor(
  services = services.map { it.convert() },
  components = components.map { it.convert() },
  listeners = listeners.map { it.convert() },
  extensionPoints = extensionPoints.map { it.convert() },
)

fun ClientKindValue.convert(): ClientKind = when (this) {
  ClientKindValue.LOCAL -> ClientKind.LOCAL
  ClientKindValue.FRONTEND -> ClientKind.FRONTEND
  ClientKindValue.CONTROLLER -> ClientKind.CONTROLLER
  ClientKindValue.GUEST -> ClientKind.GUEST
  ClientKindValue.OWNER -> ClientKind.OWNER
  ClientKindValue.REMOTE -> ClientKind.REMOTE
  ClientKindValue.ALL -> ClientKind.ALL
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

fun OSValue.convert(): ExtensionDescriptor.Os = when (this) {
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
  open,
  configurationSchemaKey,
  preload.convert(),
  client?.convert(),
  os?.convert()
)

fun PreloadModeValue.convert(): ServiceDescriptor.PreloadMode = when (this) {
  TRUE -> ServiceDescriptor.PreloadMode.TRUE
  FALSE -> ServiceDescriptor.PreloadMode.FALSE
  AWAIT -> ServiceDescriptor.PreloadMode.AWAIT
  NOT_HEADLESS -> ServiceDescriptor.PreloadMode.NOT_HEADLESS
  NOT_LIGHT_EDIT -> ServiceDescriptor.PreloadMode.NOT_LIGHT_EDIT
}

fun ModuleLoadingRuleValue.convert(): ModuleLoadingRule = when (this) {
  ModuleLoadingRuleValue.REQUIRED -> ModuleLoadingRule.REQUIRED
  ModuleLoadingRuleValue.EMBEDDED -> ModuleLoadingRule.EMBEDDED
  ModuleLoadingRuleValue.OPTIONAL -> ModuleLoadingRule.OPTIONAL
  ModuleLoadingRuleValue.ON_DEMAND -> ModuleLoadingRule.ON_DEMAND
  else -> throw IllegalArgumentException("Unknown module loading rule: ${this}")
}

fun ModuleLoadingRule.asParserElement(): ModuleLoadingRuleValue = when (this) {
  ModuleLoadingRule.REQUIRED -> ModuleLoadingRuleValue.REQUIRED
  ModuleLoadingRule.EMBEDDED -> ModuleLoadingRuleValue.EMBEDDED
  ModuleLoadingRule.OPTIONAL -> ModuleLoadingRuleValue.OPTIONAL
  ModuleLoadingRule.ON_DEMAND -> ModuleLoadingRuleValue.ON_DEMAND
  else -> throw IllegalArgumentException("Unknown module loading rule: ${this}")
}

fun ModuleVisibilityValue.convert(): ModuleVisibility = when (this) {
  ModuleVisibilityValue.PRIVATE -> ModuleVisibility.PRIVATE
  ModuleVisibilityValue.INTERNAL -> ModuleVisibility.INTERNAL
  ModuleVisibilityValue.PUBLIC -> ModuleVisibility.PUBLIC
}