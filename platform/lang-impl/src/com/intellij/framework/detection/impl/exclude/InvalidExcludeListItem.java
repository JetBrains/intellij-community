/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.framework.detection.impl.exclude;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.EmptyIcon;

class InvalidExcludeListItem extends ExcludeListItem {
  private final @NlsSafe String myFileUrl;
  private final String myFrameworkTypeId;

  InvalidExcludeListItem(String frameworkTypeId, @NlsSafe String fileUrl) {
    myFrameworkTypeId = frameworkTypeId;
    myFileUrl = fileUrl;
  }

  @Override
  public String getFrameworkTypeId() {
    return myFrameworkTypeId;
  }

  @Override
  public String getFileUrl() {
    return myFileUrl;
  }

  @Override
  public void renderItem(ColoredListCellRenderer renderer) {
    if (myFrameworkTypeId != null) {
      //noinspection HardCodedStringLiteral
      renderer.append(myFrameworkTypeId, SimpleTextAttributes.ERROR_ATTRIBUTES);
      if (myFileUrl != null) {
        renderer.append(ProjectBundle.message("framework.detection.in.0", myFileUrl), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
    else {
      renderer.append(myFileUrl, SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
    renderer.setIcon(EmptyIcon.ICON_16);
  }

  @Override
  public String getPresentableFrameworkName() {
    return myFrameworkTypeId;
  }
}
