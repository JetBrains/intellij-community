// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.CodeStyleBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Enumerates formatting processing states.
 */
public enum FormattingStateId {

  /**
   * Corresponds to {@link InitialInfoBuilder#buildFrom(Block, int, CompositeBlockWrapper, WrapImpl, Block)}.
   * <p/>
   * I.e. the first thing formatter does retrieval of all {@link Block code blocks} from target {@link FormattingModel model}
   * and wrapping them in order to be able to store information about modified white spaces. That processing may trigger
   * blocks construction because most of our formatting models do that lazily, i.e. they define the
   * {@link FormattingModel#getRootBlock() root block} and build its sub-blocks only on the {@link Block#getSubBlocks() first request}.
   */
  WRAPPING_BLOCKS(2, "progress.reformat.stage.wrapping.blocks"),

  /**
   * This element corresponds to the state when formatter sequentially processes {@link AbstractBlockWrapper wrapped code blocks}
   * and modifies their {@link WhiteSpace white spaces} according to the current {@link CodeStyleSettings code style settings}.
   */
  PROCESSING_BLOCKS(1, "progress.reformat.stage.processing.blocks"),

  EXPANDING_CHILDREN_INDENTS(5, "progress.reformat.stage.expanding.children.indents"),

  /**
   * This element corresponds to formatting phase when all {@link AbstractBlockWrapper wrapped code blocks} are processed and it's
   * time to apply the changes to the underlying document.
   */
  APPLYING_CHANGES(10, "progress.reformat.stage.applying.changes");
  
  private final Supplier<@NlsContexts.ProgressText String> myDescription;
  private final double myWeight;

  FormattingStateId(double weight, @PropertyKey(resourceBundle = CodeStyleBundle.BUNDLE) String descriptionKey) {
    myWeight = weight;
    myDescription = CodeStyleBundle.messagePointer(descriptionKey);
  }

  /**
   * @return    human-readable textual description of the current state id
   */
  public @NlsContexts.ProgressText String getDescription() {
    return myDescription.get();
  }

  /**
   * @return      {@code 'weight'} of the current state. Basically, it's assumed that every processing iteration of the state
   *              with greater weight is executed longer that processing iteration of the state with the lower weight
   */
  public double getProgressWeight() {
    return myWeight;
  }

  /**
   * @return    collection of formatting states that are assumed to be executed prior to the current one
   */
  @NotNull
  public Set<FormattingStateId> getPreviousStates() {
    Set<FormattingStateId> result = EnumSet.noneOf(FormattingStateId.class);
    for (FormattingStateId state : values()) {
      if (state == this) {
        break;
      }
      result.add(state);
    }
    return result;
  }
}
