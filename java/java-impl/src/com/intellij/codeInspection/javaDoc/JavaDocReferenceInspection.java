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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.FQNameCellRenderer;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class JavaDocReferenceInspection extends BaseLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "JavadocReference";
  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.javadoc.ref.display.name");


  private static ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template, InspectionManager manager,
                                                    boolean onTheFly) {
    return manager.createProblemDescriptor(element, template, onTheFly, (LocalQuickFix [])null, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
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
    docComment.accept(getVisitor(references, docCommentOwner, problems, manager, isOnTheFly));
    for (PsiJavaCodeReferenceElement reference : references) {
      final List<PsiClass> classesToImport = new ImportClassFix(reference).getClassesToImport();
      final PsiElement referenceNameElement = reference.getReferenceNameElement();
      problems.add(manager.createProblemDescriptor(referenceNameElement != null ? referenceNameElement : reference, cannotResolveSymbolMessage("<code>" + reference.getText() + "</code>"),
                                                   !isOnTheFly || classesToImport.isEmpty() ? null : new AddImportFix(classesToImport), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                                                   isOnTheFly));
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
                                       final InspectionManager manager, final boolean onTheFly) {
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
          visitRefInDocTag(tag, javadocManager, context, problems, manager, onTheFly);
        }
      }

      @Override public void visitInlineDocTag(PsiInlineDocTag tag) {
        super.visitInlineDocTag(tag);
        final JavadocManager javadocManager = JavaPsiFacade.getInstance(tag.getProject()).getJavadocManager();
        visitRefInDocTag(tag, javadocManager, context, problems, manager, onTheFly);
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

  public static void visitRefInDocTag(final PsiDocTag tag, final JavadocManager manager, final PsiElement context, ArrayList<ProblemDescriptor> problems,
                                      InspectionManager inspectionManager,
                                      boolean onTheFly) {
    final String tagName = tag.getName();
    PsiDocTagValue value = tag.getValueElement();
    if (value == null) return;
    final JavadocTagInfo info = manager.getTagInfo(tagName);
    if (info != null && !info.isValidInContext(context)) return;
    String message = info == null || !info.isInline() ? null : info.checkTagValue(value);
    if (message != null){
      problems.add(createDescriptor(value, message, inspectionManager, onTheFly));
    }
    final PsiReference reference = value.getReference();
    if (reference != null) {
      PsiElement element = reference.resolve();
      if (element == null) {
        final int textOffset = value.getTextOffset();

        if (textOffset != value.getTextRange().getEndOffset()) {
          final PsiDocTagValue valueElement = tag.getValueElement();
          if (valueElement != null) {
            final CharSequence paramName =
              value.getContainingFile().getViewProvider().getContents().subSequence(textOffset, value.getTextRange().getEndOffset());
            @NonNls String params = "<code>" + paramName + "</code>";

            final List<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>();
            if (onTheFly && "param".equals(tagName)) {
              final PsiDocCommentOwner commentOwner = PsiTreeUtil.getParentOfType(tag, PsiDocCommentOwner.class);
              if (commentOwner instanceof PsiMethod) {
                final PsiMethod method = (PsiMethod)commentOwner;
                final PsiParameter[] parameters = method.getParameterList().getParameters();
                final PsiDocTag[] tags = tag.getContainingComment().getTags();
                final Set<String> unboundParams = new HashSet<String>();
                for (PsiParameter parameter : parameters) {
                  if (!JavaDocLocalInspection.isFound(tags, parameter)) {
                    unboundParams.add(parameter.getName());
                  }
                }
                if (!unboundParams.isEmpty()) {
                  fixes.add(new RenameReferenceQuickFix(unboundParams));
                }
              }
            }
            fixes.add(new RemoveTagFix(tagName, paramName, tag));

            problems.add(inspectionManager.createProblemDescriptor(valueElement, reference.getRangeInElement(), cannotResolveSymbolMessage(params),
                                                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly,
                                                                   fixes.toArray(new LocalQuickFix[fixes.size()])));
          }
        }
      }
    }
  }

  private static String cannotResolveSymbolMessage(String params) {
    return InspectionsBundle.message("inspection.javadoc.problem.cannot.resolve", params);
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
      final PsiElement element = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiJavaCodeReferenceElement.class);
      if (element instanceof PsiJavaCodeReferenceElement) {
        final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
        Collections.sort(myClassesToImport, new PsiProximityComparator(referenceElement.getElement()));
        final JList list = new JBList(myClassesToImport.toArray(new PsiClass[myClassesToImport.size()]));
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

  private static class RenameReferenceQuickFix implements LocalQuickFix {
    private final Set<String> myUnboundParams;

    public RenameReferenceQuickFix(Set<String> unboundParams) {
      myUnboundParams = unboundParams;
    }

    @NotNull
    public String getName() {
      return "Change to ...";
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final Editor editor = PlatformDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext());
      assert editor != null;
      final TextRange textRange = ((ProblemDescriptorImpl)descriptor).getTextRange();
      editor.getSelectionModel().setSelection(textRange.getStartOffset(), textRange.getEndOffset());

      final String word = editor.getSelectionModel().getSelectedText();

      if (word == null || StringUtil.isEmptyOrSpaces(word)) {
        return;
      }
      final List<LookupElement> items = new ArrayList<LookupElement>();
      for (String variant : myUnboundParams) {
        items.add(LookupElementBuilder.create(variant));
      }
      LookupManager.getInstance(project).showLookup(editor, items.toArray(new LookupElement[items.size()]));
    }
  }

  private static class RemoveTagFix implements LocalQuickFix {
    private final String myTagName;
    private final CharSequence myParamName;
    private final PsiDocTag myTag;

    public RemoveTagFix(String tagName, CharSequence paramName, PsiDocTag tag) {
      myTagName = tagName;
      myParamName = paramName;
      myTag = tag;
    }

    @NotNull
    public String getName() {
      return "Remove @" + myTagName + " " + myParamName;
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myTag.delete();
    }
  }
}
