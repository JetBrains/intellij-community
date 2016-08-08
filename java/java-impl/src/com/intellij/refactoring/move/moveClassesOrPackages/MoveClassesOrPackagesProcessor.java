/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.classRefs.ClassInstanceScanner;
import com.intellij.refactoring.util.classRefs.ClassReferenceScanner;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Jeka,dsl
 */
public class MoveClassesOrPackagesProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor");

  private final PsiElement[] myElementsToMove;
  private boolean mySearchInComments;
  private boolean mySearchInNonJavaFiles;
  private final PackageWrapper myTargetPackage;
  private final MoveCallback myMoveCallback;
  protected @NotNull final MoveDestination myMoveDestination;
  protected NonCodeUsageInfo[] myNonCodeUsages;
  private boolean myOpenInEditor;

  public MoveClassesOrPackagesProcessor(Project project,
                                        PsiElement[] elements,
                                        @NotNull final MoveDestination moveDestination,
                                        boolean searchInComments,
                                        boolean searchInNonJavaFiles,
                                        MoveCallback moveCallback) {
    super(project);
    final Set<PsiElement> toMove = new LinkedHashSet<>();
    for (PsiElement element : elements) {
      if (element instanceof PsiClassOwner) {
        Collections.addAll(toMove, ((PsiClassOwner)element).getClasses());
      } else {
        toMove.add(element);
      }
    }
    myElementsToMove = PsiUtilCore.toPsiElementArray(toMove);
    Arrays.sort(myElementsToMove, (o1, o2) -> {
      if (o1 instanceof PsiClass && o2 instanceof PsiClass) {
        final PsiFile containingFile = o1.getContainingFile();
        if (Comparing.equal(containingFile, o2.getContainingFile())) {
          final VirtualFile virtualFile = containingFile.getVirtualFile();
          if (virtualFile != null) {
            final String fileName = virtualFile.getNameWithoutExtension();
            if (Comparing.strEqual(fileName, ((PsiClass)o1).getName())) return -1;
            if (Comparing.strEqual(fileName, ((PsiClass)o2).getName())) return 1;
          }
        }
      }
      return 0;
    });
    myMoveDestination = moveDestination;
    myTargetPackage = myMoveDestination.getTargetPackage();
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myMoveCallback = moveCallback;
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    PsiElement[] elements = new PsiElement[myElementsToMove.length];
    System.arraycopy(myElementsToMove, 0, elements, 0, myElementsToMove.length);
    return new MoveMultipleElementsViewDescriptor(elements, MoveClassesOrPackagesUtil.getPackageName(myTargetPackage));
  }

  public boolean verifyValidPackageName() {
    String qName = myTargetPackage.getQualifiedName();
    if (!StringUtil.isEmpty(qName)) {
      PsiNameHelper helper = PsiNameHelper.getInstance(myProject);
      if (!helper.isQualifiedName(qName)) {
        Messages.showMessageDialog(myProject, RefactoringBundle.message("invalid.target.package.name.specified"), "Invalid Package Name",
                                   Messages.getErrorIcon());
        return false;
      }
    }
    return true;
  }

  private boolean hasClasses() {
    for (PsiElement element : getElements()) {
      if (element instanceof PsiClass) return true;
    }
    return false;
  }

  public boolean isSearchInComments() {
    return mySearchInComments;
  }

  public boolean isSearchInNonJavaFiles() {
    return mySearchInNonJavaFiles;
  }

  public void setSearchInComments(boolean searchInComments) {
    mySearchInComments = searchInComments;
  }

  public void setSearchInNonJavaFiles(boolean searchInNonJavaFiles) {
    mySearchInNonJavaFiles = searchInNonJavaFiles;
  }


  @NotNull
  protected UsageInfo[] findUsages() {
    final List<UsageInfo> allUsages = new ArrayList<>();
    final List<UsageInfo> usagesToSkip = new ArrayList<>();
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    for (PsiElement element : myElementsToMove) {
      String newName = getNewQName(element);
      if (newName == null) continue;
      final UsageInfo[] usages = MoveClassesOrPackagesUtil.findUsages(element, mySearchInComments,
                                                                      mySearchInNonJavaFiles, newName);
      final ArrayList<UsageInfo> infos = new ArrayList<>(Arrays.asList(usages));
      allUsages.addAll(infos);
      if (Comparing.strEqual(newName, getOldQName(element))) {
        usagesToSkip.addAll(infos);
      }

      if (element instanceof PsiPackage) {
        for (PsiDirectory directory : ((PsiPackage)element).getDirectories()) {
          final UsageInfo[] dirUsages = MoveClassesOrPackagesUtil.findUsages(directory, mySearchInComments,
                                                                             mySearchInNonJavaFiles, newName);
          allUsages.addAll(new ArrayList<>(Arrays.asList(dirUsages)));
        }
      }
    }
    myMoveDestination.analyzeModuleConflicts(Arrays.asList(myElementsToMove), conflicts,
                                             allUsages.toArray(new UsageInfo[allUsages.size()]));
    final UsageInfo[] usageInfos = allUsages.toArray(new UsageInfo[allUsages.size()]);
    detectPackageLocalsMoved(usageInfos, conflicts);
    detectPackageLocalsUsed(conflicts);
    if (!conflicts.isEmpty()) {
      for (PsiElement element : conflicts.keySet()) {
        allUsages.add(new ConflictsUsageInfo(element, conflicts.get(element)));
      }
    }

    allUsages.removeAll(usagesToSkip);
    return UsageViewUtil.removeDuplicatedUsages(allUsages.toArray(new UsageInfo[allUsages.size()]));
  }

  public List<PsiElement> getElements() {
    return Collections.unmodifiableList(Arrays.asList(myElementsToMove));
  }

  public PackageWrapper getTargetPackage() {
    return myMoveDestination.getTargetPackage();
  }

  public void setOpenInEditor(boolean openInEditor) {
    myOpenInEditor = openInEditor;
  }

  protected static class ConflictsUsageInfo extends UsageInfo {
    private final Collection<String> myConflicts;

    public ConflictsUsageInfo(PsiElement pseudoElement, Collection<String> conflicts) {
      super(pseudoElement);
      myConflicts = conflicts;
    }

    public Collection<String> getConflicts() {
      return myConflicts;
    }
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.move";
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(myElementsToMove);
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(@NotNull UsageInfo[] usages) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(myTargetPackage.getDirectories());
    data.addElement(JavaPsiFacade.getInstance(myProject).findPackage(myTargetPackage.getQualifiedName()));
    return data;
  }

  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usages = refUsages.get();
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    ArrayList<UsageInfo> filteredUsages = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof ConflictsUsageInfo) {
        final ConflictsUsageInfo info = (ConflictsUsageInfo)usage;
        final PsiElement element = info.getElement();
        conflicts.putValues(element, info.getConflicts());
      }
      else {
        filteredUsages.add(usage);
      }
    }

    refUsages.set(filteredUsages.toArray(new UsageInfo[filteredUsages.size()]));
    return showConflicts(conflicts, usages);
  }

  private boolean isInsideMoved(PsiElement place) {
    for (PsiElement element : myElementsToMove) {
      if (element instanceof PsiClass) {
        if (PsiTreeUtil.isAncestor(element, place, false)) return true;
      }
    }
    return false;
  }

  private void detectPackageLocalsUsed(final MultiMap<PsiElement, String> conflicts) {
    PackageLocalsUsageCollector visitor = new PackageLocalsUsageCollector(myElementsToMove, myTargetPackage, conflicts);

    for (PsiElement element : myElementsToMove) {
      if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element;
        aClass.accept(visitor);
      }
    }
  }

  private void detectPackageLocalsMoved(final UsageInfo[] usages, final MultiMap<PsiElement, String> conflicts) {
//    final HashSet reportedPackageLocalUsed = new HashSet();
    final HashSet<PsiClass> movedClasses = new HashSet<>();
    final HashMap<PsiClass,HashSet<PsiElement>> reportedClassToContainers = new HashMap<>();
    final PackageWrapper aPackage = myTargetPackage;
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;
      if (usage instanceof MoveRenameUsageInfo && !(usage instanceof NonCodeUsageInfo) &&
          ((MoveRenameUsageInfo)usage).getReferencedElement() instanceof PsiClass) {
        PsiClass aClass = (PsiClass)((MoveRenameUsageInfo)usage).getReferencedElement();
        if (!movedClasses.contains(aClass)) {
          movedClasses.add(aClass);
        }
        String visibility = VisibilityUtil.getVisibilityModifier(aClass.getModifierList());
        if (PsiModifier.PACKAGE_LOCAL.equals(visibility)) {
          if (PsiTreeUtil.getParentOfType(element, PsiImportStatement.class) != null) continue;
          PsiElement container = ConflictsUtil.getContainer(element);
          HashSet<PsiElement> reported = reportedClassToContainers.get(aClass);
          if (reported == null) {
            reported = new HashSet<>();
            reportedClassToContainers.put(aClass, reported);
          }

          if (!reported.contains(container)) {
            reported.add(container);
            PsiFile containingFile = element.getContainingFile();
            if (containingFile != null && !isInsideMoved(element)) {
              PsiDirectory directory = containingFile.getContainingDirectory();
              if (directory != null) {
                PsiPackage usagePackage = JavaDirectoryService.getInstance().getPackage(directory);
                if (aPackage != null && usagePackage != null && !aPackage.equalToPackage(usagePackage)) {

                  final String message = RefactoringBundle.message("a.package.local.class.0.will.no.longer.be.accessible.from.1",
                                                                   CommonRefactoringUtil.htmlEmphasize(aClass.getName()),
                                                                   RefactoringUIUtil.getDescription(
                                                                   container, true));
                  conflicts.putValue(aClass, message);
                }
              }
            }
          }
        }
      }
    }

    final MyClassInstanceReferenceVisitor instanceReferenceVisitor = new MyClassInstanceReferenceVisitor(conflicts);
    for (final PsiClass aClass : movedClasses) {
      String visibility = VisibilityUtil.getVisibilityModifier(aClass.getModifierList());
      if (PsiModifier.PACKAGE_LOCAL.equals(visibility)) {
        findInstancesOfPackageLocal(aClass, usages, instanceReferenceVisitor);
      }
      else {
        // public classes
        findPublicClassConflicts(aClass, instanceReferenceVisitor);
      }
    }
  }

  static class ClassMemberWrapper {
    final PsiNamedElement myElement;
    final PsiModifierListOwner myMember;

    public ClassMemberWrapper(PsiNamedElement element) {
      myElement = element;
      myMember = (PsiModifierListOwner) element;
    }

    PsiModifierListOwner getMember() {
      return myMember;
    }


    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ClassMemberWrapper)) return false;

      ClassMemberWrapper wrapper = (ClassMemberWrapper)o;

      if (myElement instanceof PsiMethod) {
        return wrapper.myElement instanceof PsiMethod &&
            MethodSignatureUtil.areSignaturesEqual((PsiMethod) myElement, (PsiMethod) wrapper.myElement);
      }


      return Comparing.equal(myElement.getName(), wrapper.myElement.getName());
    }

    public int hashCode() {
      final String name = myElement.getName();
      if (name != null) {
        return name.hashCode();
      }
      else {
        return 0;
      }
    }
  }

  private static void findPublicClassConflicts(PsiClass aClass, final MyClassInstanceReferenceVisitor instanceReferenceVisitor) {
    //noinspection MismatchedQueryAndUpdateOfCollection
    NonPublicClassMemberWrappersSet members = new NonPublicClassMemberWrappersSet();

    members.addElements(aClass.getFields());
    members.addElements(aClass.getMethods());
    members.addElements(aClass.getInnerClasses());

    final RefactoringUtil.IsDescendantOf isDescendantOf = new RefactoringUtil.IsDescendantOf(aClass);
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(aClass.getContainingFile().getContainingDirectory());
    final GlobalSearchScope packageScope = aPackage == null ? aClass.getResolveScope() : PackageScope.packageScopeWithoutLibraries(aPackage, false);
    for (final ClassMemberWrapper memberWrapper : members) {
      ReferencesSearch.search(memberWrapper.getMember(), packageScope, false).forEach(reference -> {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiReferenceExpression) {
          final PsiReferenceExpression expression = (PsiReferenceExpression)element;
          final PsiExpression qualifierExpression = expression.getQualifierExpression();
          if (qualifierExpression != null) {
            final PsiType type = qualifierExpression.getType();
            if (type != null) {
              final PsiClass resolvedTypeClass = PsiUtil.resolveClassInType(type);
              if (isDescendantOf.value(resolvedTypeClass)) {
                instanceReferenceVisitor.visitMemberReference(memberWrapper.getMember(), expression, isDescendantOf);
              }
            }
          }
          else {
            instanceReferenceVisitor.visitMemberReference(memberWrapper.getMember(), expression, isDescendantOf);
          }
        }
        return true;
      });
    }
  }

  private static void findInstancesOfPackageLocal(final PsiClass aClass,
                                           final UsageInfo[] usages,
                                           final MyClassInstanceReferenceVisitor instanceReferenceVisitor) {
    ClassReferenceScanner referenceScanner = new ClassReferenceScanner(aClass) {
      public PsiReference[] findReferences() {
        ArrayList<PsiReference> result = new ArrayList<>();
        for (UsageInfo usage : usages) {
          if (usage instanceof MoveRenameUsageInfo && ((MoveRenameUsageInfo)usage).getReferencedElement() == aClass) {
            final PsiReference reference = usage.getReference();
            if (reference != null) {
              result.add(reference);
            }
          }
        }
        return result.toArray(new PsiReference[result.size()]);
      }
    };
    referenceScanner.processReferences(new ClassInstanceScanner(aClass, instanceReferenceVisitor));
  }


  @Nullable
  private String getNewQName(PsiElement element) {
    final String qualifiedName = myTargetPackage.getQualifiedName();
    if (element instanceof PsiClass) {
      return StringUtil.getQualifiedName(qualifiedName, ((PsiClass)element).getName());
    }
    else if (element instanceof PsiPackage) {
      return StringUtil.getQualifiedName(qualifiedName, ((PsiPackage)element).getName());
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }
  
  @Nullable
  private String getOldQName(PsiElement element) {
    if (element instanceof PsiClass) {
      return ((PsiClass)element).getQualifiedName();
    }
    else if (element instanceof PsiPackage) {
      return ((PsiPackage)element).getQualifiedName();
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  protected void refreshElements(@NotNull PsiElement[] elements) {
    LOG.assertTrue(elements.length == myElementsToMove.length);
    System.arraycopy(elements, 0, myElementsToMove, 0, elements.length);
  }

  protected boolean isPreviewUsages(@NotNull UsageInfo[] usages) {
    if (UsageViewUtil.reportNonRegularUsages(usages, myProject)) {
      return true;
    }
    else {
      return super.isPreviewUsages(usages);
    }
  }

  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    // If files are being moved then I need to collect some information to delete these
    // filese from CVS. I need to know all common parents of the moved files and releative
    // paths.

    // Move files with correction of references.

    try {
      final Map<PsiClass, Boolean> allClasses = new HashMap<>();
      for (PsiElement element : myElementsToMove) {
        if (element instanceof PsiClass) {
          final PsiClass psiClass = (PsiClass)element;
          if (allClasses.containsKey(psiClass)) {
            continue;
          }
          for (MoveAllClassesInFileHandler fileHandler : Extensions.getExtensions(MoveAllClassesInFileHandler.EP_NAME)) {
            fileHandler.processMoveAllClassesInFile(allClasses, psiClass, myElementsToMove);
          }
        }
      }

      for (PsiElement element : myElementsToMove) {
        if (element instanceof PsiClass) {
          MoveClassesOrPackagesUtil.prepareMoveClass((PsiClass)element);
        }
      }

      final Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<>();
      for (int idx = 0; idx < myElementsToMove.length; idx++) {
        PsiElement element = myElementsToMove[idx];
        final RefactoringElementListener elementListener = getTransaction().getElementListener(element);
        if (element instanceof PsiPackage) {
          final PsiDirectory[] directories = ((PsiPackage)element).getDirectories();
          final PsiPackage newElement = MoveClassesOrPackagesUtil.doMovePackage((PsiPackage)element, myMoveDestination);
          LOG.assertTrue(newElement != null, element);
          oldToNewElementsMapping.put(element, newElement);
          int i = 0;
          final PsiDirectory[] newDirectories = newElement.getDirectories();
          if (newDirectories.length == 1) {//everything is moved in one directory
            for (PsiDirectory directory : directories) {
              oldToNewElementsMapping.put(directory, newDirectories[0]);
            }
          } else {
            for (PsiDirectory directory : directories) {
              if (myMoveDestination.verify(directory) != null) {
                //e.g. directory is excluded so there is no source root for it, hence target directory would be missed from newDirectories
                continue;
              }

              oldToNewElementsMapping.put(directory, newDirectories[i++]);
            }
          }
          element = newElement;
        }
        else if (element instanceof PsiClass) {
          final PsiClass psiClass = (PsiClass)element;
          final PsiClass newElement = MoveClassesOrPackagesUtil.doMoveClass(psiClass, myMoveDestination.getTargetDirectory(element.getContainingFile()), allClasses.get(psiClass));
          oldToNewElementsMapping.put(element, newElement);
          element = newElement;
        } else {
          LOG.error("Unexpected element to move: " + element);
        }
        elementListener.elementMoved(element);
        myElementsToMove[idx] = element;
      }

      for (PsiElement element : myElementsToMove) {
        if (element instanceof PsiClass) {
          MoveClassesOrPackagesUtil.finishMoveClass((PsiClass)element);
        }
      }

      myNonCodeUsages = CommonMoveUtil.retargetUsages(usages, oldToNewElementsMapping);

      if (myOpenInEditor) {
        EditorHelper.openFilesInEditor(myElementsToMove);
      }
    }
    catch (IncorrectOperationException e) {
      myNonCodeUsages = new NonCodeUsageInfo[0];
      RefactoringUIUtil.processIncorrectOperation(myProject, e);
    }
  }

    protected void performPsiSpoilingRefactoring() {
    RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
    if (myMoveCallback != null) {
      if (myMoveCallback instanceof MoveClassesOrPackagesCallback) {
        ((MoveClassesOrPackagesCallback) myMoveCallback).classesOrPackagesMoved(myMoveDestination);
      }
      myMoveCallback.refactoringCompleted();
    }
  }

  protected String getCommandName() {
    String elements = RefactoringUIUtil.calculatePsiElementDescriptionList(myElementsToMove);
    String target = myTargetPackage.getQualifiedName();
    return RefactoringBundle.message("move.classes.command", elements, target);
  }

  private class MyClassInstanceReferenceVisitor implements ClassInstanceScanner.ClassInstanceReferenceVisitor {
    private final MultiMap<PsiElement, String> myConflicts;
    private final HashMap<PsiModifierListOwner,HashSet<PsiElement>> myReportedElementToContainer = new HashMap<>();
    private final HashMap<PsiClass, RefactoringUtil.IsDescendantOf> myIsDescendantOfCache = new HashMap<>();

    public MyClassInstanceReferenceVisitor(MultiMap<PsiElement, String> conflicts) {
      myConflicts = conflicts;
    }

    public void visitQualifier(PsiReferenceExpression qualified,
                               PsiExpression instanceRef,
                               PsiElement referencedInstance) {
      PsiElement resolved = qualified.resolve();

      if (resolved instanceof PsiMember) {
        final PsiMember member = (PsiMember)resolved;
        final PsiClass containingClass = member.getContainingClass();
        RefactoringUtil.IsDescendantOf isDescendantOf = myIsDescendantOfCache.get(containingClass);
        if (isDescendantOf == null) {
          isDescendantOf = new RefactoringUtil.IsDescendantOf(containingClass);
          myIsDescendantOfCache.put(containingClass, isDescendantOf);
        }
        visitMemberReference(member, qualified, isDescendantOf);
      }
    }

    private synchronized void visitMemberReference(final PsiModifierListOwner member, PsiReferenceExpression qualified, final RefactoringUtil.IsDescendantOf descendantOf) {
      if (member.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        visitPackageLocalMemberReference(qualified, member);
      } else if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
        final PsiExpression qualifier = qualified.getQualifierExpression();
        if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
          visitPackageLocalMemberReference(qualified, member);
        } else {
          if (!isInInheritor(qualified, descendantOf)) {
            visitPackageLocalMemberReference(qualified, member);
          }
        }
      }
    }

    private boolean isInInheritor(PsiReferenceExpression qualified, final RefactoringUtil.IsDescendantOf descendantOf) {
      PsiClass aClass = PsiTreeUtil.getParentOfType(qualified, PsiClass.class);
      while (aClass != null) {
        if (descendantOf.value(aClass)) return true;
        aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
      }
      return false;
    }

    private void visitPackageLocalMemberReference(PsiJavaCodeReferenceElement qualified, PsiModifierListOwner member) {
      PsiElement container = ConflictsUtil.getContainer(qualified);
      HashSet<PsiElement> reportedContainers = myReportedElementToContainer.get(member);
      if (reportedContainers == null) {
        reportedContainers = new HashSet<>();
        myReportedElementToContainer.put(member, reportedContainers);
      }

      if (!reportedContainers.contains(container)) {
        reportedContainers.add(container);
        if (!isInsideMoved(container)) {
          PsiFile containingFile = container.getContainingFile();
          if (containingFile != null) {
            PsiDirectory directory = containingFile.getContainingDirectory();
            if (directory != null) {
              PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
              if (!myTargetPackage.equalToPackage(aPackage)) {
                String message = RefactoringBundle.message("0.will.be.inaccessible.from.1", RefactoringUIUtil.getDescription(member, true),
                                                      RefactoringUIUtil.getDescription(container, true));
                myConflicts.putValue(member, CommonRefactoringUtil.capitalize(message));
              }
            }
          }
        }
      }
    }

    public void visitTypeCast(PsiTypeCastExpression typeCastExpression,
                              PsiExpression instanceRef,
                              PsiElement referencedInstance) {
    }

    public void visitReadUsage(PsiExpression instanceRef, PsiType expectedType, PsiElement referencedInstance) {
    }

    public void visitWriteUsage(PsiExpression instanceRef, PsiType assignedType, PsiElement referencedInstance) {
    }
  }

  private static class NonPublicClassMemberWrappersSet extends HashSet<ClassMemberWrapper> {
    public void addElement(PsiMember member) {
      final PsiNamedElement namedElement = (PsiNamedElement)member;
      if (member.hasModifierProperty(PsiModifier.PUBLIC)) return;
      if (member.hasModifierProperty(PsiModifier.PRIVATE)) return;
      add(new ClassMemberWrapper(namedElement));
    }

    public void addElements(PsiMember[] members) {
      for (PsiMember member : members) {
        addElement(member);
      }
    }
  }
}
