/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author cdr
 */
package com.intellij.psi.controlFlow;

public class ControlFlowInstructionVisitor {
  public void visitInstruction(Instruction instruction, int offset, int nextOffset) {

  }
  public void visitEmptyInstruction(EmptyInstruction instruction, int offset, int nextOffset) {
    visitSimpleInstruction(instruction, offset, nextOffset);
  }
  public void visitCommentInstruction(CommentInstruction instruction, int offset, int nextOffset) {
    visitSimpleInstruction(instruction, offset, nextOffset);
  }
  public void visitReadVariableInstruction(ReadVariableInstruction instruction, int offset, int nextOffset) {
    visitSimpleInstruction(instruction, offset, nextOffset);
  }
  public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
    visitSimpleInstruction(instruction, offset, nextOffset);
  }
  public void visitSimpleInstruction(SimpleInstruction instruction, int offset, int nextOffset) {
    visitInstruction(instruction, offset, nextOffset);
  }
  public void visitBranchingInstruction(BranchingInstruction instruction, int offset, int nextOffset) {
    visitInstruction(instruction, offset, nextOffset);
  }
  public void visitConditionalBranchingInstruction(ConditionalBranchingInstruction instruction, int offset, int nextOffset) {
    visitBranchingInstruction(instruction, offset, nextOffset);
  }
  public void visitConditionalGoToInstruction(ConditionalGoToInstruction instruction, int offset, int nextOffset) {
    visitConditionalBranchingInstruction(instruction, offset, nextOffset);
  }
  public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
    visitConditionalBranchingInstruction(instruction, offset, nextOffset);
  }
  public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
    visitBranchingInstruction(instruction, offset, nextOffset);
  }
  public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
    visitBranchingInstruction(instruction, offset, nextOffset);
  }
  public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
    visitGoToInstruction(instruction, offset, nextOffset);
  }
  public void visitReturnInstruction(ReturnInstruction instruction, int offset, int nextOffset) {
    visitGoToInstruction(instruction, offset, nextOffset);
  }
}