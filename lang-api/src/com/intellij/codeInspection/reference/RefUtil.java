/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.codeInspection.reference;

import com.intellij.ExtensionPoints;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * Application component which provides utility methods for working with the reference
 * graph.
 *
 * @author anna
 * @since 6.0
 */
public abstract class RefUtil {
  public static RefUtil getInstance() {
    return ServiceManager.getService(RefUtil.class);
  }

  public abstract boolean belongsToScope(PsiElement psiElement, RefManager refManager);

  public abstract String getQualifiedName(RefEntity refEntity);

  public abstract void removeRefElement(RefElement refElement, List<RefElement> deletedRefs);

  public static boolean isEntryPoint(final RefElement refElement) {
    final Object[] addins = Extensions.getRootArea()
      .getExtensionPoint(ExtensionPoints.INSPECTION_ENRTY_POINT).getExtensions();
    for (Object entryPoint : addins) {
      if (((EntryPoint)entryPoint).accept(refElement)) {
        return true;
      }
    }
    final PsiElement element = refElement.getElement();
    final ImplicitUsageProvider[] implicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
    for (ImplicitUsageProvider provider : implicitUsageProviders) {
      if (provider.isImplicitUsage(element)) return true;
    }
    return false;
  }
}
