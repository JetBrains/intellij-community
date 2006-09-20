/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.Function;

import java.util.List;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2005
 */
public class UsageInfoToUsageConverter {

  public static class TargetElementsDescriptor {
    private final List<SmartPsiElementPointer> myPrimarySearchedElements;
    private final List<SmartPsiElementPointer> myAdditionalSearchedElements;

    public TargetElementsDescriptor(PsiElement element) {
      this(new PsiElement[]{element});
    }

    public TargetElementsDescriptor(PsiElement[] primarySearchedElements) {
      this(primarySearchedElements, PsiElement.EMPTY_ARRAY);
    }

    public TargetElementsDescriptor(PsiElement[] primarySearchedElements, PsiElement[] additionalSearchedElements) {
      myPrimarySearchedElements = convertToSmartPointers(primarySearchedElements);
      myAdditionalSearchedElements = convertToSmartPointers(additionalSearchedElements);
    }

    private static PsiElement[] convertToPsiElements(final List<SmartPsiElementPointer> primary) {
      return ContainerUtil.map2Array(primary, PsiElement.class, new Function<SmartPsiElementPointer, PsiElement>() {
        public PsiElement fun(final SmartPsiElementPointer s) {
          return s.getElement();
        }
      });
    }

    private static List<SmartPsiElementPointer> convertToSmartPointers(final PsiElement[] primaryElements) {
      return primaryElements != null ? ContainerUtil.mapNotNull(primaryElements, new Function<PsiElement, SmartPsiElementPointer>() {
        public SmartPsiElementPointer fun(final PsiElement s) {
          return SmartPointerManager.getInstance(s.getProject()).createSmartPsiElementPointer(s);
        }
      }) : Collections.<SmartPsiElementPointer>emptyList();
    }

    /**
     * A read-only attribute describing the target as a "primary" target.
     * A primary target is a target that was the main purpose of the search.
     * All usages of a non-primary target should be considered as a special case of usages of the corresponding primary target.
     * Example: searching field and its getter and setter methods -
     *          the field searched is a primary target, and its accessor methods are non-primary targets, because
     *          for this particular search usages of getter/setter methods are to be considered as a usages of the corresponding field.
     */
    public PsiElement[] getPrimaryElements() {
      return convertToPsiElements(myPrimarySearchedElements);
    }

    public PsiElement[] getAdditionalElements() {
      return convertToPsiElements(myAdditionalSearchedElements);
    }

  }

  public static Usage convert(TargetElementsDescriptor descriptor, UsageInfo usageInfo) {
    Usage usage = _convert(descriptor, usageInfo);
    final UsageConvertor[] convertors = ApplicationManager.getApplication().getComponents(UsageConvertor.class);
    for (UsageConvertor convertor : convertors) {
      usage = convertor.convert(usage);
    }
    return usage;
  }

  private static Usage _convert(final TargetElementsDescriptor descriptor, final UsageInfo usageInfo) {
    final PsiElement[] primaryElements = descriptor.getPrimaryElements();

    if (isReadWriteAccessibleElements(primaryElements)) {
      final PsiElement usageElement = usageInfo.getElement();

      if (usageElement instanceof PsiReferenceExpression) {
        final Access access = isAccessedForReading((PsiReferenceExpression)usageElement);
        return new ReadWriteAccessUsageInfo2UsageAdapter(usageInfo, access.read, access.write);
      } else if (usageElement instanceof XmlAttributeValue) {
        final Access access = new Access(false, true);
        return new ReadWriteAccessUsageInfo2UsageAdapter(usageInfo, access.read, access.write);
      }
    }
    return new UsageInfo2UsageAdapter(usageInfo);
  }

  public static Usage[] convert(TargetElementsDescriptor descriptor, UsageInfo[] usageInfos) {
    Usage[] usages = new Usage[usageInfos.length];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = convert(descriptor, usageInfos[i]);
    }
    return usages;
  }

  private static boolean isReadWriteAccessibleElements(final PsiElement[] elements) {
    if (elements.length == 0) {
      return false;
    }
    for (PsiElement element : elements) {
      if (!(element instanceof PsiVariable) &&
          !(element instanceof XmlAttributeValue)
        ) {
        return false;
      }
    }
    return true;
  }

  private static final class Access {
    public final boolean read;
    public final boolean write;

    public Access(final boolean read, final boolean write) {
      this.read = read;
      this.write = write;
    }
  }
  private static Access isAccessedForReading(final PsiReferenceExpression referent) {
    boolean accessedForReading = PsiUtil.isAccessedForReading(referent);
    boolean accessedForWriting = PsiUtil.isAccessedForWriting(referent);
    if (!accessedForWriting) {
      //when searching usages of fields, should show all found setters as a "only write usage"
      PsiElement actualReferee = referent.resolve();
      if (actualReferee instanceof PsiMethod && PropertyUtil.isSimplePropertySetter((PsiMethod)actualReferee)) {
        accessedForWriting = true;
        accessedForReading = false;
      }
    }
    return new Access(accessedForReading, accessedForWriting);
  }

}
