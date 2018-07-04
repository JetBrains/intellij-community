/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ExternalAnnotationsManager {
  public static final String ANNOTATIONS_XML = "annotations.xml";

  public static final Topic<ExternalAnnotationsListener> TOPIC = Topic.create("external annotations", ExternalAnnotationsListener.class);

  public enum AnnotationPlace {
    IN_CODE,
    EXTERNAL,
    NOWHERE
  }

  private static final NotNullLazyKey<ExternalAnnotationsManager, Project> INSTANCE_KEY = ServiceManager.createLazyKey(ExternalAnnotationsManager.class);

  public static ExternalAnnotationsManager getInstance(@NotNull Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  public abstract boolean hasAnnotationRootsForFile(@NotNull VirtualFile file);

  public abstract boolean isExternalAnnotation(@NotNull PsiAnnotation annotation);

  @Nullable
  public abstract PsiAnnotation findExternalAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  // Method used in Kotlin plugin
  public abstract boolean isExternalAnnotationWritable(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  @Nullable
  public abstract PsiAnnotation[] findExternalAnnotations(@NotNull PsiModifierListOwner listOwner);

  public abstract void annotateExternally(@NotNull PsiModifierListOwner listOwner,
                                          @NotNull String annotationFQName,
                                          @NotNull PsiFile fromFile,
                                          @Nullable PsiNameValuePair[] value) throws CanceledConfigurationException;

  public abstract boolean deannotate(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);
  public void elementRenamedOrMoved(@NotNull PsiModifierListOwner element, @NotNull String oldExternalName) { }

  // Method used in Kotlin plugin when it is necessary to leave external annotation, but modify its arguments
  public abstract boolean editExternalAnnotation(@NotNull PsiModifierListOwner listOwner,
                                                 @NotNull String annotationFQN,
                                                 @Nullable PsiNameValuePair[] value);

  public abstract AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element);

  @Nullable
  public abstract List<PsiFile> findExternalAnnotationsFiles(@NotNull PsiModifierListOwner listOwner);

  public static class CanceledConfigurationException extends RuntimeException {}
}
