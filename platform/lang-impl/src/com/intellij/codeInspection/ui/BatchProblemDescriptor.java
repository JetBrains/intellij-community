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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class BatchProblemDescriptor {
  private final static Logger LOG = Logger.getInstance(BatchProblemDescriptor.class);

  private MultiMap<String, CommonProblemDescriptor> myMap = null;
  private final List<CommonProblemDescriptor> myDescriptors = new ArrayList<>();
  private final boolean myOnlyCount;

  public BatchProblemDescriptor(boolean count) {
    myOnlyCount = count;
  }

  public void addProblem(@Nullable CommonProblemDescriptor descriptor) {
    myDescriptors.add(descriptor);
    if (myOnlyCount) {
      return;
    }
    if (isIntersectionTrivial()) {
      return;
    }
    final boolean exist;
    if (myMap == null) {
      myMap = new MultiMap<>();
      exist = false;
    }
    else {
      exist = true;
    }
    if (descriptor == null) {
      myMap.clear();
      return;
    }
    final QuickFix[] fixes = descriptor.getFixes();
    if (fixes != null) {
      MultiMap<String, CommonProblemDescriptor> mapToUse = exist ? new MultiMap<>() : myMap;
      for (QuickFix fix : fixes) {
        mapToUse.putValue(fix.getName(), descriptor);
      }
      if (exist) {
        for (String name : new ArrayList<>(myMap.keySet())) {
          if (mapToUse.containsKey(name)) {
            myMap.put(name, mapToUse.get(name));
          }
          else {
            myMap.remove(name);
          }
        }
      }
    }
  }

  public static BatchProblemDescriptor single(@Nullable CommonProblemDescriptor descriptor) {
    final BatchProblemDescriptor intersector = new BatchProblemDescriptor(false);
    intersector.addProblem(descriptor);
    return intersector;
  }

  public int getProblemCount() {
    return myDescriptors.size();
  }

  @Nullable
  public PsiElement getFirstProblemElement() {
    for (CommonProblemDescriptor descriptor : myDescriptors) {
      if (descriptor == null) continue;
      final PsiElement element = ((ProblemDescriptorBase)descriptor).getPsiElement();
      if (element != null) {
        return element;
      }
    }
    return null;
  }

  public Set<String> getQuickFixNames() {
    LOG.assertTrue(!myOnlyCount);
    return myMap.keySet();
  }

  public void applyFixes(final String name, final Project project) {
    LOG.assertTrue(!myOnlyCount);
    for (CommonProblemDescriptor descriptor : myMap.get(name)) {
      final QuickFix[] fixes = descriptor.getFixes();
      LOG.assertTrue(fixes != null);
      for (QuickFix fix : fixes) {
        if (name.equals(fix.getName())) {
          //noinspection unchecked
          fix.applyFix(project, descriptor);
        }
      }
    }
  }

  public QuickFix findFixFor(String name) {
    LOG.assertTrue(!myOnlyCount);
    return ContainerUtil.getFirstItem(myMap.get(name)).getFixes()[0];
  }

  public boolean isIntersectionTrivial() {
    LOG.assertTrue(!myOnlyCount);
    return myMap != null && myMap.isEmpty();
  }
}