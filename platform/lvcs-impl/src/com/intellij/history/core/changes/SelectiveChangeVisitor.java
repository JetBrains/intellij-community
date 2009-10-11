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

public abstract class SelectiveChangeVisitor extends ChangeVisitor {
  private final ChangeVisitor myVisitor;

  public SelectiveChangeVisitor(ChangeVisitor v) {
    myVisitor = v;
  }

  @Override
  public void begin(ChangeSet c) throws StopVisitingException, IOException {
    if (isFinished(c)) stop();
  }

  protected abstract boolean isFinished(ChangeSet c);

  @Override
  public void visit(StructuralChange c) throws IOException, StopVisitingException {
    if (!shouldProcess(c)) return;
    c.accept(myVisitor);
  }

  protected abstract boolean shouldProcess(StructuralChange c);

  @Override
  public void started(Entry r) throws IOException {
    myVisitor.started(r);
  }

  @Override
  public void finished() throws IOException {
    myVisitor.finished();
  }
}
