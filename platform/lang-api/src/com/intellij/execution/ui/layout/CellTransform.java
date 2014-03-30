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

package com.intellij.execution.ui.layout;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.content.Content;

import java.util.ArrayList;

public interface CellTransform {

  interface Restore {
    ActionCallback restoreInGrid();

    class List implements Restore {
      private final ArrayList<Restore> myActions = new ArrayList();
      private boolean myRestoringNow;

      public void add(Restore restore) {
        myActions.add(restore);
      }

      @Override
      public ActionCallback restoreInGrid() {
        myRestoringNow = true;
        if (myActions.size() == 0) return new ActionCallback.Done();
        final ActionCallback topCallback = restore(0);
        return topCallback.doWhenDone(new Runnable() {
          @Override
          public void run() {
            myActions.clear();
            myRestoringNow = false;
          }
        });
      }

      private ActionCallback restore(final int index) {
        final ActionCallback result = new ActionCallback();
        final Restore action = myActions.get(index);
        final ActionCallback actionCalback = action.restoreInGrid();
        actionCalback.doWhenDone(new Runnable() {
          @Override
          public void run() {
            if (index < myActions.size() - 1) {
              restore(index + 1).notifyWhenDone(result);
            } else {
              result.setDone();
            }
          }
        });

        return result;
      }

      public boolean isRestoringNow() {
        return myRestoringNow;
      }
    }
  }


  interface Facade {
    void minimize(Content content, Restore restore);
  }

}
