// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.ide.util.MemberChooser;
import com.intellij.java.JavaBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.java.generate.exception.GenerateCodeException;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class GenerateMembersHandlerBase implements CodeInsightActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance(GenerateMembersHandlerBase.class);

  private final @NlsContexts.DialogTitle String myChooserTitle;
  protected boolean myToCopyJavaDoc;

  public GenerateMembersHandlerBase(@NlsContexts.DialogTitle String chooserTitle) {
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
  public final void invoke(final @NotNull Project project, final @NotNull Editor editor, @NotNull PsiFile psiFile) {
    if (!EditorModificationUtil.checkModificationAllowed(editor)) return;
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
      return;
    }
    final PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, psiFile, false);
    if (aClass == null || aClass.isInterface()) return; //?
    LOG.assertTrue(aClass.isValid());
    LOG.assertTrue(aClass.getContainingFile() != null);

    try {
      final ClassMember[] members = chooseOriginalMembers(aClass, project, editor);
      if (members == null) return;

      CommandProcessor.getInstance().executeCommand(project, () -> {
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
      }, null, null);
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
    if (!textBeforeCaret.trim().isEmpty() && StringUtil.isEmptyOrSpaces(afterCaret) &&
        (lBrace == null || lBrace.getTextOffset() < offset) && !editor.getSelectionModel().hasSelection()) {
      PsiDocumentManager.getInstance(project).commitDocument(document);
      offset = editor.getCaretModel().getOffset();
      col = editor.getCaretModel().getLogicalPosition().column;
      line = editor.getCaretModel().getLogicalPosition().line;
    }

    int finalOffset = offset;

    List<? extends GenerationInfo> prototypes = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.computeBlocking(
        () -> DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> generateMemberPrototypes(aClass, members))),
      myChooserTitle, true, project);

    List<? extends GenerationInfo> newMembers;
    if (ContainerUtil.exists(prototypes, info -> !info.getClass().equals(PsiGenerationInfo.class))) {
      // Interactive templates operate on the physical editor afterwards; keep this case on the
      // legacy write-action path.
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(0, 0));
      newMembers = WriteAction.compute(
        () -> DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(
          () -> GenerateMembersUtil.insertMembersAtOffset(aClass, finalOffset, prototypes)));
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, col));
    }
    else {
      newMembers = insertMembersInBackground(project, editor, aClass, finalOffset, members);
    }

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
          ContainerUtil.addIfNotNull(elements, member.getPsiMember());
        }
      }

      GlobalInspectionContextBase.cleanupElements(project, null, elements.toArray(PsiElement.EMPTY_ARRAY));
    }

    final ArrayList<TemplateGenerationInfo> templates = new ArrayList<>();
    for (GenerationInfo member : newMembers) {
      if (member instanceof TemplateGenerationInfo) {
        templates.add((TemplateGenerationInfo) member);
      }
    }

    if (!templates.isEmpty()){
      runTemplates(project, editor, templates, 0);
    }
    else if (!newMembers.isEmpty()){
      notifyOnSuccess(editor, members, newMembers);
    }
  }

  private @NotNull List<? extends GenerationInfo> insertMembersInBackground(@NotNull Project project,
                                                                            @NotNull Editor editor,
                                                                            @NotNull PsiClass aClass,
                                                                            int offset,
                                                                            ClassMember[] members) {
    PsiFile containingFile = aClass.getContainingFile();
    ActionContext context = ActionContext.from(editor, containingFile);
    List<TextRange> memberRanges = new ArrayList<>();
    ModCommandExecutor.executeInteractively(context, myChooserTitle, editor, () ->
      ModCommand.psiUpdate(context, updater -> {
        PsiClass classCopy = updater.getWritable(aClass);
        List<? extends GenerationInfo> inserted =
          GenerateMembersUtil.insertMembersAtOffset(classCopy, offset, generateMemberPrototypes(aClass, members));
        CodeStyleManager manager = CodeStyleManager.getInstance(project);
        for (GenerationInfo info : inserted) {
          PsiMember member = info.getPsiMember();
          if (member != null) {
            manager.reformat(member);
          }
        }
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(classCopy.getContainingFile().getFileDocument());
        for (GenerationInfo info : inserted) {
          PsiMember psiMember = info.getPsiMember();
          if (psiMember != null) {
            memberRanges.add(psiMember.getTextRange());
          }
        }
      }));

    // The command has been applied synchronously;
    // get the inserted members in the physical file.
    Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null) return List.of();
    List<GenerationInfo> result = new ArrayList<>();
    for (TextRange memberRange : memberRanges) {
      PsiMember member = PsiTreeUtil.findElementOfClassAtRange(psiFile, memberRange.getStartOffset(), memberRange.getEndOffset(), PsiMember.class);
      if (member != null) {
        result.add(new PsiGenerationInfo<>(member));
      }
    }
    return result;
  }

  protected void notifyOnSuccess(Editor editor,
                                 ClassMember[] members,
                                 List<? extends GenerationInfo> generatedMembers) {
    generatedMembers.getFirst().positionCaret(editor, false);
  }

  protected @NlsContexts.HintText String getNothingFoundMessage() {
    return JavaBundle.message("generate.members.nothing.to.insert");
  }

  private static void runTemplates(final Project myProject, final Editor editor, final List<? extends TemplateGenerationInfo> templates, final int index) {
    TemplateGenerationInfo info = templates.get(index);
    final Template template = info.getTemplate();

    PsiElement element = Objects.requireNonNull(info.getPsiMember());
    final TextRange range = element.getTextRange();
    WriteAction.run(() -> editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset()));
    int offset = range.getStartOffset();
    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    TemplateManager.getInstance(myProject).startTemplate(editor, template, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(@NotNull Template template, boolean brokenOff) {
        if (index + 1 < templates.size()){
          ApplicationManager.getApplication().invokeLater(() -> WriteCommandAction.runWriteCommandAction(myProject, ()->
              runTemplates(myProject, editor, templates, index + 1)
          ));
        }
      }
    });
  }


  protected ClassMember @Nullable [] chooseOriginalMembers(PsiClass aClass, Project project) {
    ClassMember[] allMembers = getAllOriginalMembers(aClass);
    return chooseMembers(allMembers, false, false, project, null);
  }

  protected ClassMember @Nullable [] chooseOriginalMembers(PsiClass aClass, Project project, Editor editor) {
    return chooseOriginalMembers(aClass, project);
  }

  protected ClassMember @Nullable [] chooseMembers(ClassMember[] members,
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
          final PsiDocCommentOwner owner = ((PsiElementClassMember<?>)member).getElement();
          final TextRange textRange = owner.getTextRange();
          if (textRange != null && textRange.contains(offset)) {
            preselection = member;
            break;
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
    return list == null ? null : list.toArray(ClassMember.EMPTY_ARRAY);
  }

  protected MemberChooser<ClassMember> createMembersChooser(ClassMember[] members,
                                                            boolean allowEmptySelection,
                                                            boolean copyJavadocCheckbox,
                                                            Project project) {
    MemberChooser<ClassMember> chooser =
      new MemberChooser<>(members, allowEmptySelection, true, project, getHeaderPanel(project), getOptionControls(project)) {
        @Override
        protected @Nullable String getHelpId() {
          return GenerateMembersHandlerBase.this.getHelpId();
        }
      };
    chooser.setTitle(myChooserTitle);
    chooser.setCopyJavadocVisible(copyJavadocCheckbox);
    return chooser;
  }

  protected @Nullable JComponent getHeaderPanel(Project project) {
    return null;
  }

  protected JComponent @Nullable [] getOptionControls(@Nullable Project project) {
    return getOptionControls();
  }

  protected JComponent @Nullable [] getOptionControls() {
    return null;
  }

  protected String getHelpId() {
    return null;
  }

  protected @Unmodifiable @NotNull List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
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
