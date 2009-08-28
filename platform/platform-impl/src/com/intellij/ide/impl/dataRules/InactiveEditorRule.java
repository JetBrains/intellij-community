/*
 * @author max
 */
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import org.jetbrains.annotations.Nullable;

public class InactiveEditorRule implements GetDataRule {
  @Nullable
  public Object getData(final DataProvider dataProvider) {
    return dataProvider.getData(DataConstants.EDITOR);
  }
}