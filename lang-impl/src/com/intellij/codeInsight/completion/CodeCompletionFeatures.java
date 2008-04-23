/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public interface CodeCompletionFeatures {
  @NonNls String EXCLAMATION_FINISH = "editing.completion.finishByExclamation";
  @NonNls String SECOND_CLASS_NAME_COMPLETION = "editing.completion.second.classname";
  @NonNls String EDITING_COMPLETION_SMARTTYPE_GENERAL = "editing.completion.smarttype.general";
  @NonNls String EDITING_COMPLETION_BASIC = "editing.completion.basic";
  @NonNls String EDITING_COMPLETION_CLASSNAME = "editing.completion.classname";
  @NonNls String EDITING_COMPLETION_CAMEL_HUMPS = "editing.completion.camelHumps";
  @NonNls String EDITING_COMPLETION_REPLACE = "editing.completion.replace";
  @NonNls String EDITING_COMPLETION_FINISH_BY_DOT_ETC = "editing.completion.finishByDotEtc";
  @NonNls String EDITING_COMPLETION_FINISH_BY_SMART_ENTER = "editing.completion.finishBySmartEnter";
}
