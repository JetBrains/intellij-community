/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for problems returned by local and global inspection tools.
 *
 * @author anna
 * @since 6.0
 * @see InspectionManager#createProblemDescriptor(String, QuickFix[])
 */
public interface CommonProblemDescriptor {
  CommonProblemDescriptor[] EMPTY_ARRAY = new CommonProblemDescriptor[0];
  ArrayFactory<CommonProblemDescriptor> ARRAY_FACTORY = new ArrayFactory<CommonProblemDescriptor>() {
    @NotNull
    @Override
    public CommonProblemDescriptor[] create(final int count) {
      return count == 0 ? EMPTY_ARRAY : new CommonProblemDescriptor[count];
    }
  };

  /**
   * Returns the template from which the problem description is built. The template may
   * contain special markers: <code>#ref</code> is replaced with the text of the element
   * in which the problem has been found, and <code>#loc</code> is replaced with the filename
   * and line number in exported inspection results and ignored when viewing within IDEA.
   *
   * @return the template for the problem description.
   */
  @NotNull
  String getDescriptionTemplate();

  /**
   * Returns the quickfixes for the problem.
   *
   * @return the list of quickfixes registered for the problem.
   */
  @Nullable
  QuickFix[] getFixes();
}
