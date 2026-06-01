// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class ExternalAnnotationsManager {
  public static final String ANNOTATIONS_XML = "annotations.xml";
  protected static final Key<Boolean> EXTERNAL_ANNO_MARKER = Key.create("EXTERNAL_ANNO_MARKER");

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

  @Contract(pure = true)
  public static ExternalAnnotationsManager getInstance(@NotNull Project project) {
    return project.getService(ExternalAnnotationsManager.class);
  }

  public abstract boolean hasAnnotationRootsForFile(@NotNull VirtualFile file);

  /**
   * @param annotation annotation to check
   * @return true if the annotation is an external annotation
   */
  public static boolean isExternal(@NotNull PsiAnnotation annotation) {
    return annotation.getUserData(EXTERNAL_ANNO_MARKER) != null;
  }

  /**
   * @deprecated use static method {@link #isExternal(PsiAnnotation)}.
   */
  @Deprecated
  public boolean isExternalAnnotation(@NotNull PsiAnnotation annotation) {
    return isExternal(annotation);
  }

  /**
   * Returns an external annotation with fully qualified name of {@code annotationFQN}
   * associated with {@code listOwner}.
   * <p>
   * If the same annotation is applied several times (repeatable annotation, or annotations
   * from several external annotations roots), then only one of them is returned
   * (there's no guarantee, which one).
   * <p>
   * This method does not return type annotations.
   *
   * @param listOwner API element to return external annotations of
   * @param annotationFQN fully qualified name of the annotation to search for
   * @return external annotation of the {@code listOwner}; null if there is no annotation with a given qualified name.
   * @see #findExternalTypeAnnotations(PsiModifierListOwner, String) 
   * @see #isNonCodeTypeAnnotation(PsiAnnotation)
   */
  public abstract @Nullable PsiAnnotation findExternalAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  /**
   * Returns external annotations with fully qualified name of {@code annotationFQN}
   * associated with {@code listOwner}.
   * <p>
   * Multiple results may be returned for repeatable annotations and annotations
   * from several external annotations roots.
   * <p>
   * This method does not return type annotations.
   *
   * @param listOwner API element to return external annotations of
   * @param annotationFQN fully qualified name of the annotation to search for
   * @return external annotations of the {@code listOwner}
   * @see #findExternalTypeAnnotations(PsiModifierListOwner, String)
   * @see #isNonCodeTypeAnnotation(PsiAnnotation)  
   */
  public abstract @NotNull List<PsiAnnotation> findExternalAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  /**
   * Returns external annotations with fully qualified names contained in {@code annotationFQNs}
   * associated with {@code listOwner}.
   * <p>
   * Multiple results may be returned for repeatable annotations and annotations
   * from several external annotations roots.
   *
   * @param listOwner API element to return external annotations of
   * @param annotationFQNs collection of fully qualified names of the annotations to search for
   * @return external annotations of the {@code listOwner}
   */
  public @NotNull List<PsiAnnotation> findExternalAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull Collection<String> annotationFQNs) {
    PsiAnnotation[] annotations = findExternalAnnotations(listOwner);
    //There's an implementation in Kotlin tests which violates the new contract of findExternalAnnotations(listOwner) and returns null
    //noinspection ConstantValue
    return annotations == null ? Collections.emptyList() : 
           ContainerUtil.filter(annotations, annotation -> annotationFQNs.contains(annotation.getQualifiedName()));
  }


  // Method used in Kotlin plugin
  public abstract boolean isExternalAnnotationWritable(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  /**
   * Returns external annotations associated with {@code listOwner}.
   * <p>
   * This method does not return type annotations.
   *
   * @param listOwner API element to return external annotations of
   * @return external annotations of the {@code listOwner}
   * @see #findExternalTypeAnnotations(PsiModifierListOwner, String)
   */
  public abstract @NotNull PsiAnnotation @NotNull [] findExternalAnnotations(@NotNull PsiModifierListOwner listOwner);

  /**
   * @param parent a type owner (field, method, or parameter)
   * @param typePath a type path. See {@code ExternalTypeAnnotationContainer} for syntax
   * @return external type annotations for a given type path
   */
  public @NotNull PsiAnnotation @NotNull [] findExternalTypeAnnotations(@NotNull PsiModifierListOwner parent, @NotNull String typePath) {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  /**
   * @param parent a type owner (field, method, or parameter)
   * @param typePath a type path. See {@code ExternalTypeAnnotationContainer} for syntax
   * @param annotationFQN the fully-qualified name of wanted annotation
   * @return external type annotation having a given qualified name for a given type path; null if there's no such annotation.
   */
  public @Nullable PsiAnnotation findExternalTypeAnnotation(@NotNull PsiModifierListOwner parent, 
                                                            @NotNull String typePath, 
                                                            @NotNull String annotationFQN) {
    return null;
  }

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
   * <p>
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

  /**
   * @param owner element to add annotation
   * @return {@code true} if external annotations are already configured for this element and no user interaction is required,
   *         {@code false} otherwise
   */
  public abstract boolean hasConfiguredAnnotationRoot(@NotNull PsiModifierListOwner owner);

  /**
   * @param annotation annotation to check
   * @return true if a given non-code annotation should be considered as a type annotation
   * (i.e., rendered in UI as such, etc.)
   */
  public static boolean isNonCodeTypeAnnotation(@NotNull PsiAnnotation annotation) {
    String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName == null) return false;

    boolean typeAnno = isNonCodeTypeAnnotation(qualifiedName);
    return typeAnno && (isExternal(annotation) || InferredAnnotationsManager.isInferredAnnotation(annotation));
  }

  protected static boolean isNonCodeTypeAnnotation(@NotNull String qualifiedName) {
    return qualifiedName.equals(AnnotationUtil.NOT_NULL) ||
           qualifiedName.equals(AnnotationUtil.NULLABLE) ||
           qualifiedName.equals(AnnotationUtil.UNKNOWN_NULLABILITY) ||
           qualifiedName.equals("org.jetbrains.annotations.Unmodifiable") ||
           qualifiedName.equals("org.jetbrains.annotations.UnmodifiableView");
  }
}
