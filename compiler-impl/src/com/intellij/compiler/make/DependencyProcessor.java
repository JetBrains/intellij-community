/**
 * created at Feb 19, 2002
 * @author Jeka
 */
package com.intellij.compiler.make;

import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.classParsing.ConstantValue;
import com.intellij.compiler.classParsing.FieldInfo;
import com.intellij.compiler.classParsing.MemberInfo;
import com.intellij.compiler.classParsing.MethodInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.cls.ClsUtil;
import gnu.trove.TIntHashSet;
import org.apache.bcel.classfile.Utility;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class DependencyProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.DependencyProcessor");
  private final DependencyCache myDependencyCache;
  private final int myQName;
  private final Set myAddedMembers = new HashSet();
  private final Set myRemovedMembers = new HashSet();
  private final Set myChangedMembers = new HashSet();
  private final Map<MemberInfo, ChangeDescription> myChangeDescriptions = new HashMap<MemberInfo, ChangeDescription>();
  private final Dependency[] myBackDependencies;
  private final boolean myMembersChanged;
  private final boolean mySuperInterfaceAdded;
  private final boolean mySuperInterfaceRemoved;
  private final boolean mySuperClassChanged;
  private final boolean mySuperlistGenericSignatureChanged;
  private final boolean mySuperClassAdded;
  private final Project myProject;
  private final boolean myIsAnnotation;
  private final boolean myIsRemoteInterface;
  private final boolean myWereAnnotationTargetsRemoved;
  private final boolean myRetentionPolicyChanged;

  public DependencyProcessor(Project project, DependencyCache dependencyCache, int qName) throws CacheCorruptedException {
    myProject = project;
    myDependencyCache = dependencyCache;
    myQName = qName;
    final Cache cache = dependencyCache.getCache();
    final Cache newClassesCache = dependencyCache.getNewClassesCache();

    myBackDependencies = cache.getBackDependencies(qName);
    addAddedMembers(qName, cache, newClassesCache, myAddedMembers);
    addRemovedMembers(qName, cache, newClassesCache, myRemovedMembers);
    addChangedMembers(qName, cache, newClassesCache, myChangedMembers);

    myMembersChanged = myAddedMembers.size() > 0 || myRemovedMembers.size() > 0 || myChangedMembers.size() > 0;
    // track changes in super list
    final int oldCacheClassId = cache.getClassId(qName);
    final int newCacheClassId = newClassesCache.getClassId(qName);

    myIsRemoteInterface = CacheUtils.isInterface(cache, myQName) && cache.isRemote(oldCacheClassId);
    myIsAnnotation = ClsUtil.isAnnotation(cache.getFlags(oldCacheClassId));
    myWereAnnotationTargetsRemoved = myIsAnnotation && wereAnnotationTargesRemoved(cache, newClassesCache);
    myRetentionPolicyChanged = myIsAnnotation && hasRetentionPolicyChanged(cache, newClassesCache);

    int[] oldInterfaces = cache.getSuperInterfaces(oldCacheClassId);
    int[] newInterfaces = newClassesCache.getSuperInterfaces(newCacheClassId);
    mySuperInterfaceRemoved = wereInterfacesRemoved(oldInterfaces, newInterfaces);
    mySuperInterfaceAdded = wereInterfacesRemoved(newInterfaces, oldInterfaces);

    mySuperlistGenericSignatureChanged = isSuperlistGenericSignatureChanged(cache.getGenericSignature(oldCacheClassId), newClassesCache.getGenericSignature(newCacheClassId));

    boolean superclassesDiffer = cache.getSuperQualifiedName(oldCacheClassId) != newClassesCache.getSuperQualifiedName(newCacheClassId);
    boolean wasDerivedFromObject = "java.lang.Object".equals(dependencyCache.resolve(cache.getSuperQualifiedName(oldCacheClassId)));
    mySuperClassChanged = !wasDerivedFromObject && superclassesDiffer;
    mySuperClassAdded = wasDerivedFromObject && superclassesDiffer;
  }

  private boolean hasMembersWithoutDefaults(Set addedMembers) {
    for (Iterator it = addedMembers.iterator(); it.hasNext();) {
      MemberInfo memberInfo = (MemberInfo)it.next();
      if (memberInfo instanceof MethodInfo) {
        final ConstantValue annotationDefault = ((MethodInfo)memberInfo).getAnnotationDefault();
        if (annotationDefault == null || ConstantValue.EMPTY_CONSTANT_VALUE.equals(annotationDefault)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean wereAnnotationDefaultsRemoved() {
    for (Iterator<MemberInfo> it = myChangeDescriptions.keySet().iterator(); it.hasNext();) {
      final MemberInfo memberInfo = it.next();
      if (memberInfo instanceof MethodInfo) {
        MethodChangeDescription description = (MethodChangeDescription)myChangeDescriptions.get(memberInfo);
        if (description.removedAnnotationDefault) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isSuperlistGenericSignatureChanged(int oldGenericSignature, int newGenericSignature) throws CacheCorruptedException {
    if (oldGenericSignature == newGenericSignature) {
      return false;
    }
    if (oldGenericSignature != -1 && newGenericSignature != -1) {
      final SymbolTable symbolTable = myDependencyCache.getSymbolTable();
      final String _oldGenericMethodSignature = cutFormalPatrams(symbolTable.getSymbol(oldGenericSignature));
      final String _newGenericMethodSignature = cutFormalPatrams(symbolTable.getSymbol(newGenericSignature));
      return !_oldGenericMethodSignature.equals(_newGenericMethodSignature);
    }
    return true;
  }

  private String cutFormalPatrams(String genericClassSignature) {
    if (genericClassSignature.charAt(0) == '<') {
      int idx = genericClassSignature.indexOf('>');
      return genericClassSignature.substring(idx + 1);
    }
    return genericClassSignature;
  }

  public void run() throws CacheCorruptedException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Checking dependencies for " + myDependencyCache.resolve(myQName));
    }
    final boolean superListChanged = mySuperClassChanged || mySuperClassAdded || mySuperInterfaceAdded || mySuperInterfaceRemoved || mySuperlistGenericSignatureChanged;
    if (superListChanged) {
      myDependencyCache.registerSuperListChange(myQName);
    }
    final Cache oldCache = myDependencyCache.getCache();
    final Cache newCache = myDependencyCache.getNewClassesCache();

    if (!myMembersChanged &&
        (oldCache.getFlags(oldCache.getClassId(myQName)) == newCache.getFlags(newCache.getClassId(myQName))) &&
        !superListChanged &&
        !myWereAnnotationTargetsRemoved &&
        !myRetentionPolicyChanged) {
      return; // nothing to do
    }

    if (myIsAnnotation) {
      if (hasMembersWithoutDefaults(myAddedMembers)) {
        markAll(myBackDependencies, LOG.isDebugEnabled()? "; reason: added annotation type member without default " + myDependencyCache.resolve(myQName) : "");
        return;
      }
      if (myRemovedMembers.size() > 0) {
        markAll(myBackDependencies, LOG.isDebugEnabled()? "; reason: removed annotation type member " + myDependencyCache.resolve(myQName) : "");
        return;
      }
      if (myChangedMembers.size() > 0) { // for annotations "changed" means return type changed
        markAll(myBackDependencies, LOG.isDebugEnabled()? "; reason: changed annotation member's type " + myDependencyCache.resolve(myQName) : "");
        return;
      }
      if (wereAnnotationDefaultsRemoved()) {
        markAll(myBackDependencies, LOG.isDebugEnabled()? "; reason: removed annotation member's default value " + myDependencyCache.resolve(myQName): "");
        return;
      }
      if (myWereAnnotationTargetsRemoved) {
        markAll(myBackDependencies, LOG.isDebugEnabled()? "; reason: removed annotation's targets " + myDependencyCache.resolve(myQName) : "");
        return;
      }
      if (myRetentionPolicyChanged) {
        markAll(myBackDependencies, LOG.isDebugEnabled()? "; reason: retention policy changed for " + myDependencyCache.resolve(myQName) : "");
        return;
      }
    }

    final DependencyCacheNavigator cacheNavigator = myDependencyCache.getCacheNavigator();

    if (mySuperClassChanged || mySuperInterfaceRemoved || mySuperlistGenericSignatureChanged) {
      // superclass changed == old removed and possibly new added
      // if anything (class or interface) in the superlist was removed, should recompile all subclasses (both direct and indirect)
      // and all back-dependencies of this class and its subclasses
      markAll(myBackDependencies, LOG.isDebugEnabled()? "; reason: deleted items from the superlist or changed superlist generic signature of " + myDependencyCache.resolve(myQName) : "");
      cacheNavigator.walkSubClasses(myQName, new ClassInfoProcessor() {
        public boolean process(int classQName) throws CacheCorruptedException {
          markAll(oldCache.getBackDependencies(classQName), LOG.isDebugEnabled()? "; reason: deleted items from the superlist or changed superlist generic signature of " + myDependencyCache.resolve(myQName) : "");
          return true;
        }
      });
      return;
    }

    final boolean isKindChanged =
      (CacheUtils.isInterface(oldCache, myQName) && !CacheUtils.isInterface(newCache, myQName)) ||
      (!CacheUtils.isInterface(oldCache, myQName) && CacheUtils.isInterface(newCache, myQName));
    if (isKindChanged) {
      markAll(myBackDependencies, LOG.isDebugEnabled()? "; reason: class kind changed (class/interface) " + myDependencyCache.resolve(myQName) : "");
      cacheNavigator.walkSubClasses(myQName, new ClassInfoProcessor() {
        public boolean process(int classQName) throws CacheCorruptedException {
          markAll(oldCache.getBackDependencies(classQName), LOG.isDebugEnabled()? "; reason: class kind changed (class/interface) " + myDependencyCache.resolve(myQName) : "");
          return true;
        }
      });
      return;
    }

    boolean becameFinal = !CacheUtils.isFinal(oldCache, myQName) && CacheUtils.isFinal(newCache, myQName);
    if (becameFinal) {
      markAll(myBackDependencies, LOG.isDebugEnabled()? "; reason: class became final: " + myDependencyCache.resolve(myQName) : "");
    }
    else {
      boolean becameAbstract = !CacheUtils.isAbstract(oldCache, myQName) && CacheUtils.isAbstract(newCache, myQName);
      boolean accessRestricted = MakeUtil.isMoreAccessible(oldCache.getFlags(oldCache.getClassId(myQName)), newCache.getFlags(newCache.getClassId(myQName)));
      for (int idx = 0; idx < myBackDependencies.length; idx++) {
        Dependency backDependency = myBackDependencies[idx];
        if (myDependencyCache.isTargetClassInfoMarked(backDependency)) continue;

        if (accessRestricted) {
          if (myDependencyCache.markTargetClassInfo(backDependency)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class "+myDependencyCache.resolve(backDependency.getClassQualifiedName()) + "; reason: " + myDependencyCache.resolve(myQName) + " made less accessible");
            }
          }
          continue;
        }
        if (becameAbstract) {
          if (processClassBecameAbstract(backDependency)) {
            continue;
          }
        }
        if (isDependentOnRemovedMembers(backDependency)) {
          if (myDependencyCache.markTargetClassInfo(backDependency)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class " + myDependencyCache.resolve(backDependency.getClassQualifiedName()) + "; reason: the class uses removed members of "+myDependencyCache.resolve(myQName));
            }
          }
          continue;
        }
        if (isDependentOnChangedMembers(backDependency)) {
          if (myDependencyCache.markTargetClassInfo(backDependency)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class " + myDependencyCache.resolve(backDependency.getClassQualifiedName()) + "; reason: the class uses changed members of "+myDependencyCache.resolve(myQName));
            }
          }
          continue;
        }
        MethodInfo[] usedMethods = backDependency.getUsedMethods();
        if (isDependentOnEquivalentMethods(usedMethods, myRemovedMembers)) {
          if (myDependencyCache.markTargetClassInfo(backDependency)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class "+myDependencyCache.resolve(backDependency.getClassQualifiedName()) + "; reason: some overloaded methods of "+myDependencyCache.resolve(myQName)+ " were removed");
            }
          }
          continue;
        }
        if (isDependentOnEquivalentMethods(usedMethods, myAddedMembers)) {
          if (myDependencyCache.markTargetClassInfo(backDependency)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class "+myDependencyCache.resolve(backDependency.getClassQualifiedName()) + "; reason: some overloaded methods of "+myDependencyCache.resolve(myQName)+ " were added");
            }
          }
          continue;
        }
      }
    }

    final Set methodsToCheck = new HashSet();
    extractMethods(myRemovedMembers, methodsToCheck, false);
    extractMethods(myAddedMembers, methodsToCheck, false);

    processInheritanceDependencies(methodsToCheck.size() > 0);

    if (!MakeUtil.isAnonymous(myDependencyCache.resolve(myQName))) {
      // these checks make no sence for anonymous classes
      final TIntHashSet fieldNames = new TIntHashSet();
      extractFieldNames(myAddedMembers, fieldNames);
      int addedFieldsCount = fieldNames.size();
      extractFieldNames(myRemovedMembers, fieldNames);
      if (fieldNames.size() > 0) {
        cacheNavigator.walkSuperClasses(myQName, new ClassInfoProcessor() {
          public boolean process(final int classQName) throws CacheCorruptedException {
            markUseDependenciesOnFields(classQName, fieldNames);
            return true;
          }
        });
      }
      if (addedFieldsCount > 0 && CacheUtils.isInterface(oldCache, myQName)) {
        final TIntHashSet visitedClasses = new TIntHashSet();
        visitedClasses.add(myQName);
        cacheNavigator.walkSubClasses(myQName, new ClassInfoProcessor() {
          public boolean process(int subclassQName) throws CacheCorruptedException {
            markUseDependenciesOnFields(subclassQName, fieldNames);
            visitedClasses.add(subclassQName);
            cacheNavigator.walkSuperClasses(subclassQName, new ClassInfoProcessor() {
              public boolean process(int superclassQName) throws CacheCorruptedException {
                if (visitedClasses.contains(superclassQName)) {
                  return false;
                }
                markUseDependenciesOnFields(superclassQName, fieldNames);
                visitedClasses.add(superclassQName);
                return true;
              }
            });
            return true;
          }
        });
      }

      if (methodsToCheck.size() > 0) {
        cacheNavigator.walkSuperClasses(myQName, new ClassInfoProcessor() {
          public boolean process(int classQName) throws CacheCorruptedException {
            markUseDependenciesOnEquivalentMethods(classQName, methodsToCheck, myQName);
            return true;
          }
        });

        cacheNavigator.walkSubClasses(myQName, new ClassInfoProcessor() {
          public boolean process(int classQName) throws CacheCorruptedException {
            markUseDependenciesOnEquivalentMethods(classQName, methodsToCheck, myQName);
            return true;
          }
        });
      }
    }
  }

  private static final int[] ALL_TARGETS = new int[] {
    AnnotationTargets.ANNOTATION_TYPE,
    AnnotationTargets.CONSTRUCTOR,
    AnnotationTargets.FIELD,
    AnnotationTargets.LOCAL_VARIABLE,
    AnnotationTargets.METHOD,
    AnnotationTargets.PACKAGE,
    AnnotationTargets.PARAMETER,
    AnnotationTargets.TYPE
  };
  private boolean wereAnnotationTargesRemoved(final Cache oldCache, final Cache newCache) throws CacheCorruptedException {
    final int oldAnnotationTargets = MakeUtil.getAnnotationTargets(oldCache, myQName, myDependencyCache.getSymbolTable());
    final int newAnnotationTargets = MakeUtil.getAnnotationTargets(newCache, myQName, myDependencyCache.getSymbolTable());
    if (oldAnnotationTargets == newAnnotationTargets) {
      return false;
    }
    for (final int target : ALL_TARGETS) {
      if ((oldAnnotationTargets & target) != 0 && (newAnnotationTargets & target) == 0) {
        return true;
      }
    }
    return false;
  }

  private boolean hasRetentionPolicyChanged(final Cache oldCache, final Cache newCache) throws CacheCorruptedException {
    // if retention policy changed from SOURCE to CLASS or RUNTIME, all sources should be recompiled to propagate changes
    final int oldPolicy = MakeUtil.getAnnotationRetentionPolicy(myQName, oldCache, myDependencyCache.getSymbolTable());
    final int newPolicy = MakeUtil.getAnnotationRetentionPolicy(myQName, newCache, myDependencyCache.getSymbolTable());
    if ((oldPolicy == RetentionPolicies.SOURCE) && (newPolicy == RetentionPolicies.CLASS || newPolicy == RetentionPolicies.RUNTIME)) {
      return true;
    }
    if ((oldPolicy == RetentionPolicies.CLASS) && (newPolicy == RetentionPolicies.RUNTIME)) {
      return true;
    }
    return false;
  }

  private void markAll(Dependency[] backDependencies, @NonNls String reason) throws CacheCorruptedException {
    for (Dependency backDependency : backDependencies) {
      if (myDependencyCache.markTargetClassInfo(backDependency)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Mark dependent class " + myDependencyCache.resolve(backDependency.getClassQualifiedName()) + reason);
        }
      }
    }
  }

  private void extractFieldNames(Collection fromCollection, TIntHashSet toCollection) {
    for (Iterator it = fromCollection.iterator(); it.hasNext();) {
      MemberInfo memberInfo = (MemberInfo)it.next();
      if (memberInfo instanceof FieldInfo) {
        toCollection.add(memberInfo.getName());
      }
    }
  }

  private void extractMethods(Collection fromCollection, Collection toCollection, boolean includeConstructors) {
    for (Iterator it = fromCollection.iterator(); it.hasNext();) {
      MemberInfo memberInfo = (MemberInfo)it.next();
      if (memberInfo instanceof MethodInfo) {
        if (includeConstructors) {
          toCollection.add(memberInfo);
        }
        else {
          if (!((MethodInfo)memberInfo).isConstructor()) {
            toCollection.add(memberInfo);
          }
        }
      }
    }
  }

  private boolean processClassBecameAbstract(Dependency dependency) throws CacheCorruptedException {
    MemberInfo[] usedMembers = dependency.getUsedMembers();
    for (int i = 0; i < usedMembers.length; i++) {
      MemberInfo usedMember = usedMembers[i];
      if (usedMember instanceof MethodInfo && ((MethodInfo)usedMember).isConstructor()) {
        if (myDependencyCache.markTargetClassInfo(dependency)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Mark dependent class "+myDependencyCache.resolve(dependency.getClassQualifiedName()) + "; reason: " + myDependencyCache.resolve(myQName) + " made abstract");
          }
        }
        return true;
      }
    }
    return false;
  }

  private boolean isDependentOnRemovedMembers(Dependency dependency) {
    MemberInfo[] usedMembers = dependency.getUsedMembers();
    for (int idx = 0; idx < usedMembers.length; idx++) {
      MemberInfo usedMember = usedMembers[idx];
      if (myRemovedMembers.contains(usedMember)) {
        return true;
      }
    }
    return false;
  }

  private boolean isDependentOnChangedMembers(Dependency dependency) {
    MemberInfo[] usedMembers = dependency.getUsedMembers();
    for (int idx = 0; idx < usedMembers.length; idx++) {
      final MemberInfo usedMember = usedMembers[idx];
      if (myChangedMembers.contains(usedMember)) {
        if (usedMember instanceof MethodInfo) {
          final MethodChangeDescription changeDescription = (MethodChangeDescription)myChangeDescriptions.get(usedMember);
          if (changeDescription.returnTypeDescriptorChanged       ||
              changeDescription.returnTypeGenericSignatureChanged ||
              changeDescription.throwsListChanged                 ||
              changeDescription.staticPropertyChanged             ||
              changeDescription.accessRestricted) {
            return true;
          }
        }
        else { // FieldInfo
          return true;
        }
      }
    }
    return false;
  }

  private boolean isDependentOnEquivalentMethods(MethodInfo[] checkedMembers, Set members) throws CacheCorruptedException {
    // check if 'members' contains method with the same name and the same numbers of parameters, but with different types
    if (members.size() == 0) return false; // optimization
    for (int idx = 0; idx < checkedMembers.length; idx++) {
      MethodInfo checkedMethod = checkedMembers[idx];
      if (hasEquivalentMethod(members, checkedMethod)) {
        return true;
      }
    }
    return false;
  }

  private void markUseDependenciesOnEquivalentMethods(final int checkedInfoQName, Set methodsToCheck, int methodsClassName) throws CacheCorruptedException {
    Dependency[] backDependencies = myDependencyCache.getCache().getBackDependencies(checkedInfoQName);
    for (int idx = 0; idx < backDependencies.length; idx++) {
      Dependency dependency = backDependencies[idx];
      if (myDependencyCache.isTargetClassInfoMarked(dependency)) continue;
      if (isDependentOnEquivalentMethods(dependency.getUsedMethods(), methodsToCheck)) {
        if (myDependencyCache.markTargetClassInfo(dependency)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Mark dependent class "+myDependencyCache.resolve(dependency.getClassQualifiedName()) + "; reason: more specific methods added to " + myDependencyCache.resolve(methodsClassName));
          }
        }
        myDependencyCache.addClassToUpdate(checkedInfoQName);
      }
    }
  }

  private void markUseDependenciesOnFields(final int classQName, TIntHashSet fieldNames) throws CacheCorruptedException {
    final Cache oldCache = myDependencyCache.getCache();
    Dependency[] backDependencies = oldCache.getBackDependencies(classQName);
    for (int j = 0; j < backDependencies.length; j++) {
      Dependency useDependency = backDependencies[j];
      if (!myDependencyCache.isTargetClassInfoMarked(useDependency)) {
        FieldInfo[] usedFields = useDependency.getUsedFields();
        for (int idx = 0; idx < usedFields.length; idx++) {
          FieldInfo field = usedFields[idx];
          if (fieldNames.contains(field.getName())) {
            if (myDependencyCache.markTargetClassInfo(useDependency)) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Mark dependent class "+myDependencyCache.resolve(useDependency.getClassQualifiedName()) + "; reason: conflicting fields were added to the hierarchy of the class " + myDependencyCache.resolve(classQName));
              }
            }
            myDependencyCache.addClassToUpdate(classQName);
            break; // stop iterating fields
          }
        }
      }
    }
  }

  private void processInheritanceDependencies(boolean hasRemovedMethods) throws CacheCorruptedException {
    final Cache oldCache = myDependencyCache.getCache();
    final Cache newCache = myDependencyCache.getNewClassesCache();

    boolean becameFinal = !CacheUtils.isFinal(oldCache, myQName) && CacheUtils.isFinal(newCache, myQName);
    final SymbolTable symbolTable = myDependencyCache.getSymbolTable();

    final Set removedConcreteMethods = fetchNonAbstractMethods(myRemovedMembers);
    final int[] subclasses = oldCache.getSubclasses(oldCache.getClassId(myQName));
    for (int idx = 0; idx < subclasses.length/*myBackDependencies.length*/; idx++) {
      final int subclassQName = subclasses[idx];
      if (myDependencyCache.isClassInfoMarked(subclassQName)) {
        continue;
      }

      int subclassId = oldCache.getClassId(subclassQName);
      if (subclassId == Cache.UNKNOWN) {
        continue;
      }

      if (hasRemovedMethods && myIsRemoteInterface && !CacheUtils.isInterface(oldCache, subclassQName)) {
        if (myDependencyCache.markClass(subclassQName)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: methods were removed from remote interface: "+myDependencyCache.resolve(myQName));
          }
        }
        continue;
      }

      if (mySuperClassAdded || mySuperInterfaceAdded) {
        if (myDependencyCache.markClass(subclassQName)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: the superlist of "+myDependencyCache.resolve(myQName)+ " is changed");
          }
        }
        continue;
      }

      // if info became final, mark direct inheritors
      if (becameFinal) {
        if (myQName == oldCache.getSuperQualifiedName(subclassId)) {
          if (myDependencyCache.markClass(subclassQName)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: the class "+myDependencyCache.resolve(myQName)+ " was made final");
            }
          }
          continue;
        }
      }

      // process added members
      for (Iterator it = myAddedMembers.iterator(); it.hasNext();) {
        final MemberInfo member = (MemberInfo)it.next();
        if (member instanceof MethodInfo) {
          final MethodInfo method = (MethodInfo)member;
          if (method.isAbstract()) {
            // all derived classes should be marked in case an abstract method was added
            if (myDependencyCache.markClass(subclassQName)) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: added abstract method to " + myDependencyCache.resolve(myQName));
              }
            }
            break;
          }
          if (!method.isPrivate()) {
            int derivedMethod = CacheUtils.findMethodBySignature(oldCache, oldCache.getClassDeclarationId(subclassQName), method.getDescriptor(symbolTable), symbolTable);
            if (derivedMethod != Cache.UNKNOWN) {
              if (!method.getReturnTypeDescriptor(symbolTable).equals(CacheUtils.getMethodReturnTypeDescriptor(oldCache, derivedMethod, symbolTable))) {
                if (myDependencyCache.markClass(subclassQName)) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: return types of method " + method + " in base and derived classes are different");
                  }
                }
                break;
              }
              if (MakeUtil.isMoreAccessible(method.getFlags(), oldCache.getMethodFlags(derivedMethod))) {
                if (myDependencyCache.markClass(subclassQName)) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: the method " + method + " in derived class is less accessible than in base class");
                  }
                }
                break;
              }
              if (!CacheUtils.areArraysContentsEqual(method.getThrownExceptions(), oldCache.getMethodThrownExceptions(derivedMethod))) {
                if (myDependencyCache.markClass(subclassQName)) {
                  if (LOG.isDebugEnabled()) {
                    LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: exception lists of " + method + " in base and derived classes are different");
                  }
                }
                break;
              }
            }
            if (hasGenericsNameClashes(method, oldCache, subclassQName)) {
              if (myDependencyCache.markClass(subclassQName)) {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: found method with the same name, different generic signature, but the same erasure as " + method);
                }
              }
              break;
            }
          }
        }
        else if (member instanceof FieldInfo){
          if (CacheUtils.findFieldByName(oldCache, oldCache.getClassDeclarationId(subclassQName), member.getName()) != Cache.UNKNOWN) {
            if (myDependencyCache.markClass(subclassQName)) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: added field " + member + " to base class");
              }
            }
            break;
          }
        }
      }

      // process changed members
      for (Iterator it = myChangedMembers.iterator(); it.hasNext();) {
        final MemberInfo changedMember = (MemberInfo)it.next();
        if (changedMember instanceof MethodInfo) {
          final MethodInfo oldMethod = (MethodInfo)changedMember;
          MethodChangeDescription changeDescription = (MethodChangeDescription)myChangeDescriptions.get(oldMethod);
          if (changeDescription.becameAbstract) {
            if (!ClsUtil.isAbstract(oldCache.getFlags(subclassId))) { // if the subclass was not abstract
              if (myDependencyCache.markClass(subclassQName)) {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: changed base method " + oldMethod);
                }
              }
              break;
            }
          }
          int derivedMethod = CacheUtils.findMethodBySignature(oldCache, oldCache.getClassDeclarationId(subclassQName), oldMethod.getDescriptor(symbolTable), symbolTable);
          if (derivedMethod != Cache.UNKNOWN) {
            if (myDependencyCache.markClass(subclassQName)) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: changed base method " + oldMethod);
              }
            }
            break;
          }
        }
      }

      if (!ClsUtil.isAbstract(oldCache.getFlags(subclassId))) {
        if (hasUnimplementedAbstractMethods(subclassQName, new HashSet(removedConcreteMethods))) {
          if (myDependencyCache.markClass(subclassQName)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Mark dependent class "+myDependencyCache.resolve(subclassQName) + "; reason: the class should be declared abstract because abstract method implementation was removed from its superclass: " + myDependencyCache.resolve(myQName));
            }
          }
        }
      }
    }
  }

  private boolean hasGenericsNameClashes(final MethodInfo baseMethod, final Cache oldCache, final int subclassQName) throws CacheCorruptedException {
    // it is illegal if 2 methods in a hierarchy have 1) same name 2) different signatures 3) same erasure
    final int[] methods = CacheUtils.findMethodsByName(oldCache, oldCache.getClassDeclarationId(subclassQName), baseMethod.getName());
    if (methods.length > 0) {
      for (int i = 0; i < methods.length; i++) {
        final int methodInSubclass = methods[i];
        if (ClsUtil.isBridge(oldCache.getMethodFlags(methodInSubclass))) {
          continue;
        }
        if (baseMethod.getDescriptor() == oldCache.getMethodDescriptor(methodInSubclass) &&
            baseMethod.getGenericSignature() != oldCache.getMethodGenericSignature(methodInSubclass)) {
          return true;
        }
      }
    }
    return false;
  }

  private Set fetchNonAbstractMethods(Set membersToCheck) {
    final Set methodsToCheck = new HashSet();
    for (Iterator it = membersToCheck.iterator(); it.hasNext();) {
      final MemberInfo memberInfo = (MemberInfo)it.next();
      if (memberInfo instanceof MethodInfo) {
        final MethodInfo methodInfo = (MethodInfo)memberInfo;
        if (!methodInfo.isAbstract() && !methodInfo.isConstructor()) {
          methodsToCheck.add(memberInfo);
        }
      }
    }
    return methodsToCheck;
  }

  private boolean hasUnimplementedAbstractMethods(int superQName, final Set methodsToCheck) throws CacheCorruptedException {
    if (myDependencyCache.getCache().getClassId(superQName) != Cache.UNKNOWN) {
      if (hasBaseAbstractMethods(superQName, methodsToCheck)) {
        return true;
      }
      return hasBaseAbstractMethodsInHierarchy(superQName, methodsToCheck);
    }
    else {
      final String qName = myDependencyCache.resolve(superQName);
      if (!"java.lang.Object".equals(qName)) {
        if (hasBaseAbstractMethods2(qName, methodsToCheck)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean hasBaseAbstractMethodsInHierarchy(int fromClassQName, final Set methodsToCheck) throws CacheCorruptedException {
    if (fromClassQName == Cache.UNKNOWN || methodsToCheck.size() == 0) {
      return false;
    }
    final Cache cache = myDependencyCache.getCache();
    int superName = cache.getSuperQualifiedName(cache.getClassId(fromClassQName));
    if (superName != Cache.UNKNOWN) {
      if (hasUnimplementedAbstractMethods(superName, methodsToCheck)) {
        return true;
      }
    }
    if (methodsToCheck.size() == 0) {
      return false;
    }
    int[] superInterfaces = cache.getSuperInterfaces(cache.getClassId(fromClassQName));
    for (int idx = 0; idx < superInterfaces.length; idx++) {
      if (hasUnimplementedAbstractMethods(superInterfaces[idx], methodsToCheck)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasBaseAbstractMethods(int qName, Set methodsToCheck) throws CacheCorruptedException {
    final SymbolTable symbolTable = myDependencyCache.getSymbolTable();
    final Cache oldCache = myDependencyCache.getCache();
    final Cache newCache = myDependencyCache.getNewClassesCache();
    final Cache cache = newCache.getClassId(qName) != Cache.UNKNOWN? newCache : oldCache; // use recompiled version (if any) for searching methods
    for (Iterator it = methodsToCheck.iterator(); it.hasNext();) {
      final MethodInfo methodInfo = (MethodInfo)it.next();
      final int superMethod = CacheUtils.findMethodBySignature(cache, cache.getClassDeclarationId(qName), methodInfo.getDescriptor(symbolTable), symbolTable);
      if (superMethod != Cache.UNKNOWN) {
        if (ClsUtil.isAbstract(cache.getMethodFlags(superMethod))) {
          return true;
        }
        it.remove();
      }
    }
    return false;
  }

  // search using PSI
  private boolean hasBaseAbstractMethods2(final String qName, final Set methodsToCheck) throws CacheCorruptedException {
    final boolean[] found = new boolean[] {false};
    final CacheCorruptedException ex = ApplicationManager.getApplication().runReadAction(new Computable<CacheCorruptedException>() {
      public CacheCorruptedException compute() {
        try {
          final PsiManager psiManager = PsiManager.getInstance(myProject);
          final PsiClass aClass = psiManager.findClass(qName, GlobalSearchScope.allScope(myProject));
          if (aClass == null) {
            return null;
          }
          final PsiElementFactory factory = psiManager.getElementFactory();
          final PsiNameHelper nameHelper = PsiManager.getInstance(myProject).getNameHelper();
          for (Iterator it = methodsToCheck.iterator(); it.hasNext();) {
            final MethodInfo methodInfo = (MethodInfo)it.next();
            if (!nameHelper.isIdentifier(myDependencyCache.resolve(methodInfo.getName()), LanguageLevel.JDK_1_3)) { // fix for SCR 16068
              continue;
            }
            // language level 1.3 will prevent exceptions from PSI if there are methods named "assert"
            final PsiMethod methodPattern = factory.createMethodFromText(getMethodText(methodInfo), null, LanguageLevel.JDK_1_3);
            final PsiMethod superMethod = aClass.findMethodBySignature(methodPattern, true);
            if (superMethod != null) {
              if (superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
                found[0] = true;
                return null;
              }
              it.remove();
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        catch (CacheCorruptedException e) {
          return e;
        }
        return null;
      }
    });
    if (ex != null) {
      throw ex;
    }
    return found[0];
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private @NonNls String getMethodText(MethodInfo methodInfo) throws CacheCorruptedException {
    final SymbolTable symbolTable = myDependencyCache.getSymbolTable();
    StringBuffer text = new StringBuffer(16);
    final String returnType = Utility.signatureToString(methodInfo.getReturnTypeDescriptor(symbolTable));
    text.append(returnType);
    text.append(" ");
    text.append(myDependencyCache.resolve(methodInfo.getName()));
    text.append("(");
    final String[] parameterSignatures = methodInfo.getParameterDescriptors(symbolTable);
    for (int idx = 0; idx < parameterSignatures.length; idx++) {
      String parameterSignature = parameterSignatures[idx];
      if (idx > 0) {
        text.append(",");
      }
      text.append(Utility.signatureToString(parameterSignature));
      text.append(" arg");
      text.append(idx);
    }
    text.append(")");
    return text.toString();
  }

  private static boolean wereInterfacesRemoved(int[] oldInterfaces, int[] newInterfaces) {
    for (int oldInterface : oldInterfaces) {
      boolean found = false;
      for (int newInterface : newInterfaces) {
        found = (oldInterface == newInterface);
        if (found) {
          break;
        }
      }
      if (!found) {
        return true;
      }
    }
    return false;
  }

  private void addAddedMembers(int qName, Cache oldCache, Cache newCache, Collection members) throws CacheCorruptedException {
    int[] newFields = newCache.getFieldIds(newCache.getClassDeclarationId(qName));
    for (int newField : newFields) {
      if (CacheUtils.findFieldByName(oldCache, oldCache.getClassDeclarationId(qName), newCache.getFieldName(newField)) == Cache.UNKNOWN) {
        members.add(newCache.createFieldInfo(newField));
      }
    }
    int[] newMethods = newCache.getMethodIds(newCache.getClassDeclarationId(qName));
    final SymbolTable symbolTable = myDependencyCache.getSymbolTable();
    for (int idx = 0; idx < newMethods.length; idx++) {
      int newMethod = newMethods[idx];
      final int name = newCache.getMethodName(newMethod);
      final int methodDescriptor = newCache.getMethodDescriptor(newMethod);
      final String methodSignature = CacheUtils.getMethodSignature(symbolTable.getSymbol(name), symbolTable.getSymbol(methodDescriptor));
      if (CacheUtils.findMethodBySignature(oldCache, oldCache.getClassDeclarationId(myQName), methodSignature, symbolTable) == Cache.UNKNOWN) {
        members.add(newCache.createMethodInfo(newMethod));
      }
    }
  }

  private void addRemovedMembers(int qName, Cache oldCache, Cache newCache, Collection members) throws CacheCorruptedException {
    addAddedMembers(qName, newCache, oldCache, members);
  }

  private void addChangedMembers(final int qName, Cache oldCache, Cache newCache, Collection members) throws CacheCorruptedException {
    int[] oldFields = oldCache.getFieldIds(oldCache.getClassDeclarationId(qName));
    for (int idx = 0; idx < oldFields.length; idx++) {
      final int oldField = oldFields[idx];
      final int newField = CacheUtils.findFieldByName(newCache, newCache.getClassDeclarationId(qName), oldCache.getFieldName(oldField));
      if (newField != Cache.UNKNOWN) {
        final FieldChangeDescription changeDescription = new FieldChangeDescription(oldCache, newCache, oldField, newField);
        if (changeDescription.isChanged()) {
          final FieldInfo fieldInfo = oldCache.createFieldInfo(oldField);
          members.add(fieldInfo);
          myChangeDescriptions.put(fieldInfo, changeDescription);
        }
      }
    }
    int[] oldMethods = oldCache.getMethodIds(oldCache.getClassDeclarationId(qName));
    final SymbolTable symbolTable = myDependencyCache.getSymbolTable();
    for (int idx = 0; idx < oldMethods.length; idx++) {
      final int oldMethod = oldMethods[idx];
      final int name = oldCache.getMethodName(oldMethod);
      final int methodDescriptor = oldCache.getMethodDescriptor(oldMethod);
      final String signature = CacheUtils.getMethodSignature(symbolTable.getSymbol(name), symbolTable.getSymbol(methodDescriptor));
      final int newMethod = CacheUtils.findMethodBySignature(newCache, newCache.getClassDeclarationId(qName), signature, symbolTable);
      if (newMethod != Cache.UNKNOWN) {
        final MethodChangeDescription changeDescription = new MethodChangeDescription(oldCache, newCache, oldMethod, newMethod, symbolTable);
        if (changeDescription.isChanged()) {
          final MethodInfo methodInfo = oldCache.createMethodInfo(oldMethod);
          members.add(methodInfo);
          myChangeDescriptions.put(methodInfo, changeDescription);
        }
      }
    }
  }

  private boolean hasEquivalentMethod(Collection members, MethodInfo modelMethod) throws CacheCorruptedException {
    final String[] modelSignature = modelMethod.getParameterDescriptors(myDependencyCache.getSymbolTable());
    for (Iterator it = members.iterator(); it.hasNext();) {
      MemberInfo member = (MemberInfo)it.next();

      if (!(member instanceof MethodInfo)) continue;
      final MethodInfo method = (MethodInfo)member;

      if (modelMethod.getName() != method.getName()) continue;
      String[] methodSignature = method.getParameterDescriptors(myDependencyCache.getSymbolTable());

      if (modelSignature.length != methodSignature.length) continue;

      for (int i = 0; i < methodSignature.length; i++) {
        if (!methodSignature[i].equals(modelSignature[i])) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Equivalent: " + modelMethod.getDescriptor(myDependencyCache.getSymbolTable()) + " <=> " + method.getDescriptor(myDependencyCache.getSymbolTable()));
          }
          return true;
        }
      }
    }
    return false;
  }

}
