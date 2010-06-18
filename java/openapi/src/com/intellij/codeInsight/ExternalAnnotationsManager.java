/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 26-Jun-2007
 */
package com.intellij.codeInsight;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public abstract class ExternalAnnotationsManager {
  @NonNls public static final String ANNOTATIONS_XML = "annotations.xml";

  public enum AnnotationPlace {
    IN_CODE,
    EXTERNAL,
    NOWHERE
  }

  private static final NotNullLazyKey<ExternalAnnotationsManager, Project> INSTANCE_KEY = ServiceManager.createLazyKey(ExternalAnnotationsManager.class);

  public static ExternalAnnotationsManager getInstance(Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  @Nullable
  public abstract PsiAnnotation findExternalAnnotation(final PsiModifierListOwner listOwner, final String annotationFQN);

  @Nullable
  public abstract PsiAnnotation[] findExternalAnnotations(final PsiModifierListOwner listOwner);

  public abstract void annotateExternally(final PsiModifierListOwner listOwner,
                                          final String annotationFQName,
                                          final PsiFile fromFile,
                                          final PsiNameValuePair[] value);

  public abstract boolean deannotate(final PsiModifierListOwner listOwner, final String annotationFQN);

  public abstract AnnotationPlace chooseAnnotationsPlace(final PsiElement element);

}
