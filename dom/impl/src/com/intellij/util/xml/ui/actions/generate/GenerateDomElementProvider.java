package com.intellij.util.xml.ui.actions.generate;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;
import com.intellij.util.xml.DomElementsNavigationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;

/**
 * User: Sergey.Vasiliev
 */
public abstract class GenerateDomElementProvider<T extends DomElement> {
  private String myDescription;

  public GenerateDomElementProvider(String description) {
    myDescription = description;
  }

  public abstract T generate(final Project project, final Editor editor, final PsiFile file);

  public void navigate(final DomElement element) {
    if (element != null && element.isValid()) {
      final DomElement copy = element.createStableCopy();
      final DomElementNavigationProvider navigateProvider = getNavigationProviderName(element.getManager().getProject());

      if (navigateProvider != null && navigateProvider.canNavigate(copy)) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            doNavigate(navigateProvider, copy);
          }
        });
      }
    }
  }

  protected void doNavigate(final DomElementNavigationProvider navigateProvider, final DomElement copy) {
    navigateProvider.navigate(copy, true);
  }

  protected static DomElementNavigationProvider getNavigationProviderName(Project project) {
    return DomElementsNavigationManager.getManager(project)
      .getDomElementsNavigateProvider(DomElementsNavigationManager.DEFAULT_PROVIDER_NAME);
  }

  public String getDescription() {
    return myDescription == null ? "" : myDescription;
  }
}
