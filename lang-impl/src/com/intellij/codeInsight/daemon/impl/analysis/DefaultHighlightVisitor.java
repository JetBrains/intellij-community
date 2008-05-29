package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;

import java.util.List;

/**
 * @author yole
 */
public class DefaultHighlightVisitor extends PsiElementVisitor implements HighlightVisitor {
  private final AnnotationHolderImpl myAnnotationHolder = new AnnotationHolderImpl();
  private HighlightInfoHolder myHolder;

  public static final ExtensionPointName<Condition<PsiErrorElement>> FILTER_EP_NAME = ExtensionPointName.create("com.intellij.highlightErrorFilter");
  private final Condition<PsiErrorElement>[] myErrorFilters;
  private final Project myProject;

  public DefaultHighlightVisitor(Project project) {
    myProject = project;
    myErrorFilters = Extensions.getExtensions(FILTER_EP_NAME, project);
  }

  public boolean suitableForFile(final PsiFile file) {
    return true;
  }

  public void visit(final PsiElement element, final HighlightInfoHolder holder) {
    myHolder = holder;
    assert !myAnnotationHolder.hasAnnotations() : myAnnotationHolder;
    element.accept(this);
  }

  public boolean analyze(final Runnable action, final boolean updateWholeFile, final PsiFile file) {
    try {
      action.run();
    }
    finally {
      myAnnotationHolder.clear();
      myHolder = null;
    }
    return true;
  }

  public HighlightVisitor clone() {
    return new DefaultHighlightVisitor(myProject);
  }

  public int order() {
    return 2;
  }

  public void visitElement(final PsiElement element) {
    runAnnotators(element);
  }

  private void runAnnotators(final PsiElement element) {
    List<Annotator> annotators = LanguageAnnotators.INSTANCE.allForLanguage(element.getLanguage());
    if (!annotators.isEmpty()) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < annotators.size(); i++) {
        Annotator annotator = annotators.get(i);
        annotator.annotate(element, myAnnotationHolder);
      }
      if (myAnnotationHolder.hasAnnotations()) {
        for (Annotation annotation : myAnnotationHolder) {
          myHolder.add(HighlightInfo.fromAnnotation(annotation));
        }
        myAnnotationHolder.clear();
      }
    }
  }

  public void visitErrorElement(final PsiErrorElement element) {
    for(Condition<PsiErrorElement> errorFilter: myErrorFilters) {
      if (errorFilter.value(element)) return;
    }

    HighlightInfo info = createErrorElementInfo(element);
    myHolder.add(info);
  }

  public static HighlightInfo createErrorElementInfo(final PsiErrorElement element) {
    TextRange range = element.getTextRange();
    if (range.getLength() > 0) {
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
      int start = offset;
      PsiElement prevElement = containingFile.findElementAt(offset - 1);
      if (offset > 0 && prevElement != null && prevElement.getText().equals("(") && StringUtil.startsWithChar(text, ')')) {
        start = offset - 1;
      }
      int end = offset + 1;
      info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, start, end, element.getErrorDescription());
      info.navigationShift = offset - start;
    }
    else {
      int start;
      int end;
      if (offset > 0) {
        start = offset - 1;
        end = offset;
      }
      else {
        start = offset;
        end = offset < fileLength ? offset + 1 : offset;
      }
      info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, start, end, element.getErrorDescription());
      info.isAfterEndOfLine = true;
    }
    return info;
  }
}
