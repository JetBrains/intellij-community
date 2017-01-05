/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.classFilter.ClassFilter;
import one.util.streamex.StreamEx;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author egor
 */
public class ClassFiltersField extends TextFieldWithBrowseButton {
  private ClassFilter[] myClassFilters = ClassFilter.EMPTY_ARRAY;
  private ClassFilter[] myClassExclusionFilters = ClassFilter.EMPTY_ARRAY;

  public ClassFiltersField(Project project) {
    addActionListener(e -> {
                        reloadFilters();
                        EditClassFiltersDialog dialog = new EditClassFiltersDialog(project);
                        dialog.setFilters(myClassFilters, myClassExclusionFilters);
                        dialog.show();
                        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
                          myClassFilters = dialog.getFilters();
                          myClassExclusionFilters = dialog.getExclusionFilters();
                          updateEditor();
                        }
                      }
    );
  }

  public void setClassFilters(ClassFilter[] includeFilters, ClassFilter[] excludeFilters) {
    myClassFilters = includeFilters;
    myClassExclusionFilters = excludeFilters;
    updateEditor();
  }

  public ClassFilter[] getClassFilters() {
    reloadFilters();
    return myClassFilters;
  }

  public ClassFilter[] getClassExclusionFilters() {
    reloadFilters();
    return myClassExclusionFilters;
  }

  private void reloadFilters() {
    String filtersText = getText();

    ArrayList<ClassFilter> classFilters = new ArrayList<>();
    ArrayList<ClassFilter> exclusionFilters = new ArrayList<>();
    int startFilter = -1;
    for (int i = 0; i <= filtersText.length(); i++) {
      if (i < filtersText.length() && !Character.isWhitespace(filtersText.charAt(i))) {
        if (startFilter == -1) {
          startFilter = i;
        }
      }
      else {
        if (startFilter >= 0) {
          if (filtersText.charAt(startFilter) == '-') {
            exclusionFilters.add(new ClassFilter(filtersText.substring(startFilter + 1, i)));
          }
          else {
            classFilters.add(new ClassFilter(filtersText.substring(startFilter, i)));
          }
          startFilter = -1;
        }
      }
    }
    Arrays.stream(myClassFilters).filter(f -> !f.isEnabled()).forEach(classFilters::add);
    Arrays.stream(myClassExclusionFilters).filter(f -> !f.isEnabled()).forEach(classFilters::add);

    myClassFilters = classFilters.toArray(new ClassFilter[classFilters.size()]);
    myClassExclusionFilters = exclusionFilters.toArray(new ClassFilter[exclusionFilters.size()]);
  }

  private void updateEditor() {
    String enabledStr = StreamEx.of(myClassFilters).filter(ClassFilter::isEnabled).map(ClassFilter::getPattern).joining(" ");
    String disabledStr = StreamEx.of(myClassExclusionFilters).filter(ClassFilter::isEnabled).map(f -> "-" + f.getPattern()).joining(" ");
    if (!enabledStr.isEmpty() && !disabledStr.isEmpty()) {
      enabledStr += " ";
    }
    setText(enabledStr + disabledStr);
  }
}
