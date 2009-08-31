package com.intellij.ide.fileTemplates;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiClass;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ide.actions.SaveFileAsTemplateHandler;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class SaveJavaAsTemplateHandler implements SaveFileAsTemplateHandler {
  @Nullable
  public String getTemplateText(final PsiFile psiFile, String fileText, String nameWithoutExtension) {
    if(psiFile instanceof PsiJavaFile){
      PsiJavaFile javaFile = (PsiJavaFile)psiFile;
      String packageName = javaFile.getPackageName();
      if (packageName.length() > 0){
        fileText = StringUtil.replace(fileText, packageName, "${PACKAGE_NAME}");
      }
      PsiClass[] classes = javaFile.getClasses();
      PsiClass psiClass = null;
      if((classes.length > 0)){
        for (PsiClass aClass : classes) {
          if (nameWithoutExtension.equals(aClass.getName())) {
            psiClass = aClass;
            break;
          }
        }
      }
      if(psiClass != null){
        //todo[myakovlev] ? PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
        return StringUtil.replace(fileText, nameWithoutExtension,"${NAME}");
      }
    }
    return null;
  }
}
