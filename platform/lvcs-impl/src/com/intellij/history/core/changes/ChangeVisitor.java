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

package com.intellij.history.core.changes;

import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public abstract class ChangeVisitor {
  protected Entry myRoot;

  public void started(Entry root) throws IOException {
    myRoot = root;
  }

  public void finished() throws IOException {
  }

  public void begin(ChangeSet c) throws IOException, StopVisitingException {
  }

  public void end(ChangeSet c) throws IOException, StopVisitingException {
  }

  public void visit(PutLabelChange c) throws IOException, StopVisitingException {
  }

  public void visit(StructuralChange c) throws IOException, StopVisitingException {
  }

  public void visit(CreateEntryChange c) throws IOException, StopVisitingException {
    visit((StructuralChange)c);
  }

  public void visit(ContentChange c) throws IOException, StopVisitingException {
    visit((StructuralChange)c);
  }

  public void visit(RenameChange c) throws IOException, StopVisitingException {
    visit((StructuralChange)c);
  }

  public void visit(ROStatusChange c) throws IOException, StopVisitingException {
    visit((StructuralChange)c);
  }

  public void visit(MoveChange c) throws IOException, StopVisitingException {
    visit((StructuralChange)c);
  }

  public void visit(DeleteChange c) throws IOException, StopVisitingException {
    visit((StructuralChange)c);
  }

  protected void stop() throws StopVisitingException {
    throw new StopVisitingException();
  }

  public static class StopVisitingException extends Exception {
  }
}
