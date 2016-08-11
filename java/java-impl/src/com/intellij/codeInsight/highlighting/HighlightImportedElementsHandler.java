/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.NavigationItemListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author Bas Leijdekkers
 */
public class HighlightImportedElementsHandler extends HighlightUsagesHandlerBase<PsiMember> {

  private final PsiElement myTarget;
  private final PsiImportStatementBase myImportStatement;
  private final boolean myImportStatic;
  private Map<PsiMember,List<PsiElement>> myClassReferenceListMap;

  public HighlightImportedElementsHandler(Editor editor, PsiFile file, PsiElement target, PsiImportStatementBase importStatement) {
    super(editor, file);
    myTarget = target;
    myImportStatement = importStatement;
    myImportStatic = myImportStatement instanceof PsiImportStaticStatement;
  }

  @Override
  public List<PsiMember> getTargets() {
    final PsiJavaCodeReferenceElement importReference = myImportStatement.getImportReference();
    if (importReference == null) {
      return Collections.emptyList();
    }
    final PsiJavaFile javaFile = PsiTreeUtil.getParentOfType(importReference, PsiJavaFile.class);
    if (javaFile == null) {
      return Collections.emptyList();
    }
    final JavaResolveResult[] resolveResults = importReference.multiResolve(false);
    if (resolveResults.length == 0) {
      return Collections.emptyList();
    }
    final PsiElement[] importedElements = new PsiElement[resolveResults.length];
    for (int i = 0; i < resolveResults.length; i++) {
      final JavaResolveResult resolveResult = resolveResults[i];
      importedElements[i] = resolveResult.getElement();
    }
    final ReferenceCollector collector = new ReferenceCollector(importedElements, myImportStatement.isOnDemand(), myImportStatic);
    javaFile.accept(collector);
    myClassReferenceListMap = collector.getClassReferenceListMap();
    if (myClassReferenceListMap.isEmpty()) {
      return Collections.emptyList();
    }
    return new ArrayList<>(myClassReferenceListMap.keySet());
  }

  @Override
  protected void selectTargets(final List<PsiMember> targets, final Consumer<List<PsiMember>> selectionConsumer) {
    if (targets.isEmpty()) {
      selectionConsumer.consume(Collections.<PsiMember>emptyList());
      return;
    }
    if (targets.size() == 1) {
      selectionConsumer.consume(Collections.singletonList(targets.get(0)));
      return;
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      selectionConsumer.consume(targets);
      return;
    }
    Collections.sort(targets, new PsiMemberComparator());
    final List<Object> model = new ArrayList<>();
    model.add(CodeInsightBundle.message("highlight.thrown.exceptions.chooser.all.entry"));
    model.addAll(targets);
    final JList list = new JBList(model);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    final ListCellRenderer renderer = new NavigationItemListCellRenderer();
    list.setCellRenderer(renderer);
    final PopupChooserBuilder builder = new PopupChooserBuilder(list);
    builder.setFilteringEnabled(o -> {
      if (o instanceof PsiMember) {
        final PsiMember member = (PsiMember)o;
        return member.getName();
      }
      return o.toString();
    });
    if (myImportStatic) {
      builder.setTitle(CodeInsightBundle.message("highlight.imported.members.chooser.title"));
    } else {
      builder.setTitle(CodeInsightBundle.message("highlight.imported.classes.chooser.title"));
    }
    builder.setItemChoosenCallback(() -> {
      final int index= list.getSelectedIndex();
      if (index == 0) {
        selectionConsumer.consume(targets);
      }
      else {
        selectionConsumer.consume(Collections.singletonList(targets.get(index - 1)));
      }
    });
    final JBPopup popup = builder.createPopup();
    popup.showInBestPositionFor(myEditor);
  }

  @Override
  public void computeUsages(List<PsiMember> targets) {
    if (targets.isEmpty()) {
      buildStatusText("import", 0);
      return;
    }
    if (myClassReferenceListMap == null) {
      return;
    }
    addOccurrence(myTarget);
    for (PsiMember target : targets) {
      final List<PsiElement> elements = myClassReferenceListMap.get(target);
      for (PsiElement element : elements) {
        addOccurrence(element);
      }
    }
    buildStatusText("import", myReadUsages.size() - 1 /* exclude target */);
  }

  static class ReferenceCollector extends JavaRecursiveElementVisitor {

