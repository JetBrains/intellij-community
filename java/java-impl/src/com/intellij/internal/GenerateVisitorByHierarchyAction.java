// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.ide.IdeView;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.ValidationInfo;
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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.List;
import java.util.*;

final class GenerateVisitorByHierarchyAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
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


      @Override
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
          @Override
          protected void textChanged(@NotNull final DocumentEvent e) {
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
        final EditorTextField editorTextField = new EditorTextField(document, project, JavaFileType.INSTANCE);
        labeledComponent.setComponent(editorTextField);
        editorTextField.addDocumentListener(new DocumentListener() {
          @Override
          public void documentChanged(@NotNull final com.intellij.openapi.editor.event.DocumentEvent e) {
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
    final PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
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
    final IdeView ideView = e.getData(LangDataKeys.IDE_VIEW);
    final PsiClass visitorClass = JavaPsiFacade.getInstance(project).findClass(visitorQName, GlobalSearchScope.projectScope(project));
    if (ideView != null && visitorClass != null) {
      ideView.selectElement(visitorClass);
    }
  }

  public static String generateEverything(final PsiPackage psiPackage, final PsiClass rootClass, final String visitorName) {
    final String visitorQName = PsiNameHelper.getShortClassName(visitorName).equals(visitorName) ?
                                psiPackage.getQualifiedName() + "." + visitorName : visitorName;
    final PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(rootClass.getProject(),
                                                                               StringUtil.getPackageName(visitorQName), null, false);
    generateVisitorClass(visitorQName, rootClass, directory, new PackageScope(psiPackage, false, false));
    return visitorQName;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }

  private static void generateVisitorClass(final String visitorName,
                                           final PsiClass baseClass,
                                           final PsiDirectory directory,
                                           final GlobalSearchScope scope) {

    final Map<PsiClass, Set<PsiClass>> classes = new HashMap<>();
    for (PsiClass aClass : ClassInheritorsSearch.search(baseClass, scope, true).findAll()) {
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) == baseClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final List<PsiClass> implementors =
          ContainerUtil.findAll(ClassInheritorsSearch.search(aClass).findAll(),
                                psiClass -> !psiClass.hasModifierProperty(PsiModifier.ABSTRACT));
        classes.put(aClass, new HashSet<>(implementors));
      }
    }
    final Map<PsiClass, Set<PsiClass>> pathMap = new HashMap<>();
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
    pathMap.put(baseClass, Collections.emptySet());
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
    try {
      WriteCommandAction.writeCommandAction(project, PsiUtilCore.toPsiFileArray(psiFiles))
                        .withGlobalUndo()
                        .run(() -> {
                          if (visitorClass == null) {
                            final String shortClassName = PsiNameHelper.getShortClassName(visitorName);
                            if (directory != null) {
                              final PsiClass vc = JavaDirectoryService.getInstance().createClass(directory, shortClassName);
                              generateVisitorClass(vc, classes, pathMap, finalDetectedPrefix);
                            }
                          }
                          else {
                            generateVisitorClass(visitorClass, classes, pathMap, finalDetectedPrefix);
                          }
                        });
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
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
        detectedPrefix = detectedPrefix.isEmpty() ? prefix : detectedPrefix.equals(prefix) ? detectedPrefix : null;
      }
      if (detectedPrefix == null) return "";
    }
    return detectedPrefix;
  }

  private static void generateVisitorClass(final PsiClass visitorClass, final Map<PsiClass, Set<PsiClass>> classes,
                                           final Map<PsiClass, Set<PsiClass>> pathMap, int classPrefix) throws Throwable {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(visitorClass.getProject());
    for (PsiClass psiClass : classes.keySet()) {
      final PsiMethod method = elementFactory.createMethodFromText(
        "public void accept(final " + visitorClass.getQualifiedName() + " visitor) { visitor.visit" + psiClass.getName().substring(classPrefix) + "(this); }", psiClass);
      for (PsiClass implementor : classes.get(psiClass)) {
        addOrReplaceMethod(method, implementor);
      }
    }

    final Set<PsiClass> visitedClasses = new HashSet<>();
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
      GenerateMembersUtil.insertMembersAtOffset(implementor, implementor.getLastChild().getTextOffset(), Collections.<GenerationInfo>singletonList(
        new PsiGenerationInfo<>(method)));
    }
  }
}