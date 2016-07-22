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
package com.intellij.internal;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.ide.IdeView;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorTextField;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.*;
import java.util.List;

public class GenerateVisitorByHierarchyAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    final Ref<String> visitorNameRef = Ref.create("MyVisitor");
    final Ref<PsiClass> parentClassRef = Ref.create(null);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    final PsiNameHelper helper = PsiNameHelper.getInstance(project);
    final PackageChooserDialog dialog = new PackageChooserDialog("Choose Target Package and Hierarchy Root Class", project) {

      @Override
      protected ValidationInfo doValidate() {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        if (!helper.isQualifiedName(visitorNameRef.get())) {
          return new ValidationInfo("Visitor class name is not valid");
        }
        else if (parentClassRef.isNull()) {
          return new ValidationInfo("Hierarchy root class should be specified");
        }
        else if (parentClassRef.get().isAnnotationType() || parentClassRef.get().isEnum()) {
          return new ValidationInfo("Hierarchy root class should be an interface or a class");
        }
        return super.doValidate();
      }


      protected JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(super.createCenterPanel(), BorderLayout.CENTER);
        panel.add(createNamePanel(), BorderLayout.NORTH);
        panel.add(createBaseClassPanel(), BorderLayout.SOUTH);
        return panel;
      }

      private JComponent createNamePanel() {
        LabeledComponent<JTextField> labeledComponent = new LabeledComponent<>();
        labeledComponent.setText("Visitor class");
        final JTextField nameField = new JTextField(visitorNameRef.get());
        labeledComponent.setComponent(nameField);
        nameField.getDocument().addDocumentListener(new DocumentAdapter() {
          protected void textChanged(final DocumentEvent e) {
            visitorNameRef.set(nameField.getText());
          }
        });
        return labeledComponent;
      }

      private JComponent createBaseClassPanel() {
        LabeledComponent<EditorTextField> labeledComponent = new LabeledComponent<>();
        labeledComponent.setText("Hierarchy root class");
        final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
        final PsiTypeCodeFragment codeFragment = factory.createTypeCodeFragment("", null, true, JavaCodeFragmentFactory.ALLOW_VOID);
        final Document document = PsiDocumentManager.getInstance(project).getDocument(codeFragment);
        final EditorTextField editorTextField = new EditorTextField(document, project, StdFileTypes.JAVA);
        labeledComponent.setComponent(editorTextField);
        editorTextField.addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
          public void documentChanged(final com.intellij.openapi.editor.event.DocumentEvent e) {
            parentClassRef.set(null);
            try {
              final PsiType psiType = codeFragment.getType();
              final PsiClass psiClass = psiType instanceof PsiClassType ? ((PsiClassType)psiType).resolve() : null;
              parentClassRef.set(psiClass);
            }
            catch (PsiTypeCodeFragment.IncorrectTypeException e1) {
              // ok
            }
          }
        });
        return labeledComponent;
      }
    };
    final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(e.getDataContext());
    if (element instanceof PsiPackage) {
      dialog.selectPackage(((PsiPackage)element).getQualifiedName());
    }
    else if (element instanceof PsiDirectory) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)element);
      if (aPackage != null) {
        dialog.selectPackage(aPackage.getQualifiedName());
      }
    }
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE ||
        dialog.getSelectedPackage() == null ||
        dialog.getSelectedPackage().getQualifiedName().length() == 0 ||
        parentClassRef.isNull()) {
      return;
    }
    final String visitorQName = generateEverything(dialog.getSelectedPackage(), parentClassRef.get(), visitorNameRef.get());
    final IdeView ideView = LangDataKeys.IDE_VIEW.getData(e.getDataContext());
    final PsiClass visitorClass = JavaPsiFacade.getInstance(project).findClass(visitorQName, GlobalSearchScope.projectScope(project));
    if (ideView != null && visitorClass != null) {
      ideView.selectElement(visitorClass);
    }
  }

  public static String generateEverything(final PsiPackage psiPackage, final PsiClass rootClass, final String visitorName) {
    final String visitorQName = PsiNameHelper.getShortClassName(visitorName).equals(visitorName)?
                                psiPackage.getQualifiedName()+"."+ visitorName : visitorName;
    final PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(rootClass.getProject(),
                                                                               StringUtil.getPackageName(visitorQName), null, false);
    generateVisitorClass(visitorQName, rootClass, directory, new PackageScope(psiPackage, false, false));
    return visitorQName;
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(CommonDataKeys.PROJECT) != null);
  }

  private static void generateVisitorClass(final String visitorName,
                                           final PsiClass baseClass,
                                           final PsiDirectory directory,
                                           final GlobalSearchScope scope) {

    final THashMap<PsiClass, Set<PsiClass>> classes = new THashMap<>();
    for (PsiClass aClass : ClassInheritorsSearch.search(baseClass, scope, true).findAll()) {
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) == baseClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final List<PsiClass> implementors =
          ContainerUtil.findAll(ClassInheritorsSearch.search(aClass).findAll(),
                                psiClass -> !psiClass.hasModifierProperty(PsiModifier.ABSTRACT));
        classes.put(aClass, new THashSet<>(implementors));
      }
    }
    final THashMap<PsiClass, Set<PsiClass>> pathMap = new THashMap<>();
    for (PsiClass aClass : classes.keySet()) {
      final Set<PsiClass> superClasses = new LinkedHashSet<>();
      for (PsiClass superClass : aClass.getSupers()) {
        if (superClass.isInheritor(baseClass, true)) {
          superClasses.add(superClass);
          final Set<PsiClass> superImplementors = classes.get(superClass);
          if (superImplementors != null) {
            superImplementors.removeAll(classes.get(aClass));
          }
        }
      }
      if (superClasses.isEmpty()) {
        superClasses.add(baseClass);
      }
      pathMap.put(aClass, superClasses);
    }
    pathMap.put(baseClass, Collections.<PsiClass>emptySet());
    final ArrayList<PsiFile> psiFiles = new ArrayList<>();
    for (Set<PsiClass> implementors : classes.values()) {
      for (PsiClass psiClass : implementors) {
        psiFiles.add(psiClass.getContainingFile());
      }
    }
    final Project project = baseClass.getProject();
    final PsiClass visitorClass = JavaPsiFacade.getInstance(project).findClass(visitorName, GlobalSearchScope.projectScope(project));
    if (visitorClass != null) {
      psiFiles.add(visitorClass.getContainingFile());
    }
    final int finalDetectedPrefix = detectClassPrefix(classes.keySet()).length();
    new WriteCommandAction(project, PsiUtilCore.toPsiFileArray(psiFiles)) {
      protected void run(@NotNull final Result result) throws Throwable {
        if (visitorClass == null) {
          final String shortClassName = PsiNameHelper.getShortClassName(visitorName);
          if (directory != null) {
            final PsiClass visitorClass = JavaDirectoryService.getInstance().createClass(directory, shortClassName);
            generateVisitorClass(visitorClass, classes, pathMap, finalDetectedPrefix);
          }
        }
        else {
          generateVisitorClass(visitorClass, classes, pathMap, finalDetectedPrefix);
        }
      }

      @Override
      protected boolean isGlobalUndoAction() {
        return true;
      }
    }.execute();
  }

  @NotNull
  private static String detectClassPrefix(Collection<PsiClass> classes) {
    String detectedPrefix = "";
    List<TextRange> range = new SmartList<>();
    for (PsiClass aClass : classes) {
      String className = aClass.getName();
      SelectWordUtil.addWordSelection(true, className, 0, range);
      TextRange prefixRange = ContainerUtil.getFirstItem(range);
      if (prefixRange != null) {
        String prefix = prefixRange.substring(className);
        detectedPrefix = detectedPrefix == "" ? prefix : detectedPrefix.equals(prefix) ? detectedPrefix : null;
      }
      if (detectedPrefix == null) return "";
    }
    return detectedPrefix;
  }

  private static void generateVisitorClass(final PsiClass visitorClass, final Map<PsiClass, Set<PsiClass>> classes,
                                           final THashMap<PsiClass, Set<PsiClass>> pathMap, int classPrefix) throws Throwable {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(visitorClass.getProject()).getElementFactory();
    for (PsiClass psiClass : classes.keySet()) {
      final PsiMethod method = elementFactory.createMethodFromText(
        "public void accept(final " + visitorClass.getQualifiedName() + " visitor) { visitor.visit" + psiClass.getName().substring(classPrefix) + "(this); }", psiClass);
      for (PsiClass implementor : classes.get(psiClass)) {
        addOrReplaceMethod(method, implementor);
      }
    }

    final THashSet<PsiClass> visitedClasses = new THashSet<>();
    final LinkedList<PsiClass> toProcess = new LinkedList<>(classes.keySet());
    while (!toProcess.isEmpty()) {
      final PsiClass psiClass = toProcess.removeFirst();
      if (!visitedClasses.add(psiClass)) continue;
      final Set<PsiClass> pathClasses = pathMap.get(psiClass);
      toProcess.addAll(pathClasses);
      final StringBuilder methodText = new StringBuilder();

      methodText.append("public void visit").append(psiClass.getName().substring(classPrefix)).append("(final ").append(psiClass.getQualifiedName()).append(" o) {");
      boolean first = true;
      for (PsiClass pathClass : pathClasses) {
        if (first) {
          first = false;
        }
        else {
          methodText.append("// ");
        }
        methodText.append("visit").append(pathClass.getName().substring(classPrefix)).append("(o);\n");
      }
      methodText.append("}");
      final PsiMethod method = elementFactory.createMethodFromText(methodText.toString(), psiClass);
      addOrReplaceMethod(method, visitorClass);
    }

  }

  private static void addOrReplaceMethod(final PsiMethod method, final PsiClass implementor) throws IncorrectOperationException {
    final PsiMethod accept = implementor.findMethodBySignature(method, false);
    if (accept != null) {
      accept.replace(method);
    }
    else {
      GenerateMembersUtil.insertMembersAtOffset(implementor.getContainingFile(), implementor.getLastChild().getTextOffset(), Collections.<GenerationInfo>singletonList(
        new PsiGenerationInfo<>(method)));
    }
  }


}