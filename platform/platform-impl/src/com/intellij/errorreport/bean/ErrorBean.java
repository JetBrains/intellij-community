/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.errorreport.bean;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.List;

/**
 * @author stathik
 * @since May 5, 2003
 */
public class ErrorBean {
  private final String lastAction;
  private String pluginName;
  private String pluginVersion;
  private Integer previousException;
  private String message;
  private String stackTrace;
  private String description;
  private Integer assigneeId;
  private List<Attachment> attachments = Collections.emptyList();

  public ErrorBean(Throwable throwable, String lastAction) {
    if (throwable != null) {
      message = throwable.getMessage();
      stackTrace = ExceptionUtil.getThrowableText(throwable);
    }
    this.lastAction = lastAction;
  }

  public Integer getPreviousException() {
    return previousException;
  }

  public void setPreviousException(Integer previousException) {
    this.previousException = previousException;
  }

  public String getPluginName() {
    return pluginName;
  }

  public void setPluginName(String pluginName) {
    this.pluginName = pluginName;
  }

  public String getPluginVersion() {
    return pluginVersion;
  }

  public void setPluginVersion(String pluginVersion) {
    this.pluginVersion = pluginVersion;
  }

  public String getLastAction() {
    return lastAction;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(@NonNls String description) {
    this.description = description;
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public void setAttachments(List<Attachment> attachments) {
    this.attachments = attachments;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public Integer getAssigneeId() {
    return assigneeId;
  }

  public void setAssigneeId(Integer assigneeId) {
    this.assigneeId = assigneeId;
  }
}
