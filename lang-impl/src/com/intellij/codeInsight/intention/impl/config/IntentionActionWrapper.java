package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class IntentionActionWrapper implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper");

  private IntentionAction myDelegate;
  private final String[] myCategories;
  private final IntentionActionBean myExtension;

  public IntentionActionWrapper(final IntentionActionBean extension, String[] categories) {
    myExtension = extension;
    myCategories = categories;
  }

  @NotNull
  public String getText() {
    return getDelegate().getText();
  }

  @NotNull
  public String getFamilyName() {
    return getDelegate().getFamilyName();
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return getDelegate().isAvailable(project, editor, file);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    getDelegate().invoke(project, editor, file);
  }

  public boolean startInWriteAction() {
    return getDelegate().startInWriteAction();
  }

  public String getFullFamilyName(){
    if (myCategories != null) {
      return StringUtil.join(myCategories, "/") + "/" + getFamilyName();
    }
    else {
      return getFamilyName();
    }
  }

  public synchronized IntentionAction getDelegate() {
    if (myDelegate == null) {
      try {
        myDelegate = myExtension.instantiate();
      }
      catch (ClassNotFoundException e) {
        LOG.error(e);
      }
    }
    return myDelegate;
  }

  public String getImplementationClassName() {
    return myExtension.className;
  }

  public ClassLoader getImplementationClassLoader() {
    return myExtension.getLoaderForClass();
  }
}
