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
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actions.EnterAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.exception.GenerateCodeException;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class GenerateMembersHandlerBase implements CodeInsightActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateMembersHandlerBase");

  private final String myChooserTitle;
  protected boolean myToCopyJavaDoc;

  public GenerateMembersHandlerBase(String chooserTitle) {
    myChooserTitle = chooserTitle;
  }

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    final PsiClass aClass = OverrideImplementUtil.getContextClass(file.getProject(), editor, file, false);
    return aClass != null && hasMembers(aClass);
  }

  protected boolean hasMembers(@NotNull PsiClass aClass) {
    return true;
  }

  @Override
  public final void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
      return;
    }
    final PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
    if (aClass == null || aClass.isInterface()) return; //?
    LOG.assertTrue(aClass.isValid());
    LOG.assertTrue(aClass.getContainingFile() != null);

    try {
      final ClassMember[] members = chooseOriginalMembers(aClass, project, editor);
      if (members == null) return;

      WriteCommandAction.runWriteCommandAction(project, () -> {
        final int offset = editor.getCaretModel().getOffset();
        try {
          doGenerate(project, editor, aClass, members);
        }
        catch (GenerateCodeException e) {
          final String message = e.getMessage();
          ApplicationManager.getApplication().invokeLater(() -> {
            if (!editor.isDisposed()) {
              editor.getCaretModel().moveToOffset(offset);
              HintManager.getInstance().showErrorHint(editor, message);
            }
          }, project.getDisposed());
        }
      });
    }
    finally {
      cleanup();
    }
  }

  protected void cleanup() {
  }

  private void doGenerate(final Project project, final Editor editor, PsiClass aClass, ClassMember[] members) {
    int offset = editor.getCaretModel().getOffset();

    int col = editor.getCaretModel().getLogicalPosition().column;
    int line = editor.getCaretModel().getLogicalPosition().line;
    final Document document = editor.getDocument();
    int lineStartOffset = document.getLineStartOffset(line);
    CharSequence docText = document.getCharsSequence();
    String textBeforeCaret = docText.subSequence(lineStartOffset, offset).toString();
    final String afterCaret = docText.subSequence(offset, document.getLineEndOffset(line)).toString();
    final PsiElement lBrace = aClass.getLBrace();
    if (textBeforeCaret.trim().length() > 0 && StringUtil.isEmptyOrSpaces(afterCaret) &&
        (lBrace == null || lBrace.getTextOffset() < offset) && !editor.getSelectionModel().hasSelection()) {
      EnterAction.insertNewLineAtCaret(editor);
      PsiDocumentManager.getInstance(project).commitDocument(document);
      offset = editor.getCaretModel().getOffset();
      col = editor.getCaretModel().getLogicalPosition().column;
      line = editor.getCaretModel().getLogicalPosition().line;
    }

    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(0, 0));

    List<? extends GenerationInfo> newMembers;
    try{
      List<? extends GenerationInfo> prototypes = generateMemberPrototypes(aClass, members);
      newMembers = GenerateMembersUtil.insertMembersAtOffset(aClass.getContainingFile(), offset, prototypes);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
      return;
    }

    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, col));

    if (newMembers.isEmpty()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        HintManager.getInstance().showErrorHint(editor, getNothingFoundMessage());
      }
      return;
    } 
    else {
      final List<PsiElement> elements = new ArrayList<>();
      for (GenerationInfo member : newMembers) {
        if (!(member instanceof TemplateGenerationInfo)) {
          final PsiMember psiMember = member.getPsiMember();
          if (psiMember != null) {
            elements.add(psiMember);
          }
        }
      }

      GlobalInspectionContextBase.cleanupElements(project, null, elements.toArray(new PsiElement[elements.size()]));
    }

    final ArrayList<TemplateGenerationInfo> templates = new ArrayList<>();
    for (GenerationInfo member : newMembers) {
      if (member instanceof TemplateGenerationInfo) {
        templates.add((TemplateGenerationInfo) member);
      }
    }

    if (!templates.isEmpty()){
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
      runTemplates(project, editor, templates, 0);
    }
    else if (!newMembers.isEmpty()){
      newMembers.get(0).positionCaret(editor, false);
    }
  }

  protected String getNothingFoundMessage() {
    return "Nothing found to insert";
  }

  private static void runTemplates(final Project myProject, final Editor editor, final List<TemplateGenerationInfo> templates, final int index) {
    TemplateGenerationInfo info = templates.get(index);
    final Template template = info.getTemplate();

    final PsiElement element = info.getPsiMember();
    final TextRange range = element.getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    int offset = range.getStartOffset();
    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    TemplateManager.getInstance(myProject).startTemplate(editor, template, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        if (index + 1 < templates.size()){
          ApplicationManager.getApplication().invokeLater(() -> new WriteCommandAction(myProject) {
            @Override
            protected void run(@NotNull Result result) throws Throwable {
              runTemplates(myProject, editor, templates, index + 1);
            }
          }.execute());
        }
      }
    });
  }


  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    ClassMember[] allMembers = getAllOriginalMembers(aClass);
    return chooseMembers(allMembers, false, false, project, null);
  }

  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project, Editor editor) {
    return chooseOriginalMembers(aClass, project);
  }

  @Nullable
  protected ClassMember[] chooseMembers(ClassMember[] members,
                                        boolean allowEmptySelection,
                                        boolean copyJavadocCheckbox,
                                        Project project,
                                        @Nullable Editor editor) {
    MemberChooser<ClassMember> chooser = createMembersChooser(members, allowEmptySelection, copyJavadocCheckbox, project);
    if (editor != null) {
      final int offset = editor.getCaretModel().getOffset();

      ClassMember preselection = null;
      for (ClassMember member : members) {
        if (member instanceof PsiElementClassMember) {
          final PsiDocCommentOwner owner = ((PsiElementClassMember)member).getElement();
          if (owner != null) {
            final TextRange textRange = owner.getTextRange();
            if (textRange != null && textRange.contains(offset)) {
              preselection = member;
              break;
            }
          }
        }
      }
      if (preselection != null) {
        chooser.selectElements(new ClassMember[]{preselection});
      }
    }

    chooser.show();
    myToCopyJavaDoc = chooser.isCopyJavadoc();
    final List<ClassMember> list = chooser.getSelectedElements();
    return list == null ? null : list.toArray(new ClassMember[list.size()]);
  }

  protected MemberChooser<ClassMember> createMembersChooser(ClassMember[] members,
                                                            boolean allowEmptySelection,
                                                            boolean copyJavadocCheckbox,
                                                            Project project) {
    MemberChooser<ClassMember> chooser = new MemberChooser<ClassMember>(members, allowEmptySelection, true, project, getHeaderPanel(project), getOptionControls()) {
      @Nullable
      @Override
      protected String getHelpId() {
        return GenerateMembersHandlerBase.this.getHelpId();
      }
    };
    chooser.setTitle(myChooserTitle);
    chooser.setCopyJavadocVisible(copyJavadocCheckbox);
    return chooser;
  }

  @Nullable
  protected JComponent getHeaderPanel(Project project) {
    return null;
  }

  @Nullable
  protected JComponent[] getOptionControls() {
    return null;
  }

  protected String getHelpId() {
    return null;
  }

  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
    ArrayList<GenerationInfo> array = new ArrayList<>();
    for (ClassMember member : members) {
      GenerationInfo[] prototypes = generateMemberPrototypes(aClass, member);
      if (prototypes != null) {
        ContainerUtil.addAll(array, prototypes);
      }
    }
    return array;
  }

  protected abstract ClassMember[] getAllOriginalMembers(PsiClass aClass);

  protected abstract GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) throws IncorrectOperationException;

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
