/*
 * User: anna
 * Date: 21-Jan-2008
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.FieldSmartPointerNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class PsiFieldFavoriteNodeProvider extends FavoriteNodeProvider {
  public Collection<AbstractTreeNode> getFavoriteNodes(final DataContext context, final ViewSettings viewSettings) {
    final Project project = PlatformDataKeys.PROJECT.getData(context);
    if (project == null) return null;
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context);
    if (elements == null) {
      final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(context);
      if (element != null) {
        elements = new PsiElement[]{element};
      }
    }
    if (elements != null) {
      final Collection<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
      for (PsiElement element : elements) {
        if (element instanceof PsiField) {
          result.add(new FieldSmartPointerNode(project, element, viewSettings));
        }
      }
      return result.isEmpty() ? null : result;
    }
    return null;
  }

  @Override
  public AbstractTreeNode createNode(final Project project, final Object element, final ViewSettings viewSettings) {
    if (element instanceof PsiField) {
      return new FieldSmartPointerNode(project, element, viewSettings);
    }
    return super.createNode(project, element, viewSettings);
  }

  public boolean elementContainsFile(final Object element, final VirtualFile vFile) {
    return false;
  }

  public int getElementWeight(final Object value, final boolean isSortByType) {
     if (value instanceof PsiField){
      return 4;
    }
    return -1;
  }

  public String getElementLocation(final Object element) {
    if (element instanceof PsiField) {
      final PsiClass psiClass = ((PsiField)element).getContainingClass();
      if (psiClass != null) {
        return ClassPresentationUtil.getNameForClass(psiClass, true);
      }
    }
    return null;
  }

  public boolean isInvalidElement(final Object element) {
    return element instanceof PsiField && !((PsiField)element).isValid();
  }

  @NotNull
  public String getFavoriteTypeId() {
    return "field";
  }

  public String getElementUrl(final Object element) {
    if (element instanceof PsiField) {
      final PsiField aField = (PsiField)element;
      return aField.getContainingClass().getQualifiedName() + ";" + aField.getName();
    }
    return null;
  }

  public String getElementModuleName(final Object element) {
     if (element instanceof PsiField) {
      final Module module = ModuleUtil.findModuleForPsiElement((PsiField)element);
      return module != null ? module.getName() : null;
    }
    return null;
  }

  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    final Module module = moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
    final GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.allScope(project);
    final String[] paths = url.split(";");
    if (paths == null || paths.length != 2) return null;
    final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(paths[0], scope);
    if (aClass == null) return null;
    final PsiField aField = aClass.findFieldByName(paths[1], false);
    return new Object[]{aField};
  }


}