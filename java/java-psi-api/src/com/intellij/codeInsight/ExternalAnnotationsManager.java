// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ExternalAnnotationsManager {
  public static final String ANNOTATIONS_XML = "annotations.xml";

  public static final Topic<ExternalAnnotationsListener> TOPIC = new Topic<>("external annotations", ExternalAnnotationsListener.class);

  /**
   * Describes where to place the new annotation
   */
  public enum AnnotationPlace {
    /**
     * Annotation must be placed right in the code
     */
    IN_CODE,
    /**
     * Annotation must be placed externally
     */
    EXTERNAL,
    /**
     * User should be asked to decide whether they want to create new annotation root for external annotation.
     * {@link ExternalAnnotationsManager#chooseAnnotationsPlace(PsiElement)} asks user automatically, so this result is never returned,
     * but it requires EDT thread. On the other hand, {@link ExternalAnnotationsManager#chooseAnnotationsPlaceNoUi(PsiElement)}
     * never displays UI but may return this result.
     */
    NEED_ASK_USER,
    /**
     * User actively cancelled the annotation addition, so it should not be added at all.
     */
    NOWHERE
  }

  public static ExternalAnnotationsManager getInstance(@NotNull Project project) {
    return project.getService(ExternalAnnotationsManager.class);
  }

  public abstract boolean hasAnnotationRootsForFile(@NotNull VirtualFile file);

  public abstract boolean isExternalAnnotation(@NotNull PsiAnnotation annotation);

  public abstract @Nullable PsiAnnotation findExternalAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

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
  public abstract @NotNull List<PsiAnnotation> findExternalAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);


  // Method used in Kotlin plugin
  public abstract boolean isExternalAnnotationWritable(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  public abstract PsiAnnotation @Nullable [] findExternalAnnotations(@NotNull PsiModifierListOwner listOwner);

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
  public abstract @Nullable List<PsiAnnotation> findDefaultConstructorExternalAnnotations(@NotNull PsiClass aClass);

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
  public abstract @Nullable List<PsiAnnotation> findDefaultConstructorExternalAnnotations(@NotNull PsiClass aClass, @NotNull String annotationFQN);

  public abstract void annotateExternally(@NotNull PsiModifierListOwner listOwner,
                                          @NotNull String annotationFQName,
                                          @NotNull PsiFile fromFile,
                                          PsiNameValuePair @Nullable [] value) throws CanceledConfigurationException;

  public abstract boolean deannotate(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);
  public void elementRenamedOrMoved(@NotNull PsiModifierListOwner element, @NotNull String oldExternalName) { }

  // Method used in Kotlin plugin when it is necessary to leave external annotation, but modify its arguments
  public abstract boolean editExternalAnnotation(@NotNull PsiModifierListOwner listOwner,
                                                 @NotNull String annotationFQN,
                                                 PsiNameValuePair @Nullable [] value);

  /**
   * @param element element to add new annotation for
   * @return place where the annotation must be added. No UI is displayed, so can be called inside any read-action.
   * May return {@link AnnotationPlace#NEED_ASK_USER} if the user confirmation is necessary.
   */
  public abstract @NotNull AnnotationPlace chooseAnnotationsPlaceNoUi(@NotNull PsiElement element);

  /**
   * @param element element to add new annotation for
   * @return place where the annotation must be added. Must be called in EDT.
   */
  public abstract @NotNull AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element);

  /**
   * @return null if were unable to load external annotations
   */
  public abstract @Nullable List<PsiFile> findExternalAnnotationsFiles(@NotNull PsiModifierListOwner listOwner);

  public static class CanceledConfigurationException extends RuntimeException {}
}
