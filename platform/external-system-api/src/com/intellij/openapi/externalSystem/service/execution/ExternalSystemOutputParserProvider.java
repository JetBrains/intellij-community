// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.output.BuildOutputParser;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Provides build output parsers for external system task.
 *
 * @see BuildOutputParser
 * @see com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
 */
public interface ExternalSystemOutputParserProvider {
  ExtensionPointName<ExternalSystemOutputParserProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.externalSystemOutputParserProvider");

  /**
   * External system id is needed to find applicable parsers provider for external system task.
   */
  ProjectSystemId getExternalSystemId();

  /**
   * Creates build output parsers.
   *
   * @param taskId is id of build task that output should be patched by these parsers.
   * @return parsers for messages from text and build events.
   */
  List<BuildOutputParser> getBuildOutputParsers(@NotNull ExternalSystemTaskId taskId);
}
