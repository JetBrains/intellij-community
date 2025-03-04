// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser

import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.parser.elements.ComponentElement
import com.intellij.ide.plugins.parser.elements.ExtensionPointElement
import com.intellij.ide.plugins.parser.elements.ListenerElement
import com.intellij.ide.plugins.parser.elements.OS
import com.intellij.ide.plugins.parser.elements.OS.FREEBSD
import com.intellij.ide.plugins.parser.elements.OS.LINUX
import com.intellij.ide.plugins.parser.elements.OS.MAC
import com.intellij.ide.plugins.parser.elements.OS.UNIX
import com.intellij.ide.plugins.parser.elements.OS.WINDOWS
import com.intellij.ide.plugins.parser.elements.ServiceElement
import com.intellij.ide.plugins.parser.elements.PreloadMode
import com.intellij.ide.plugins.parser.elements.PreloadMode.AWAIT
import com.intellij.ide.plugins.parser.elements.PreloadMode.FALSE
import com.intellij.ide.plugins.parser.elements.PreloadMode.NOT_HEADLESS
import com.intellij.ide.plugins.parser.elements.PreloadMode.NOT_LIGHT_EDIT
import com.intellij.ide.plugins.parser.elements.PreloadMode.TRUE
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.ide.plugins.parser.elements.ClientKind as ClientKindElement

fun ScopedElementsContainer.convert(): ContainerDescriptor = ContainerDescriptor(
  services = services.map { it.convert() },
  components = components.map { it.convert() } ,
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