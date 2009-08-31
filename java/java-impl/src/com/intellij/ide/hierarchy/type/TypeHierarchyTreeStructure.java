package com.intellij.ide.hierarchy.type;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;

import java.util.ArrayList;

public final class TypeHierarchyTreeStructure extends SubtypesHierarchyTreeStructure {

  public TypeHierarchyTreeStructure(final Project project, final PsiClass aClass) {
    super(project, buildHierarchyElement(project, aClass));
    setBaseElement(myBaseDescriptor); //to set myRoot
  }

  private static HierarchyNodeDescriptor buildHierarchyElement(final Project project, final PsiClass aClass) {
    HierarchyNodeDescriptor descriptor = null;
    final PsiClass[] superClasses = createSuperClasses(aClass);
    for(int i = superClasses.length - 1; i >= 0; i--){
      final PsiClass superClass = superClasses[i];
      final HierarchyNodeDescriptor newDescriptor = new TypeHierarchyNodeDescriptor(project, descriptor, superClass, false);
      if (descriptor != null){
        descriptor.setCachedChildren(new HierarchyNodeDescriptor[] {newDescriptor});
      }
      descriptor = newDescriptor;
    }
    final HierarchyNodeDescriptor newDescriptor = new TypeHierarchyNodeDescriptor(project, descriptor, aClass, true);
    if (descriptor != null) {
      descriptor.setCachedChildren(new HierarchyNodeDescriptor[] {newDescriptor});
    }
    return newDescriptor;
  }

  private static PsiClass[] createSuperClasses(PsiClass aClass) {
    if (!aClass.isValid()) return PsiClass.EMPTY_ARRAY;
    if (aClass.isInterface()) return PsiClass.EMPTY_ARRAY;

    final ArrayList<PsiClass> superClasses = new ArrayList<PsiClass>();
    while (!"java.lang.Object".equals(aClass.getQualifiedName())) {
      final PsiClass aClass1 = aClass;
      final PsiClass[] superTypes = aClass1.getSupers();
      PsiClass superType = null;
      for (int i = 0; i < superTypes.length; i++) {
        final PsiClass type = superTypes[i];
        if (!type.isInterface()) {
          superType = type;
          break;
        }
      }
      if (superType == null) break;
      if (superClasses.contains(superType)) break;
      superClasses.add(superType);
      aClass = superType;
    }

    return superClasses.toArray(new PsiClass[superClasses.size()]);
  }
}
