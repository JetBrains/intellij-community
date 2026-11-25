// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.basic.frontend;

import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NotNull;

/**
 * Colors an annotation name and an annotation attribute name the way the Java highlight visitor does.
 * The color needs no resolve: the parent {@link PsiAnnotation} decides it.
 * The name range starts at the {@code @} sign, like the IDEA rule in {@code HighlightNamesUtil.highlightClassName}.
 * The full Java plugin colors the same ranges through its highlight visitor, so this annotator then does nothing.
 */
final class JavaAnnotationNameAnnotator implements Annotator, DumbAware {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof PsiAnnotation annotation) {
      var reference = annotation.getNameReferenceElement();
      if (reference == null || isFullJavaPluginEnabled()) return;
      var name = reference.getReferenceNameElement();
      int nameEnd = (name != null ? name : reference).getTextRange().getEndOffset();
      color(holder, new TextRange(annotation.getTextRange().getStartOffset(), nameEnd), JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES);
    }
    else if (element instanceof PsiNameValuePair pair) {
      var name = pair.getNameIdentifier();
      if (name == null || isFullJavaPluginEnabled()) return;
      color(holder, name.getTextRange(), JavaHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES);
    }
  }

  private static boolean isFullJavaPluginEnabled() {
    IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginManagerCore.JAVA_PLUGIN_ALIAS_ID);
    return plugin != null && plugin.isEnabled();
  }

  // INFORMATION lands in the additional-syntax layer, below the LSP semantic token layer
  private static void color(@NotNull AnnotationHolder holder, @NotNull TextRange range, @NotNull TextAttributesKey key) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(range).textAttributes(key).create();
  }
}