    private final Map<PsiMember, List<PsiElement>> classReferenceListMap = new HashMap<>();
    private final PsiElement[] myImportTargets;
    private final boolean myOnDemand;
    private final boolean myImportStatic;

    ReferenceCollector(@NotNull PsiElement[] importTargets, boolean onDemand, boolean importStatic) {
      this.myImportTargets = importTargets;
      this.myOnDemand = onDemand;
      this.myImportStatic = importStatic;
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (!myImportStatic && reference.getText().equals(reference.getQualifiedName())) {
        return;
      }
      PsiElement parent = reference.getParent();
      if (parent instanceof PsiImportStatementBase) {
        return;
      }
      while (parent instanceof PsiJavaCodeReferenceElement) {
        parent = parent.getParent();
        if (parent instanceof PsiImportStatementBase) {
          return;
        }
      }
      if (myImportStatic) {
        checkStaticImportReference(reference);
      }
      else {
        checkImportReference(reference);
      }
    }

    private void checkStaticImportReference(PsiJavaCodeReferenceElement reference) {
      if (reference.isQualified()) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiMethod) && !(target instanceof PsiClass) && !(target instanceof PsiField)) {
        return;
      }
      final PsiMember member = (PsiMember)target;
      for (PsiElement importTarget : myImportTargets) {
        if (importTarget instanceof PsiMethod) {
          if (member.equals(importTarget)) {
            addReference(member, reference);
          }
        }
        else if (importTarget instanceof PsiClass) {
          final PsiClass importClass = (PsiClass)importTarget;
          if (myOnDemand) {
            final PsiClass containingClass = member.getContainingClass();
            if (InheritanceUtil.isInheritorOrSelf(importClass, containingClass, true)) {
              addReference(member, reference);
            }
          }
          else {
            if (importTarget.equals(member)) {
              addReference(member, reference);
            }
          }
        }
      }
    }

    private void checkImportReference(PsiJavaCodeReferenceElement reference) {
      final PsiElement element = reference.resolve();
      if (!(element instanceof PsiClass)) {
        return;
      }
      final PsiClass referencedClass = (PsiClass)element;
      for (PsiElement importTarget : myImportTargets) {
        if (importTarget instanceof PsiPackage) {
          if (referencedClass.getContainingClass() != null) {
            return;
          }
          final PsiFile file = referencedClass.getContainingFile();
          if (!(file instanceof PsiJavaFile)) {
            return;
          }
          final PsiJavaFile javaFile = (PsiJavaFile)file;
          final PsiPackage aPackage = (PsiPackage)importTarget;
          final String packageName = aPackage.getQualifiedName();
          final String filePackage = javaFile.getPackageName();
          if (filePackage.equals(packageName)) {
            addReference(referencedClass, reference);
          }
        }
        else if (importTarget instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)importTarget;
          final String name = aClass.getQualifiedName();
          if (name == null) {
            return;
          }
          if (!myOnDemand) {
            if (name.equals(referencedClass.getQualifiedName())) {
              addReference(referencedClass, reference);
            }
          }
          else {
            final PsiClass containingClass = referencedClass.getContainingClass();
            if (containingClass == null) {
              return;
            }
            if (name.equals(containingClass.getQualifiedName())) {
              addReference(referencedClass, reference);
            }
          }
        }
      }
    }

    private void addReference(PsiMember referencedMember, PsiJavaCodeReferenceElement reference) {
      List<PsiElement> referenceList = classReferenceListMap.get(referencedMember);
      if (referenceList == null) {
        referenceList = new ArrayList<>();
        classReferenceListMap.put(referencedMember, referenceList);
      }
      referenceList.add(reference.getReferenceNameElement());
    }

    public Map<PsiMember, List<PsiElement>> getClassReferenceListMap() {
      return classReferenceListMap;
    }
  }

  static class PsiMemberComparator implements Comparator<PsiMember> {

    @Override
    public int compare(PsiMember member1, PsiMember member2) {
      final String name1 = member1.getName();
      if (name1 == null) {
        return -1;
      }
      final String name2 = member2.getName();
      if (name2 == null) {
        return 1;
      }
      final int i = name1.compareTo(name2);
      if (i != 0) {
        return i;
      }
      final PsiJavaFile file1 = (PsiJavaFile)member1.getContainingFile();
      final PsiJavaFile file2 = (PsiJavaFile)member2.getContainingFile();
      final String packageName1 = file1.getPackageName();
      final String packageName2 = file2.getPackageName();
      return packageName1.compareTo(packageName2);
    }
  }
}
