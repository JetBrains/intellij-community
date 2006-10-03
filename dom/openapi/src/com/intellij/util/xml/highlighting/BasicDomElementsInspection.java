/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public abstract class BasicDomElementsInspection<T extends DomElement> extends DomElementsInspection<T> {

  public BasicDomElementsInspection(@NotNull Class<? extends T> domClass, Class<? extends T>... additionalClasses) {
    super(domClass, additionalClasses);
  }

  @Nullable
  protected ProblemDescriptor[] checkDomFile(@NotNull final DomFileElement<T> domFileElement,
                                             @NotNull final InspectionManager manager,
                                             final boolean isOnTheFly) {

    final Project project = manager.getProject();
    final DomElementAnnotationsManager annotationsManager = DomElementAnnotationsManager.getInstance(project);
    final DomElementsProblemsHolder problemsHolder = annotationsManager.getProblemHolder(domFileElement);
    List<ProblemDescriptor> problems =
      ContainerUtil.concat(problemsHolder.getAllProblems(), new Function<DomElementProblemDescriptor, Collection<? extends ProblemDescriptor>>() {
        public Collection<ProblemDescriptor> fun(final DomElementProblemDescriptor s) {
          return annotationsManager.createProblemDescriptors(manager, s);
        }
      });
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }
}
