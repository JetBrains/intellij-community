package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class RenamePublicClassFix implements IntentionAction {
  private final PsiNamedElement myClass;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.RenameFileFix");

  public RenamePublicClassFix(PsiNamedElement aClass) {
    myClass = aClass;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("rename.public.class.text",
                                  myClass.getName(),
                                  myClass.getContainingFile().getVirtualFile().getNameWithoutExtension());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("rename.public.class.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myClass.isValid()) {
      return false;
    }
    final NamesValidator namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(file.getLanguage());
    return namesValidator.isIdentifier(file.getVirtualFile().getNameWithoutExtension(), project);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    LOG.assertTrue (file == myClass.getContainingFile());
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    VirtualFile vFile = file.getVirtualFile();
    String newName = vFile.getNameWithoutExtension();
    RenameProcessor processor = new RenameProcessor(project, myClass, newName, false, false);
    processor.run();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
