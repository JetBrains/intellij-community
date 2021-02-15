// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search;

import com.intellij.openapi.util.NlsContexts.Label;

/**
 * @author Konstantin Bulenkov
 */
public abstract class BooleanOptionDescription extends OptionDescription {
  public BooleanOptionDescription(@Label String option, String configurableId) {
    super(option, configurableId, null, null);
  }

  public abstract boolean isOptionEnabled();

  public abstract void setOptionState(boolean enabled);

  public interface RequiresRebuild {
  }
}
