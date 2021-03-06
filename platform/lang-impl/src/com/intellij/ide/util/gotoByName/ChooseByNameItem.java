package com.intellij.ide.util.gotoByName;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * An item displayed in ListChooseByNameModel.
 *
 * @author yole
 */
public interface ChooseByNameItem {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  String getName();
  
  @Nls(capitalization = Nls.Capitalization.Sentence)
  String getDescription();
}
