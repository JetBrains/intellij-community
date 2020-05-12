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
package com.intellij.codeInsight.intention;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * An interface that {@link IntentionAction} and {@link com.intellij.codeInspection.LocalQuickFix} share.
 * @author peter
 */
public interface FileModifier extends WriteActionAware {

  /**
   * Controls whether this intention/fix is going to modify the current file.
   * If {@code @NotNull}, and the current file is read-only,
   * it will be made writable (honoring version control integration) before the intention/fix is invoked. <p/>
   *
   * By default, as a heuristic, returns the same as {@link #startInWriteAction()}.<p/>
   *
   * If the action is going to modify multiple files, or the set of the files is unknown in advance, please
   * don't bother overriding this method, return {@code false} from {@link #startInWriteAction()}, and call {@link com.intellij.codeInsight.FileModificationService} methods in the implementation, and take write actions yourself as needed.
   *
   * @param currentFile the same file where intention would be invoked (for {@link com.intellij.codeInspection.LocalQuickFix} it would be the containing file of {@link com.intellij.codeInspection.ProblemDescriptor#getPsiElement})
   */
  @Nullable
  default PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return startInWriteAction() ? currentFile : null;
  }

  /**
   * Returns the equivalent file modifier that could be applied to the 
   * non-physical copy of the file used to preview the modification.
   * May return itself if the action doesn't depend on the file.
   *
   * @param target target non-physical file 
   * @return the action that could be applied to the non-physical copy of the file.
   * Returns null if operation is not supported.
   */
  default @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    if (!startInWriteAction()) return null;
    for (Field field : ReflectionUtil.collectFields(((Object)this).getClass())) {
      if (Modifier.isStatic(field.getModifiers())) continue;
      Class<?> type = field.getType();
      if (field.getAnnotation(SafeFieldForPreview.class) != null) continue;
      while (type.isArray()) type = type.getComponentType();
      if (type.isPrimitive() || type.isEnum() || type.equals(String.class) ||
          type.equals(Class.class) || type.equals(Integer.class) || type.equals(Boolean.class) ||
          // Back-link to the parent inspection looks safe, as inspection should not depend on the file 
          (field.isSynthetic() && field.getName().equals("this$0") && 
           LocalInspectionTool.class.isAssignableFrom(type))) {
        continue;
      }
      return null;
    }
    // No PSI-specific state: it's safe to apply this action to a file copy
    return this;
  }

  /**
   * Use this annotation to mark fields in implementors that are known to contain no file-related state.
   * It's mainly useful for the fields in abstract classes: marking unknown abstract class field as 
   * safe for preview will enable default {@link #getFileModifierForPreview(PsiFile)} behavior for all
   * subclasses (unless subclass declares its own suspicious field).
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  @interface SafeFieldForPreview {}
}
