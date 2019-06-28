// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  /**
   * Returns external annotations with fully qualified name of {@code annotationFQN}
   * associated with {@code listOwner}.
   *
   * Multiple results may be returned for repeatable annotations and annotations
   * from several external annotations roots.
   *
   * @param listOwner API element to return external annotations of
   * @param annotationFQN fully qualified name of the annotation to search for
   * @return external annotations of the {@code listOwner}
   */
  @NotNull
  public abstract List<PsiAnnotation> findExternalAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);


  // Method used in Kotlin plugin
  public abstract boolean isExternalAnnotationWritable(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  @Nullable
  public abstract PsiAnnotation[] findExternalAnnotations(@NotNull PsiModifierListOwner listOwner);

  /**
   * Returns external annotations associated with default
   * constructor of the {@code aClass}, if the constructor exists.
   * <p>
   * Default constructors should be handled specially
   * because they don't have {@code PsiModifierListOwner},
   * nor they are returned in {@link PsiClass#getConstructors()}.
   * <p>
   * Yet default constructors may be externally annotated
   * in corresponding {@code annotations.xml}:
   * <pre>{@code <item name='com.example.Foo Foo()'>
   *  <annotation name='org.some.Annotation'/>
   * </item>}</pre>
   *
   * @param aClass class of which default constructor's external annotations are to be found
   * @return external annotations of the default constructor of {@code aClass} or {@code null}
   * if the class doesn't have a default constructor
   */
  @Nullable
  public abstract List<PsiAnnotation> findDefaultConstructorExternalAnnotations(@NotNull PsiClass aClass);

  /**
   * Returns external annotations with fully qualified name of {@code annotationFQN}
   * associated with default constructor of the {@code aClass}, if the constructor exists.
   *
   * Multiple annotations may be returned since there may be repeatable annotations
   * or annotations from several external annotations roots.
   *
   * @param aClass class of which default constructor's external annotations are to be found
   * @param annotationFQN fully qualified name of annotation class to search for
   * @return annotations of the default constructor of {@code aClass}, or {@code null} if the
   * class doesn't have a default constructor.
   * @see #findDefaultConstructorExternalAnnotations(PsiClass)
   */
  @Nullable
  public abstract List<PsiAnnotation> findDefaultConstructorExternalAnnotations(@NotNull PsiClass aClass, @NotNull String annotationFQN);

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

  @NotNull
  public abstract AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element);

  @Nullable
  public abstract List<PsiFile> findExternalAnnotationsFiles(@NotNull PsiModifierListOwner listOwner);

  public static class CanceledConfigurationException extends RuntimeException {}
}
