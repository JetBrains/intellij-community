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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public abstract class GenerateMembersHandlerBase implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateMembersHandlerBase");

  private String myChooserTitle;
  protected boolean myToCopyJavaDoc = false;

  public GenerateMembersHandlerBase(String chooserTitle) {
    myChooserTitle = chooserTitle;
  }

  public final void invoke(final Project project, final Editor editor, PsiFile file) {
    if (!file.isWritable()){
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)){
        return;
      }
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    do {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    } while (element instanceof PsiTypeParameter);

    final PsiClass aClass = (PsiClass) element;
    if (aClass == null || aClass.isInterface()) return; //?
    LOG.assertTrue(aClass.isValid());
    LOG.assertTrue(aClass.getContainingFile() != null);

    final ClassMember[] members = chooseOriginalMembers(aClass, project);
    if (members == null) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        doGenerate(project, editor, aClass, members);
      }
    });
  }

  private void doGenerate(final Project project, final Editor editor, PsiClass aClass, ClassMember[] members) {
    int offset = editor.getCaretModel().getOffset();

    int col = editor.getCaretModel().getLogicalPosition().column;
    int line = editor.getCaretModel().getLogicalPosition().line;
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(0, 0));

    GenerationInfo[] newMembers;
    try{
      GenerationInfo[] prototypes = generateMemberPrototypes(aClass, members);
      newMembers = GenerateMembersUtil.insertMembersAtOffset(aClass.getContainingFile(), offset, prototypes);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
      return;
    }
    if (newMembers == null) return;

    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, col));

    final ArrayList<TemplateGenerationInfo> templates = new ArrayList<TemplateGenerationInfo>();
    for (GenerationInfo member : newMembers) {
      if (member instanceof TemplateGenerationInfo) {
        templates.add((TemplateGenerationInfo) member);
      }
    }

    if (!templates.isEmpty()){
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
      runTemplates(project, editor, templates, 0);
    }
    else if (newMembers.length > 0){
      GenerateMembersUtil.positionCaret(editor, newMembers[0].getPsiMember(), false);
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
      public void templateFinished(Template template) {
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

  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
    ArrayList<GenerationInfo> array = new ArrayList<GenerationInfo>();
    for (ClassMember member : members) {
      GenerationInfo[] prototypes = generateMemberPrototypes(aClass, member);
      if (prototypes != null) {
        array.addAll(Arrays.asList(prototypes));
      }
    }
    return array.toArray(new GenerationInfo[array.size()]);
  }

  protected abstract ClassMember[] getAllOriginalMembers(PsiClass aClass);

  protected abstract GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) throws IncorrectOperationException;

  public boolean startInWriteAction() {
    return false;
  }
}