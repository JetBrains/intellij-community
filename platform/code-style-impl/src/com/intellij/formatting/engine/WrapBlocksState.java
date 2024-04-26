// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.engine;

import com.intellij.formatting.*;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public final class WrapBlocksState extends State {
  private final InitialInfoBuilder myWrapper;
  private final BlockIndentOptions myBlockIndentOptions;
  private WhiteSpace myLastWhiteSpace;
  private BlockRangesMap myBlockRangesMap;
  private DependentSpacingEngine myDependentSpacingEngine;
  private AlignmentHelper myAlignmentHelper;
  private IndentAdjuster myIndentAdjuster;
  private WrapProcessor myWrapProcessor;

  public WrapBlocksState(@NotNull InitialInfoBuilder initialInfoBuilder, BlockIndentOptions blockIndentOptions) {
    myWrapper = initialInfoBuilder;
    myBlockIndentOptions = blockIndentOptions;
  }

  @Override
  public void doIteration() {
    if (isDone()) {
      return;
    }
    setDone(myWrapper.iteration());
  }

  public Map<AbstractBlockWrapper, Block> getBlockToInfoMap() {
    return myWrapper.getBlockToInfoMap();
  }

  public LeafBlockWrapper getFirstBlock() {
    assertDone();
    return myWrapper.getFirstTokenBlock();
  }

  public LeafBlockWrapper getLastBlock() {
    assertDone();
    return myWrapper.getLastTokenBlock();
  }

  public WhiteSpace getLastWhiteSpace() {
    assertDone();
    if (myLastWhiteSpace == null) {
      int lastBlockOffset = getLastBlock().getEndOffset();
      myLastWhiteSpace = new WhiteSpace(lastBlockOffset, false);
      FormattingDocumentModel model = myWrapper.getFormattingDocumentModel();
      CommonCodeStyleSettings.IndentOptions options = myBlockIndentOptions.getIndentOptions();
      myLastWhiteSpace.changeEndOffset(Math.max(lastBlockOffset, myWrapper.getEndOffset()), model, options);
    }
    return myLastWhiteSpace;
  }
  
  public BlockRangesMap getBlockRangesMap() {
    assertDone();
    if (myBlockRangesMap == null) {
      myBlockRangesMap = new BlockRangesMap(getFirstBlock(), getLastBlock());
    }
    return myBlockRangesMap;
  }
  
  DependentSpacingEngine getDependentSpacingEngine() {
    assertDone();
    if (myDependentSpacingEngine == null) {
      myDependentSpacingEngine = new DependentSpacingEngine(getBlockRangesMap());
    }
    return myDependentSpacingEngine;
  }
  
  Set<Alignment> getAlignmentsInsideRangesToModify() {
    assertDone();
    return myWrapper.getAlignmentsInsideRangeToModify();
  }
  
  AlignmentHelper getAlignmentHelper() {
    assertDone();
    if (myAlignmentHelper == null) {
      Document document = myWrapper.getFormattingDocumentModel().getDocument();
      myAlignmentHelper = new AlignmentHelper(document, myWrapper.getBlocksToAlign(), myBlockIndentOptions);
    }
    return myAlignmentHelper;
  }
  
  public IndentAdjuster getIndentAdjuster() {
    assertDone();
    if (myIndentAdjuster == null) {
      myIndentAdjuster = new IndentAdjuster(myBlockIndentOptions, getAlignmentHelper());
    }
    return myIndentAdjuster;
  }
  
  MultiMap<ExpandableIndent, AbstractBlockWrapper> getExpandableIndent() {
    assertDone();
    return myWrapper.getExpandableIndentsBlocks();
  }
  
  WrapProcessor getWrapProcessor() {
    assertDone();
    if (myWrapProcessor == null) {
      int rightMargin = myBlockIndentOptions.getRightMargin();
      myWrapProcessor = new WrapProcessor(myBlockRangesMap, getIndentAdjuster(), rightMargin);
    }
    return myWrapProcessor;
  }

  BlockIndentOptions getBlockIndentOptions() {
    return myBlockIndentOptions;
  }
  
  void assertDone() {
    if (!isDone()) throw new IllegalStateException();
  }
  
}