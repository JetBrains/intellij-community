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
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.ide.util.MemberChooser;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public abstract class GenerateMembersHandlerBase implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateMembersHandlerBase");

  private final String myChooserTitle;
  protected boolean myToCopyJavaDoc = false;

  public GenerateMembersHandlerBase(String chooserTitle) {
    myChooserTitle = chooserTitle;
  }

  public final void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
      return;
    }
    final PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
    if (aClass == null || aClass.isInterface()) return; //?
    LOG.assertTrue(aClass.isValid());
    LOG.assertTrue(aClass.getContainingFile() != null);

    try {
      final ClassMember[] members = chooseOriginalMembers(aClass, project);
      if (members == null) return;

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          doGenerate(project, editor, aClass, members);
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
    if (textBeforeCaret.trim().length() > 0 && StringUtil.isEmptyOrSpaces(afterCaret) && !editor.getSelectionModel().hasSelection()) {
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
    if (newMembers.isEmpty()) return;

    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, col));

    final ArrayList<TemplateGenerationInfo> templates = new ArrayList<TemplateGenerationInfo>();
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
      GenerateMembersUtil.positionCaret(editor, newMembers.get(0).getPsiMember(), false);
    }
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
      public void templateFinished(Template template, boolean brokenOff) {
        if (index + 1 < templates.size()){
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              new WriteCommandAction(myProject) {
                protected void run(Result result) throws Throwable {
                  runTemplates(myProject, editor, templates, index + 1);
                }
              }.execute();
            }
          });
        }
      }
    });
  }


  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    ClassMember[] allMembers = getAllOriginalMembers(aClass);
    return chooseMembers(allMembers, false, false, project);
  }

  @Nullable
  protected final ClassMember[] chooseMembers(ClassMember[] members, boolean allowEmptySelection, boolean copyJavadocCheckbox, Project project) {
    MemberChooser<ClassMember> chooser = new MemberChooser<ClassMember>(members, allowEmptySelection, true, project);
    chooser.setTitle(myChooserTitle);
    chooser.setCopyJavadocVisible(copyJavadocCheckbox);
    chooser.show();
    myToCopyJavaDoc = chooser.isCopyJavadoc();
    final List<ClassMember> list = chooser.getSelectedElements();
    return list == null ? null : list.toArray(new ClassMember[list.size()]);
  }

  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
    ArrayList<GenerationInfo> array = new ArrayList<GenerationInfo>();
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

  public boolean startInWriteAction() {
    return false;
  }
}
