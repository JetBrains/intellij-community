// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

/**
 * @deprecated Please use {@link ConfigurableEP#nonDefaultProject} instead.
 *
 * Marker interface that should be implemented by project level configurables that do not apply
 * to the default project.
 */
@Deprecated(forRemoval = true)
public interface NonDefaultProjectConfigurable {
}