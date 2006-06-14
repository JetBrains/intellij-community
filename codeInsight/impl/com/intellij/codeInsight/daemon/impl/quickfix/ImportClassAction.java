/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 20, 2002
 * Time: 8:49:24 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ImportClassAction implements IntentionAction {
  private PsiJavaCodeReferenceElement myRef;

  public ImportClassAction(PsiJavaCodeReferenceElement element) {
    myRef = element;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("import.class.fix");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("import.class.fix");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (myRef == null || !myRef.isValid()) return false;
    if (!file.getManager().isInProject(file)) return false;

    PsiManager manager = file.getManager();

    //[ven] show intention action always
    //if (DaemonCodeAnalyzer.getInstance(project).isImportHintsEnabled(file)) return false;

    List<PsiClass> classesToImport = getClassesToImport(manager);
    return classesToImport.size() != 0;
  }

  private List<PsiClass> getClassesToImport(PsiManager manager) {
    PsiShortNamesCache cache = manager.getShortNamesCache();
    String name = myRef.getReferenceName();
    GlobalSearchScope scope = myRef.getResolveScope();
    PsiClass[] classes = cache.getClassesByName(name, scope);
    ArrayList<PsiClass> classList = new ArrayList<PsiClass>();
    boolean isAnnotationReference = myRef.getParent() instanceof PsiAnnotation;
    for (PsiClass aClass : classes) {
      if (isAnnotationReference && !aClass.isAnnotationType()) continue;
      if (CompletionUtil.isInExcludedPackage(aClass)) continue;
      PsiFile file = aClass.getContainingFile();
      if (file instanceof PsiJavaFile) {
        if (((PsiJavaFile)file).getPackageName().length() != 0) { //do not show classes from default package
          String qName = aClass.getQualifiedName();
          if (qName != null) { //filter local classes
            if (qName.endsWith(name)) {
              if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                classList.add(aClass);
              }
            }
          }
        }
      }
    }
    return classList;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiManager manager = PsiManager.getInstance(project);
        List<PsiClass> classesToImport = getClassesToImport(manager);
        PsiClass[] classes = classesToImport.toArray(new PsiClass[classesToImport.size()]);
        if (classes.length == 0) return;
        CodeInsightUtil.sortIdenticalShortNameClasses(classes);

        AddImportAction action = new AddImportAction(project, myRef, classes, editor);
        action.execute();
      }
    });
  }

  public boolean startInWriteAction() {
    return true;
  }
}
