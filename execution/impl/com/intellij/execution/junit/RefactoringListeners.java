package com.intellij.execution.junit;

import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.SingleClassConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;

public class RefactoringListeners {
  public static RefactoringElementListener getListener(final PsiPackage psiPackage, final Accessor<PsiPackage> accessor) {
    final StringBuilder path = new StringBuilder();
    for (PsiPackage parent = accessor.getPsiElement(); parent != null; parent = parent.getParentPackage()) {
      if (parent.equals(psiPackage)) return new RefactorPackage(accessor, path.toString());
      if (path.length() > 0) path.insert(0, '.');
      path.insert(0, parent.getName());
    }
    return null;
  }

  public static RefactoringElementListener getListeners(final PsiClass psiClass, final Accessor<PsiClass> accessor) {
    final PsiClass aClass = accessor.getPsiElement();
    if (aClass == null) return null;
    final StringBuilder path = new StringBuilder();
    for (PsiClass parent = aClass; parent != null; parent = PsiTreeUtil.getParentOfType(parent, PsiClass.class, true)) {
      if (parent.equals(psiClass)) return new RefactorClass(accessor, path.toString());
      if (path.length() > 0) path.insert(0, '$');
      path.insert(0, parent.getName());
    }
    return null;
  }

  public static RefactoringElementListener getClassOrPackageListener(final PsiElement element, final Accessor<PsiClass> accessor) {
    if (element instanceof PsiClass) return getListeners((PsiClass)element, accessor);
    if (element instanceof PsiPackage) {
      final PsiClass aClass = accessor.getPsiElement();
      if (aClass == null) return null;
      return getListener((PsiPackage)element, new ClassPackageAccessor(accessor));
    }
    return null;

  }

  public interface Accessor<T extends PsiElement> {
    void setName(String qualifiedName);
    T getPsiElement();
    void setPsiElement(T psiElement);
  }

  public static class SingleClassConfigurationAccessor implements Accessor<PsiClass> {
    private final SingleClassConfiguration myConfiguration;

    public SingleClassConfigurationAccessor(final SingleClassConfiguration configuration) {
      myConfiguration = configuration;
    }

    public PsiClass getPsiElement() {
      return myConfiguration.getMainClass();
    }

    public void setPsiElement(final PsiClass psiClass) {
      myConfiguration.setMainClass(psiClass);
    }

    public void setName(final String qualifiedName) {
      myConfiguration.setMainClassName(qualifiedName);
    }
  }

  private static abstract class RenameElement<T extends PsiElement> implements RefactoringElementListener {
    private final Accessor<T> myAccessor;
    private final String myPath;

    public RenameElement(final Accessor<T> accessor, final String path) {
      myAccessor = accessor;
      myPath = path;
    }

    public void elementMoved(final PsiElement newElement) {
      setName((T)newElement);
    }

    public void elementRenamed(final PsiElement newElement) {
      setName((T)newElement);
    }

    private void setName(T newElement) {
      String qualifiedName = getQualifiedName(newElement);
      if (myPath.length() > 0) {
        qualifiedName = qualifiedName + "." + myPath;
        newElement = findNewElement(newElement, qualifiedName);
      }
      if (newElement != null) {
        myAccessor.setPsiElement(newElement);
      }
      else {
        myAccessor.setName(qualifiedName);
      }
    }

    protected abstract T findNewElement(T newParent, String qualifiedName);

    protected abstract String getQualifiedName(T element);
  }

  private static class RefactorPackage extends RenameElement<PsiPackage> {
    public RefactorPackage(final Accessor<PsiPackage> accessor, final String path) {
      super(accessor, path);
    }

    public PsiPackage findNewElement(final PsiPackage psiPackage, final String qualifiedName) {
      return psiPackage.getManager().findPackage(qualifiedName);
    }

    public String getQualifiedName(final PsiPackage psiPackage) {
      return psiPackage.getQualifiedName();
    }
  }

  private static class RefactorClass extends RenameElement<PsiClass> {
    public RefactorClass(final Accessor<PsiClass> accessor, final String path) {
      super(accessor, path);
    }

    public PsiClass findNewElement(final PsiClass psiClass, final String qualifiedName) {
      return JavaPsiFacade.getInstance(psiClass.getProject())
        .findClass(qualifiedName.replace('$', '.'), GlobalSearchScope.moduleScope(ExecutionUtil.findModule(psiClass)));
    }

    public String getQualifiedName(final PsiClass psiClass) {
      return psiClass.getQualifiedName();
    }
  }

  private static class ClassPackageAccessor implements RefactoringListeners.Accessor<PsiPackage> {
    private final PsiPackage myContainingPackage;
    private final Module myModule;
    private final RefactoringListeners.Accessor<PsiClass> myAccessor;
    private final String myInpackageName;

    public ClassPackageAccessor(final RefactoringListeners.Accessor<PsiClass> accessor) {
      myAccessor = accessor;
      PsiClass aClass = myAccessor.getPsiElement();
      aClass = (PsiClass)aClass.getOriginalElement();
      myContainingPackage = JavaDirectoryService.getInstance().getPackage(aClass.getContainingFile().getContainingDirectory());
      myModule = ExecutionUtil.findModule(aClass);
      final String classQName = aClass.getQualifiedName();
      final String classPackageQName = myContainingPackage.getQualifiedName();
      if (classQName.startsWith(classPackageQName)) {
        final String inpackageName = classQName.substring(classPackageQName.length());
        if (StringUtil.startsWithChar(inpackageName, '.')) {
          myInpackageName = inpackageName.substring(1);
        }
        else {
          myInpackageName = inpackageName;
        }
      }
      else {
        myInpackageName = null;
      }
    }

    public PsiPackage getPsiElement() {
      return myContainingPackage;
    }

    public void setPsiElement(final PsiPackage psiPackage) {
      if (myInpackageName == null) return; //we can do nothing
      final String classQName = getClassQName(psiPackage.getQualifiedName());
      final PsiClass newClass = JUnitUtil.findPsiClass(classQName, myModule, psiPackage.getProject(), false);
      if (newClass != null) {
        myAccessor.setPsiElement(newClass);
      }
      else {
        myAccessor.setName(classQName);
      }
    }

    public void setName(final String qualifiedName) {
      myAccessor.setName(getClassQName(qualifiedName));
    }

    private String getClassQName(final String packageQName) {
      if (packageQName.length() > 0) {
        return packageQName + '.' + myInpackageName;
      }
      else {
        return myInpackageName;
      }
    }
  }
}
