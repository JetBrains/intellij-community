package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;

import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public abstract class AbstractMemberInfoStorage<T extends PsiElement, C extends PsiElement, M extends MemberInfoBase<T>> {
  protected final HashMap<C, LinkedHashSet<C>> myClassToSubclassesMap = new HashMap<C, LinkedHashSet<C>>();
  private final HashMap<C, Set<C>> myTargetClassToExtendingMap = new HashMap<C, Set<C>>();
  private final HashMap<C, List<M>> myClassToMemberInfoMap = new HashMap<C, List<M>>();
  protected final C myClass;
  protected final MemberInfoBase.Filter<T> myFilter;
  private final HashMap<C, List<M>> myTargetClassToMemberInfosMap = new HashMap<C, List<M>>();
  private final HashMap<C, LinkedHashSet<M>> myTargetClassToMemberInfosListMap = new HashMap<C, LinkedHashSet<M>>();
  private final HashMap<C, HashSet<M>> myTargetClassToDuplicatedMemberInfosMap = new HashMap<C, HashSet<M>>();

  public AbstractMemberInfoStorage(C aClass, MemberInfoBase.Filter<T> memberInfoFilter) {
    myClass = aClass;
    buildSubClassesMap(aClass);
    myFilter = memberInfoFilter;
  }

  private Set<C> getAllClasses() {
    return myClassToSubclassesMap.keySet();
  }

  public Set<C> getExtending(C baseClass) {
    Set<C> result = myTargetClassToExtendingMap.get(baseClass);
    if(result == null) {
      result = new HashSet<C>();
      result.add(baseClass);
      final Set<C> allClasses = getAllClasses();
      for (C aClass : allClasses) {
        if (isInheritor(baseClass, aClass)) {
          result.add(aClass);
        }
      }
      myTargetClassToExtendingMap.put(baseClass, result);
    }

    return result;
  }

  protected abstract boolean isInheritor(C baseClass, C aClass);

  protected abstract void buildSubClassesMap(C aClass);

  public List<M> getClassMemberInfos(C aClass) {
    List<M> result = myClassToMemberInfoMap.get(aClass);
    if(result == null) {
      ArrayList<M> temp = new ArrayList<M>();
      extractClassMembers(aClass, temp);
      result = Collections.unmodifiableList(temp);
      myClassToMemberInfoMap.put(aClass, result);
    }
    return result;
  }

  protected abstract void extractClassMembers(C aClass, ArrayList<M> temp);

  public List<M> getMemberInfosList(C baseClass) {
    List<M> result = myTargetClassToMemberInfosMap.get(baseClass);

    if (result == null) {
      Set<M> list = getIntermediateClassesMemberInfosList(baseClass);
      result = Collections.unmodifiableList(new ArrayList<M>(list));
      myTargetClassToMemberInfosMap.put(baseClass, result);
    }

    return result;
  }

  private Set<M> getIntermediateClassesMemberInfosList(C targetClass) {
    LinkedHashSet<M> result = myTargetClassToMemberInfosListMap.get(targetClass);
    if(result == null) {
      result = new LinkedHashSet<M>();
      Set<C> subclasses = getSubclasses(targetClass);
      for (C subclass : subclasses) {
        List<M> memberInfos = getClassMemberInfos(subclass);
        result.addAll(memberInfos);
      }
      for (C subclass : subclasses) {
        result.addAll(getIntermediateClassesMemberInfosList(subclass));
      }
      myTargetClassToMemberInfosListMap.put(targetClass, result);
    }
    return result;
  }

  protected LinkedHashSet<C> getSubclasses(C aClass) {
    LinkedHashSet<C> result = myClassToSubclassesMap.get(aClass);
    if(result == null) {
      result = new LinkedHashSet<C>();
      myClassToSubclassesMap.put(aClass, result);
    }
    return result;
  }

  public Set<M> getDuplicatedMemberInfos(C baseClass) {
    HashSet<M> result = myTargetClassToDuplicatedMemberInfosMap.get(baseClass);

    if(result == null) {
      result = buildDuplicatedMemberInfos(baseClass);
      myTargetClassToDuplicatedMemberInfosMap.put(baseClass, result);
    }
    return result;
  }

  private HashSet<M> buildDuplicatedMemberInfos(C baseClass) {
    HashSet<M> result = new HashSet<M>();
    List<M> memberInfos = getMemberInfosList(baseClass);

    for (int i = 0; i < memberInfos.size(); i++) {
      final M memberInfo = memberInfos.get(i);
      final PsiElement member = memberInfo.getMember();

      for(int j = 0; j < i; j++) {
        final M memberInfo1 = memberInfos.get(j);
        final PsiElement member1 = memberInfo1.getMember();
        if(memberConflict(member1,  member)) {
          result.add(memberInfo);
//        We let the first one be...
//          result.add(memberInfo1);
        }
      }
    }
    return result;
  }

  protected abstract boolean memberConflict(PsiElement member1, PsiElement member);
}
