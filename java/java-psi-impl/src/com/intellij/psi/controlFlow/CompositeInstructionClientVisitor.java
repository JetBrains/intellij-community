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
 * @author Alexey
 */
package com.intellij.psi.controlFlow;

class CompositeInstructionClientVisitor extends InstructionClientVisitor<Object[]> {
  private final InstructionClientVisitor[] myVisitors;

  public CompositeInstructionClientVisitor(InstructionClientVisitor[] visitors) {
    myVisitors = visitors;
  }

  @Override
  public Object[] getResult() {
    Object[] result = new Object[myVisitors.length];
    for (int i = 0; i < myVisitors.length; i++) {
      final InstructionClientVisitor visitor = myVisitors[i];
      result[i] = visitor.getResult();
    }
    return result;
  }

  @Override public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitEmptyInstruction(EmptyInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitEmptyInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitCommentInstruction(CommentInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitCommentInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitReadVariableInstruction(ReadVariableInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitReadVariableInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitWriteVariableInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitSimpleInstruction(SimpleInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitSimpleInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitBranchingInstruction(BranchingInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitBranchingInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitConditionalBranchingInstruction(ConditionalBranchingInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitConditionalBranchingInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitConditionalGoToInstruction(ConditionalGoToInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitConditionalGoToInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitConditionalThrowToInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitThrowToInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitGoToInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitCallInstruction(instruction, offset, nextOffset);
    }
  }

  @Override public void visitReturnInstruction(ReturnInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitReturnInstruction(instruction, offset, nextOffset);
    }
  }
}