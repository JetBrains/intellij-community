/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class NewProjectFromVCSGroup extends CheckoutActionGroup {

  public NewProjectFromVCSGroup() {
    super("NewProjectFromVCS");
  }

  @NotNull
  @Override
  protected AnAction createAction(CheckoutProvider provider) {
    return new CheckoutAction(provider, myIdPrefix) {
      @Override
      protected CheckoutProvider.Listener getListener(Project project) {
        return new CheckoutProvider.Listener() {
          @Override
          public void directoryCheckedOut(File directory, VcsKey vcs) {
            final VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory);
            if (dir != null) {
                PlatformProjectOpenProcessor.getInstance().doOpenProject(dir, null, false);
            }
          }

          @Override
          public void checkoutCompleted() {

          }
        };
      }
    };
  }
}
