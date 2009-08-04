package com.intellij.refactoring.classMembers;

import com.intellij.lang.LanguageDependentMembersRefactoringSupport;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;

import java.util.Map;
import java.util.Set;


public class MemberDependenciesStorage<T extends NavigatablePsiElement, C extends PsiElement> {
  protected final C myClass;
  private final C mySuperClass;
  private final Map<T, Set<T>> myDependencyGraph;

  public MemberDependenciesStorage(C aClass, C superClass) {
    myClass = aClass;
    mySuperClass = superClass;
    myDependencyGraph = new HashMap<T, Set<T>>();
  }

  protected Set<T> getMemberDependencies(T member) {
    Set<T> result = myDependencyGraph.get(member);
    if (result == null) {
      DependentMembersCollectorBase<T, C> collector = getCollector();
      if (collector != null) {
        collector.collect(member);
        result = collector.getCollection();
      }
      myDependencyGraph.put(member, result);
    }
    return result;
  }

  private DependentMembersCollectorBase<T, C> getCollector() {
    final ClassMembersRefactoringSupport factory = LanguageDependentMembersRefactoringSupport.INSTANCE.forLanguage(myClass.getLanguage());
    return factory != null ? factory.createDependentMembersCollector(myClass, mySuperClass) : null;
  }
}
