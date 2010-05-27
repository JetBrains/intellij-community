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

package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.concurrency.JobUtil;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class DefaultHighlightVisitor implements HighlightVisitor, DumbAware {
  private final AnnotationHolderImpl myAnnotationHolder = new AnnotationHolderImpl() {
    // need synchronize since several annotators can run concurrently
    @Override
    protected synchronized Annotation createAnnotation(TextRange range, HighlightSeverity severity, String message) {
      return super.createAnnotation(range, severity, message);
    }
  };

  public static final ExtensionPointName<HighlightErrorFilter> FILTER_EP_NAME = ExtensionPointName.create("com.intellij.highlightErrorFilter");
  private final HighlightErrorFilter[] myErrorFilters;
  private final Project myProject;
  private final DumbService myDumbService;

  public DefaultHighlightVisitor(Project project) {
    myProject = project;
    myErrorFilters = Extensions.getExtensions(FILTER_EP_NAME, project);
    myDumbService = DumbService.getInstance(project);
  }
                                                     
  public boolean suitableForFile(final PsiFile file) {
    return true;
  }

  public void visit(PsiElement element, HighlightInfoHolder holder) {
    if (element instanceof PsiErrorElement) {
      visitErrorElement((PsiErrorElement)element, holder);
    }
    else {
      runAnnotators(element, holder, myAnnotationHolder);
    }
  }

  public boolean analyze(final Runnable action, final boolean updateWholeFile, final PsiFile file) {
    try {
      action.run();
    }
    finally {
      synchronized (myAnnotationHolder) {
        myAnnotationHolder.clear();
      }
    }
    return true;
  }

  public HighlightVisitor clone() {
    return new DefaultHighlightVisitor(myProject);
  }

  public int order() {
    return 2;
  }

  private static final PerThreadMap<Annotator,Language> cachedAnnotators = new PerThreadMap<Annotator, Language>() {
    @NotNull
    @Override
    public Collection<Annotator> initialValue(@NotNull Language key) {
      return LanguageAnnotators.INSTANCE.allForLanguage(key);
    }
  };
  
  static {
    LanguageAnnotators.INSTANCE.addListener(new ExtensionPointListener<Annotator>() {
      public void extensionAdded(Annotator extension, @Nullable PluginDescriptor pluginDescriptor) {
        cachedAnnotators.clear();
      }

      public void extensionRemoved(Annotator extension, @Nullable PluginDescriptor pluginDescriptor) {
        cachedAnnotators.clear();
      }
    });
  }

  private void runAnnotators(final PsiElement element, HighlightInfoHolder holder, final AnnotationHolderImpl annotationHolder) {
    List<Annotator> annotators = cachedAnnotators.get(element.getLanguage());
    if (annotators.isEmpty()) return;
    final boolean dumb = myDumbService.isDumb();

    JobUtil.invokeConcurrentlyUnderMyProgress(annotators, new Processor<Annotator>() {
      public boolean process(Annotator annotator) {
        if (dumb && !(annotator instanceof DumbAware)) {
          return true;
        }

        annotator.annotate(element, annotationHolder);
        return true;
      }
    }, true);

    synchronized (annotationHolder) {
      if (annotationHolder.hasAnnotations()) {
        for (Annotation annotation : annotationHolder) {
          holder.add(HighlightInfo.fromAnnotation(annotation));
        }
        annotationHolder.clear();
      }
    }
  }

  private void visitErrorElement(final PsiErrorElement element, HighlightInfoHolder myHolder) {
    for(HighlightErrorFilter errorFilter: myErrorFilters) {
      if (!errorFilter.shouldHighlightErrorElement(element)) return;
    }

    HighlightInfo info = createErrorElementInfo(element);
    myHolder.add(info);
  }

  public static HighlightInfo createErrorElementInfo(final PsiErrorElement element) {
    TextRange range = element.getTextRange();
    if (!range.isEmpty()) {
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, range, element.getErrorDescription());
      for(ErrorQuickFixProvider provider: Extensions.getExtensions(ErrorQuickFixProvider.EP_NAME)) {
        provider.registerErrorQuickFix(element, highlightInfo);
      }
      return highlightInfo;
    }
    int offset = range.getStartOffset();
    PsiFile containingFile = element.getContainingFile();
    int fileLength = containingFile.getTextLength();
    FileViewProvider viewProvider = containingFile.getViewProvider();
    PsiElement elementAtOffset = viewProvider.findElementAt(offset, viewProvider.getBaseLanguage());
    String text = elementAtOffset == null ? null : elementAtOffset.getText();
    HighlightInfo info;
    if (offset < fileLength && text != null && !StringUtil.startsWithChar(text, '\n') && !StringUtil.startsWithChar(text, '\r')) {
      info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, offset, offset + 1, element.getErrorDescription());
    }
    else {
      int start;
      int end;
      if (offset > 0) {
        start = offset/* - 1*/;
        end = offset;
      }
      else {
        start = offset;
        end = offset < fileLength ? offset + 1 : offset;
      }
      info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, element, start, end, element.getErrorDescription(),element.getErrorDescription(), true, null);
    }
    return info;
  }
}
