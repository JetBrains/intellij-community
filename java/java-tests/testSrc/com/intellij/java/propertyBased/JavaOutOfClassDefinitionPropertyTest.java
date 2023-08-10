// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.java.FieldElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;
import org.jetbrains.jetCheck.PropertyChecker;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class JavaOutOfClassDefinitionPropertyTest extends LightJavaCodeInsightFixtureTestCase {

  public void testMoveMethodOutOfClassAndBack() {
    Supplier<MadTestingAction> fileAction =
      MadTestingUtil.performOnFileContents(myFixture, PathManager.getHomePath(), f -> f.getName().endsWith(".java"),
                                           this::doTestMoveMethodOutOfClassAndBack);
    PropertyChecker.customized()
      .checkScenarios(fileAction);
  }

  private void doTestMoveMethodOutOfClassAndBack(@NotNull ImperativeCommand.Environment env, @NotNull VirtualFile file) {
    Project project = getProject();
    if (!isCompilable(project, file)) return;
    myFixture.openFileInEditor(file);
    PsiJavaFile psiJavaFile = ObjectUtils.tryCast(myFixture.getFile(), PsiJavaFile.class);
    if (psiJavaFile == null) return;
    PsiClass psiClass = findClass(file, psiJavaFile);
    // enum and annotation fields are not supported
    if (psiClass == null || psiClass.isEnum() || psiClass.isAnnotationType()) return;
    List<PsiMember> members = findMembers(psiClass);
    int nMembers = members.size();
    if (nMembers == 0) return;
    PsiMember psiMember = env.generateValue(Generator.sampledFrom(members.toArray(PsiMember.EMPTY_ARRAY)), "Selected member %s");
    SmartPsiElementPointer<PsiClass> classPtr = SmartPointerManager.createPointer(psiClass);
    if (!moveMemberFromClass(project, psiClass, psiMember)) return;
    psiClass = Objects.requireNonNull(classPtr.getElement());
    PsiErrorElement errorElement = Objects.requireNonNull(PsiTreeUtil.getPrevSiblingOfType(psiClass, PsiErrorElement.class));
    int outerMemberOffset = errorElement.getTextRange().getStartOffset();
    assertTrue("Failed to find member after moving it out of a class", outerMemberOffset != -1);
    moveMemberToClass(outerMemberOffset);
    psiClass = classPtr.getElement();
    if (psiClass == null) return;
    assertSize(nMembers, findMembers(psiClass));
  }

  private void moveMemberToClass(int offset) {
    myFixture.getEditor().getCaretModel().moveToOffset(offset);
    IntentionAction intention = myFixture.findSingleIntention("Move member into class");
    myFixture.launchAction(intention);
  }

  private boolean moveMemberFromClass(@NotNull Project project, @NotNull PsiClass psiClass, @NotNull PsiMember psiMember) {
    int startOffset = psiClass.getTextRange().getStartOffset();
    String memberText = psiMember.getText() + "\n";
    Document document = myFixture.getDocument(psiMember.getContainingFile());
    SmartPsiElementPointer<PsiMember> memberPtr = SmartPointerManager.createPointer(psiMember);
    WriteCommandAction.runWriteCommandAction(project, () -> {
      document.insertString(startOffset, memberText);
    });
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiMember movedMember = memberPtr.getElement();
    if (movedMember == null) return false;
    WriteCommandAction.runWriteCommandAction(project, () -> movedMember.delete());
    return true;
  }

  private static boolean isCompilable(@NotNull Project project, @NotNull VirtualFile file) {
    if (!ProjectFileIndex.getInstance(project).isInSource(file)) return false;
    String path = file.getCanonicalPath();
    // for plugins testdata ProjectFileIndex#isInSource returns true
    return path != null && !path.contains("testData");
  }

  private static boolean hasTypeParameters(@NotNull PsiMember psiMember) {
    PsiTypeElement typeElement;
    if (psiMember instanceof PsiMethod) {
      typeElement = ((PsiMethod)psiMember).getReturnTypeElement();
    }
    else {
      typeElement = ((PsiField)psiMember).getTypeElement();
    }
    return typeElement != null &&
           StreamEx.ofTree((PsiElement)typeElement, e -> StreamEx.of(e.getChildren()))
             .anyMatch(e -> e instanceof PsiJavaToken && ((PsiJavaToken)e).getTokenType() == JavaTokenType.LT);
  }

  private static boolean isMultipleFieldsDeclaration(@NotNull PsiField psiField) {
    FieldElement fieldElement = ObjectUtils.tryCast(psiField.getNode(), FieldElement.class);
    if (fieldElement == null) return false;
    return fieldElement.findChildByRole(ChildRole.TYPE) == null || fieldElement.findChildByRole(ChildRole.CLOSING_SEMICOLON) == null;
  }

  private static @Nullable PsiClass findClass(@NotNull VirtualFile file, @NotNull PsiJavaFile psiJavaFile) {
    PsiClass[] psiClasses = psiJavaFile.getClasses();
    if (psiClasses.length != 1) return null;
    PsiClass psiClass = psiClasses[0];
    String className = file.getNameWithoutExtension();
    return className.equals(psiClass.getName()) ? psiClass : null;
  }

  private static @NotNull List<PsiMember> findMembers(@NotNull PsiClass psiClass) {
    Collection<? extends PsiMember> children = PsiTreeUtil.getChildrenOfAnyType(psiClass, PsiMethod.class, PsiField.class);
    return ContainerUtil.filter(children, c -> (c instanceof PsiField && !isMultipleFieldsDeclaration((PsiField)c) ||
                                                c instanceof PsiMethod && !((PsiMethod)c).isConstructor()) &&
                                               // parser works only with simple type params, without nested type params and qualified names
                                               !hasTypeParameters(c)
    );
  }
}
