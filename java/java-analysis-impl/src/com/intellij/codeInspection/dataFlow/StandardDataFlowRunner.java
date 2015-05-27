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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 10:16:39 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.InstanceofInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class StandardDataFlowRunner extends DataFlowRunner {
  private final Set<Instruction> myCCEInstructions = new HashSet<Instruction>();

  public StandardDataFlowRunner() {
    this(false, true);
  }
  public StandardDataFlowRunner(boolean unknownMembersAreNullable, boolean honorFieldInitializers) {
    super(unknownMembersAreNullable, honorFieldInitializers);
  }

  public void onInstructionProducesCCE(Instruction instruction) {
    myCCEInstructions.add(instruction);
  }

  @NotNull public Set<Instruction> getCCEInstructions() {
    return myCCEInstructions;
  }

  @NotNull public static Set<Instruction> getRedundantInstanceofs(final DataFlowRunner runner, StandardInstructionVisitor visitor) {
    HashSet<Instruction> result = new HashSet<Instruction>(1);
    for (Instruction instruction : runner.getInstructions()) {
      if (instruction instanceof InstanceofInstruction && visitor.isInstanceofRedundant((InstanceofInstruction)instruction)) {
        result.add(instruction);
      }
    }

    return result;
  }
}
