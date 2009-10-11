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
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.FQNameCellRenderer;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class JavaDocReferenceInspection extends BaseLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "JavadocReference";
  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.javadoc.ref.display.name");


  private static ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template, InspectionManager manager) {
    return manager.createProblemDescriptor(element, template, (LocalQuickFix [])null, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
  }

  @Nullable
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod psiMethod, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkMember(psiMethod, manager, isOnTheFly);
  }

  @Nullable
  public ProblemDescriptor[] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkMember(field, manager, isOnTheFly);
  }

  @Nullable
  private ProblemDescriptor[] checkMember(final PsiDocCommentOwner docCommentOwner, final InspectionManager manager, final boolean isOnTheFly) {
    ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    final PsiDocComment docComment = docCommentOwner.getDocComment();
    if (docComment == null) return null;

    final Set<PsiJavaCodeReferenceElement> references = new HashSet<PsiJavaCodeReferenceElement>();
    docComment.accept(getVisitor(references, docCommentOwner, problems, manager));
    for (PsiJavaCodeReferenceElement reference : references) {
      final List<PsiClass> classesToImport = new ImportClassFix(reference).getClassesToImport();
      problems.add(manager.createProblemDescriptor(reference, InspectionsBundle.message("inspection.javadoc.problem.cannot.resolve",
                                                                                        "<code>" + reference.getText() + "</code>"),
                                                   !isOnTheFly || classesToImport.isEmpty() ? null : new AddImportFix(classesToImport), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL));
    }

    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkMember(aClass, manager, isOnTheFly);
  }


  private PsiElementVisitor getVisitor(final Set<PsiJavaCodeReferenceElement> references,
                                       final PsiElement context,
                                       final ArrayList<ProblemDescriptor> problems,
                                       final InspectionManager manager) {
    return new JavaElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }

      @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        JavaResolveResult result = reference.advancedResolve(false);
        if (result.getElement() == null && !result.isPackagePrefixPackageReference()) {
          references.add(reference);
        }
      }

      @Override public void visitDocTag(PsiDocTag tag) {
        super.visitDocTag(tag);
        final JavadocManager javadocManager = JavaPsiFacade.getInstance(tag.getProject()).getJavadocManager();
        final JavadocTagInfo info = javadocManager.getTagInfo(tag.getName());
        if (info == null || !info.isInline()) {
          visitRefInDocTag(tag, javadocManager, context, problems, manager);
        }
      }

      @Override public void visitInlineDocTag(PsiInlineDocTag tag) {
        super.visitInlineDocTag(tag);
        final JavadocManager javadocManager = JavaPsiFacade.getInstance(tag.getProject()).getJavadocManager();
        visitRefInDocTag(tag, javadocManager, context, problems, manager);
      }

      @Override public void visitElement(PsiElement element) {
        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
          //do not visit method javadoc twice
          if (!(child instanceof PsiDocCommentOwner)) {
            child.accept(this);
          }
        }
      }
    };
  }

  public static void visitRefInDocTag(final PsiDocTag tag,
                                  final JavadocManager manager,
                                  final PsiElement context,
                                  ArrayList<ProblemDescriptor> problems,
                                  InspectionManager inspectionManager) {
    String tagName = tag.getName();
    PsiDocTagValue value = tag.getValueElement();
    if (value == null) return;
    final JavadocTagInfo info = manager.getTagInfo(tagName);
    if (info != null && !info.isValidInContext(context)) return;
    String message = info == null || !info.isInline() ? null : info.checkTagValue(value);
    if (message != null){
      problems.add(createDescriptor(value, message, inspectionManager));
    }
    final PsiReference reference = value.getReference();
    if (reference != null) {
      PsiElement element = reference.resolve();
      if (element == null) {
        final int textOffset = value.getTextOffset();

        if (textOffset != value.getTextRange().getEndOffset()) {
          final PsiDocTagValue valueElement = tag.getValueElement();
          if (valueElement != null) {
            @NonNls String params = "<code>" + value.getContainingFile().getViewProvider().getContents().subSequence(textOffset, value.getTextRange().getEndOffset()) + "</code>";
            problems.add(createDescriptor(valueElement, InspectionsBundle.message("inspection.javadoc.problem.cannot.resolve", params), inspectionManager));
          }
        }
      }
    }
  }


  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  private class AddImportFix implements LocalQuickFix{
    private final List<PsiClass> myClassesToImport;

    public AddImportFix(final List<PsiClass> classesToImport) {
      myClassesToImport = classesToImport;
    }

    @NotNull
    public String getName() {
      return QuickFixBundle.message("import.class.fix");
    }

    @NotNull
    public String getFamilyName() {
      return QuickFixBundle.message("import.class.fix");
    }

    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiJavaCodeReferenceElement) {
        final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
        Collections.sort(myClassesToImport, new PsiProximityComparator(referenceElement.getElement()));
        final JList list = new JList(myClassesToImport.toArray(new PsiClass[myClassesToImport.size()]));
        list.setCellRenderer(new FQNameCellRenderer());
        Runnable runnable = new Runnable() {
          public void run() {
            if (!element.isValid()) return;
            final int index = list.getSelectedIndex();
            if (index < 0) return;
            new WriteCommandAction(project, element.getContainingFile()){
              protected void run(final Result result) throws Throwable {
                final PsiClass psiClass = myClassesToImport.get(index);
                if (psiClass.isValid()) {
                  PsiDocumentManager.getInstance(project).commitAllDocuments();
                  referenceElement.bindToElement(psiClass);
                }
              }
            }.execute();
          }
        };
        final Editor editor = PlatformDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext());
        assert editor != null; //available for on the fly mode only
        new PopupChooserBuilder(list).
          setTitle(QuickFixBundle.message("class.to.import.chooser.title")).
          setItemChoosenCallback(runnable).
          createPopup().
          showInBestPositionFor(editor);
      }
    }
  }
}
