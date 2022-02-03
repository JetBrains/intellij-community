// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.SingleClassConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.UndoRefactoringElementListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;

public final class RefactoringListeners {
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

    if (element instanceof PsiFile) return null;

    UClass uClass = UastContextKt.toUElement(element, UClass.class);
    if (uClass != null) {
      PsiClass aClass = accessor.getPsiElement();
      if (uClass.equals(UastContextKt.toUElement(aClass, UClass.class))) {
        return new RefactorClass(accessor, "");
      }
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

    @Override
    public PsiClass getPsiElement() {
      return myConfiguration.getMainClass();
    }

    @Override
    public void setPsiElement(final PsiClass psiClass) {
      myConfiguration.setMainClass(psiClass);
    }

    @Override
    public void setName(final String qualifiedName) {
      myConfiguration.setMainClassName(qualifiedName);
    }
  }

  private static abstract class RenameElement<T extends PsiElement> extends RefactoringElementAdapter
                                                                    implements UndoRefactoringElementListener{
    private final Accessor<? super T> myAccessor;
    private final String myPath;

    RenameElement(final Accessor<? super T> accessor, final String path) {
      myAccessor = accessor;
      myPath = path;
    }

    @Override
    public void elementRenamedOrMoved(@NotNull final PsiElement newElement) {
      T newElement1 = convertNewElement(newElement);
      if (newElement1 == null) return;
      String qualifiedName = getQualifiedName(newElement1);
      if (myPath.length() > 0) {
        qualifiedName = qualifiedName + "." + myPath;
        newElement1 = findNewElement(newElement1, qualifiedName);
      }
      if (newElement1 != null) {
        myAccessor.setPsiElement(newElement1);
      }
      else {
        myAccessor.setName(qualifiedName);
      }
    }

    @Nullable
    protected T convertNewElement(PsiElement newElement) {
      return (T)newElement;
    }

    @Nullable
    protected abstract T findNewElement(T newParent, String qualifiedName);

    protected abstract String getQualifiedName(@NotNull T element);

    @Override
    public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
      myAccessor.setName(oldQualifiedName);
    }
  }

  private static class RefactorPackage extends RenameElement<PsiPackage> {
    RefactorPackage(final Accessor<? super PsiPackage> accessor, final String path) {
      super(accessor, path);
    }

    @Override
    public PsiPackage findNewElement(final PsiPackage psiPackage, final String qualifiedName) {
      return JavaPsiFacade.getInstance(psiPackage.getProject()).findPackage(qualifiedName);
    }

    @Override
    public String getQualifiedName(final @NotNull PsiPackage psiPackage) {
      return psiPackage.getQualifiedName();
    }
  }

  private static class RefactorClass extends RenameElement<UClass> {
    RefactorClass(final Accessor<? super PsiClass> accessor, final String path) {
      super(wrap(accessor), path);
    }

    private static Accessor<? super UClass> wrap(Accessor<? super PsiClass> accessor) {
      return new Accessor<>() {
        @Override
        public void setName(String qualifiedName) {
          accessor.setName(qualifiedName);
        }

        @Override
        public UClass getPsiElement() {
          return UastContextKt.toUElement(accessor.getPsiElement(), UClass.class);
        }

        @Override
        public void setPsiElement(UClass uClass) {
          accessor.setPsiElement(uClass);
        }
      };
    }

    @Override
    protected UClass convertNewElement(PsiElement newElement) {
      return UastContextKt.toUElement(newElement, UClass.class);
    }

    @Override
    @Nullable
    public UClass findNewElement(final UClass psiClass, final String qualifiedName) {
      final Module module = JavaExecutionUtil.findModule(psiClass);
      if (module == null) {
        return null;
      }
      return UastContextKt.toUElement(JavaPsiFacade.getInstance(psiClass.getProject())
        .findClass(qualifiedName.replace('$', '.'), GlobalSearchScope.moduleScope(module)), UClass.class);
    }

    @Override
    public String getQualifiedName(final @NotNull UClass psiClass) {
      return psiClass.getQualifiedName();
    }
  }

  public static class RefactorPackageByClass extends RenameElement<PsiClass> {
    public RefactorPackageByClass(final Accessor<? super PsiClass> accessor) {
      super(accessor, "*");
    }

    @Override
    @Nullable
    public PsiClass findNewElement(final PsiClass psiClass, final String qualifiedName) {
      final Module module = JavaExecutionUtil.findModule(psiClass);
      if (module == null) {
        return null;
      }
      return JavaPsiFacade.getInstance(psiClass.getProject())
        .findClass(qualifiedName.replace('$', '.').replace("\\*", psiClass.getName()),
                   GlobalSearchScope.moduleScope(module));
    }

    @Override
    public String getQualifiedName(final @NotNull PsiClass psiClass) {
      final String qualifiedName = psiClass.getQualifiedName();
      return qualifiedName != null ? StringUtil.getPackageName(qualifiedName) : null;
    }
  }

  private static class ClassPackageAccessor implements RefactoringListeners.Accessor<PsiPackage> {
    private final PsiPackage myContainingPackage;
    private final RefactoringListeners.Accessor<PsiClass> myAccessor;
    private final String myInpackageName;

    ClassPackageAccessor(final RefactoringListeners.Accessor<PsiClass> accessor) {
      myAccessor = accessor;
      PsiClass aClass = myAccessor.getPsiElement();
      aClass = (PsiClass)aClass.getOriginalElement();
      myContainingPackage = JavaDirectoryService.getInstance().getPackage(aClass.getContainingFile().getContainingDirectory());
      final String classQName = aClass.getQualifiedName();
      final String classPackageQName = myContainingPackage != null ? myContainingPackage.getQualifiedName() : null;
      if (classQName != null && classPackageQName != null && classQName.startsWith(classPackageQName)) {
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

    @Override
    public PsiPackage getPsiElement() {
      return myContainingPackage;
    }

    @Override
    public void setPsiElement(final PsiPackage psiPackage) {
      if (myInpackageName != null) {
        myAccessor.setName(getClassQName(psiPackage.getQualifiedName()));
      }
    }

    @Override
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
