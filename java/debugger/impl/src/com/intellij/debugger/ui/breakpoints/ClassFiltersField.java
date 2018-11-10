// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.Disposable;
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

  public ClassFiltersField(Project project, Disposable parent) {
    super(null, parent);
    addActionListener(e -> {
                        reloadFilters();
                        EditClassFiltersDialog dialog = createEditDialog(project);
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

  protected EditClassFiltersDialog createEditDialog(Project project) {
    return new EditClassFiltersDialog(project);
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

    myClassFilters = classFilters.toArray(ClassFilter.EMPTY_ARRAY);
    myClassExclusionFilters = exclusionFilters.toArray(ClassFilter.EMPTY_ARRAY);
  }

  private void updateEditor() {
    setText(StreamEx.of(myClassExclusionFilters).filter(ClassFilter::isEnabled).map(f -> "-" + f.getPattern())
                    .prepend(StreamEx.of(myClassFilters).filter(ClassFilter::isEnabled).map(ClassFilter::getPattern))
                    .joining(" "));
  }
}
