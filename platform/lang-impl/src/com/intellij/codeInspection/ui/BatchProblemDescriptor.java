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
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class BatchProblemDescriptor {
  private final static Logger LOG = Logger.getInstance(BatchProblemDescriptor.class);

  private MultiMap<String, CommonProblemDescriptor> myMap = null;
  private final LinkedHashSet<CommonProblemDescriptor> myDescriptors = new LinkedHashSet<>();
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

  public PsiElement getProblemElementIfOnlyOne() {
    if (myDescriptors.size() != 1) {
      return null;
    }
    CommonProblemDescriptor descriptor = myDescriptors.iterator().next();
    if (descriptor instanceof ProblemDescriptorBase) {
      return ((ProblemDescriptorBase)descriptor).getPsiElement();
    }
    return null;
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

  @NotNull
  public List<QuickFix> getQuickFixRepresentatives() {
    LOG.assertTrue(!myOnlyCount);
    List<QuickFix> result = new ArrayList<>(myMap.size());
    for (Map.Entry<String, Collection<CommonProblemDescriptor>> e : myMap.entrySet()) {
      final String fixName = e.getKey();
      final Collection<CommonProblemDescriptor> descriptors = e.getValue();
      final CommonProblemDescriptor representative = ContainerUtil.getFirstItem(descriptors);
      LOG.assertTrue(representative != null);
      QuickFix[] fixes = representative.getFixes();
      LOG.assertTrue(fixes != null);
      for (QuickFix fix : fixes) {
        if (fix.getName().equals(fixName)) {
          result.add(fix);
          break;
        }
      }
    }
    return result;
  }

  public boolean isIntersectionTrivial() {
    LOG.assertTrue(!myOnlyCount);
    return myMap != null && myMap.isEmpty();
  }

  public List<CommonProblemDescriptor> getDescriptors() {
    return new ArrayList<>(myDescriptors);
  }
}