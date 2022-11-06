// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightNamesUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorMatchingSuperFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class CreateSubclassAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(CreateSubclassAction.class);
  private @IntentionName String myText = JavaBundle.message("intention.implement.abstract.class.default.text");

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.implement.abstract.class.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final CaretModel caretModel = editor.getCaretModel();
    final int position = caretModel.getOffset();
    PsiElement element = file.findElementAt(position);
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null || psiClass.isAnnotationType() || psiClass.isEnum() || psiClass instanceof PsiAnonymousClass ||
        psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiClass);
    if (virtualFile == null) {
      return false;
    }
    if (!isSupportedLanguage(psiClass)) return false;
    final PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length > 0) {
      boolean hasNonPrivateConstructor = false;
      for (PsiMethod constructor : constructors) {
        if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          hasNonPrivateConstructor = true;
          break;
        }
      }
      if (!hasNonPrivateConstructor) return false;
    }
    PsiElement lBrace = psiClass.getLBrace();
    if (lBrace == null) return false;
    if (element.getTextOffset() >= lBrace.getTextOffset()) return false;

    TextRange declarationRange = HighlightNamesUtil.getClassDeclarationTextRange(psiClass);
    final TextRange elementTextRange = element.getTextRange();
    if (!declarationRange.contains(elementTextRange)) {
      if (!(element instanceof PsiWhiteSpace) || (declarationRange.getStartOffset() != elementTextRange.getEndOffset() &&
                                                  declarationRange.getEndOffset() != elementTextRange.getStartOffset())) {
        return false;
      }
    }

    if (shouldCreateInnerClass(psiClass) && !canModify(file)) {
      return false;
    }

    myText = getTitle(psiClass);
    return true;
  }

  protected boolean isSupportedLanguage(PsiClass aClass) {
    return aClass.getLanguage() == JavaLanguage.INSTANCE;
  }

  protected static @NlsContexts.Command String getTitle(PsiClass psiClass) {
    return psiClass.isInterface()
             ? CodeInsightBundle.message("intention.implement.abstract.class.interface.text")
             : psiClass.hasModifierProperty(PsiModifier.ABSTRACT)
               ? JavaBundle.message("intention.implement.abstract.class.default.text")
               : CodeInsightBundle.message("intention.implement.abstract.class.subclass.text");
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

    LOG.assertTrue(psiClass != null);
    if (shouldCreateInnerClass(psiClass)) {
      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
      createInnerClass(psiClass);
      return;
    }
    if (ScratchUtil.isScratch(PsiUtilCore.getVirtualFile(psiClass))) {
      createSameFileClass(suggestTargetClassName(psiClass), psiClass);
      return;
    }
    createTopLevelClass(psiClass);
  }

  private static boolean shouldCreateInnerClass(PsiClass psiClass) {
    return psiClass.hasModifierProperty(PsiModifier.PRIVATE) && psiClass.getContainingClass() != null;
  }

  public static void createSameFileClass(String newClassName, PsiClass psiClass) {
    String actionTitle = getTitle(psiClass);
    Project project = psiClass.getProject();
    WriteCommandAction.writeCommandAction(project).withName(actionTitle).withGroupId(actionTitle).run(() -> {
      PsiJavaFile containingFile = ObjectUtils.tryCast(psiClass.getContainingFile(), PsiJavaFile.class);
      LOG.assertTrue(containingFile != null);

      PsiClass[] classes = containingFile.getClasses();
      int nClasses = classes.length;
      LOG.assertTrue(nClasses > 0);

      final PsiTypeParameterList oldTypeParameterList = psiClass.getTypeParameterList();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiClass newClass = factory.createClass(newClassName);
      PsiModifierList modifiers = newClass.getModifierList();
      LOG.assertTrue(modifiers != null);
      modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
      newClass = (PsiClass)containingFile.addAfter(newClass, classes[nClasses - 1]);

      PsiIdentifier newClassIdentifier = newClass.getNameIdentifier();
      LOG.assertTrue(newClassIdentifier != null);
      startTemplate(oldTypeParameterList, project, psiClass, newClass, false);
      CodeInsightUtil.positionCursor(project, containingFile, newClassIdentifier);
    });
  }

  public static void createInnerClass(final PsiClass aClass) {
    WriteCommandAction.writeCommandAction(aClass.getProject()).withName(getTitle(aClass)).withGroupId(getTitle(aClass)).run(() -> {
      final PsiClass containingClass = aClass.getContainingClass();
      LOG.assertTrue(containingClass != null);

      final PsiTypeParameterList oldTypeParameterList = aClass.getTypeParameterList();
      PsiClass classFromText = JavaPsiFacade.getElementFactory(aClass.getProject()).createClass(
        suggestTargetClassName(aClass));
      classFromText = (PsiClass)containingClass.addAfter(classFromText, aClass);
      startTemplate(oldTypeParameterList, aClass.getProject(), aClass, classFromText, true);
    });
  }

  protected void createTopLevelClass(PsiClass psiClass) {
    final CreateClassDialog dlg = chooseSubclassToCreate(psiClass);
    if (dlg != null) {
      PsiDirectory targetDirectory = dlg.getTargetDirectory();
      PsiJavaFile containingFile = ObjectUtils.tryCast(psiClass.getContainingFile(), PsiJavaFile.class);
      boolean inSamePackage = containingFile != null && containingFile.getPackageName().equals(targetDirectory.getName());
      if (inSamePackage && hasOnlySameFileInheritors(psiClass)) {
        createSameFileClass(dlg.getClassName(), psiClass);
      } else {
        createSubclass(psiClass, targetDirectory, dlg.getClassName());
      }
    }
  }

  private static boolean hasOnlySameFileInheritors(PsiClass psiClass) {
    if (!psiClass.hasModifierProperty(PsiModifier.SEALED) || psiClass.getPermitsList() != null) return false;
    Ref<Boolean> hasInheritors = Ref.create(false);
    boolean hasOnlySameFileInheritors = DirectClassInheritorsSearch.search(psiClass).forEach((Processor<? super PsiClass>) inheritor -> {
      if (inheritor.getContainingFile() != psiClass.getContainingFile()) {
        return false;
      }
      hasInheritors.set(true);
      return true;
    });
    return hasOnlySameFileInheritors && hasInheritors.get();
  }

  @Nullable
  public static CreateClassDialog chooseSubclassToCreate(PsiClass psiClass) {
    return chooseSubclassToCreate(psiClass, suggestTargetClassName(psiClass));
  }

  @Nullable
  public static CreateClassDialog chooseSubclassToCreate(PsiClass psiClass, final String targetClassName) {
    final PsiDirectory sourceDir = psiClass.getContainingFile().getContainingDirectory();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(psiClass.getProject()).getFileIndex();
    final PsiPackage aPackage = sourceDir != null ? JavaDirectoryService.getInstance().getPackage(sourceDir) : null;
    final CreateClassDialog dialog = new CreateClassDialog(
      psiClass.getProject(), getTitle(psiClass),
      targetClassName,
      aPackage != null ? aPackage.getQualifiedName() : "",
      CreateClassKind.CLASS, true, ModuleUtilCore.findModuleForPsiElement(psiClass)) {
      @Override
      protected PsiDirectory getBaseDir(String packageName) {
        return sourceDir != null && fileIndex.getSourceRootForFile(sourceDir.getVirtualFile()) != null ? sourceDir : super.getBaseDir(packageName);
      }

      @Override
      protected boolean reportBaseInTestSelectionInSource() {
        return true;
      }
    };
    if (!dialog.showAndGet()) {
      return null;
    }
    final PsiDirectory targetDirectory = dialog.getTargetDirectory();
    if (targetDirectory == null) return null;
    return dialog;
  }

  public static String suggestTargetClassName(PsiClass psiClass) {
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(psiClass.getContainingFile());
    return javaSettings.SUBCLASS_NAME_PREFIX + psiClass.getName() + javaSettings.SUBCLASS_NAME_SUFFIX;
  }

  public static PsiClass createSubclass(final PsiClass psiClass, final PsiDirectory targetDirectory, final String className) {
    return createSubclass(psiClass, targetDirectory, className, true);
  }

  public static PsiClass createSubclass(final PsiClass psiClass, final PsiDirectory targetDirectory, final String className, boolean showChooser) {
    final Project project = psiClass.getProject();
    final PsiClass[] targetClass = new PsiClass[1];
    WriteCommandAction.writeCommandAction(project).withName(getTitle(psiClass)).withGroupId(getTitle(psiClass)).run(() -> {
      IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

      final PsiTypeParameterList oldTypeParameterList = psiClass.getTypeParameterList();

      try {
        targetClass[0] = JavaDirectoryService.getInstance().createClass(targetDirectory, className);
      }
      catch (final IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showErrorDialog(project, JavaBundle.message("intention.error.cannot.create.class.message", className) +
                                                  "\n" + e.getLocalizedMessage(),
                                         JavaBundle.message("intention.error.cannot.create.class.title")));
        return;
      }
      startTemplate(oldTypeParameterList, project, psiClass, targetClass[0], false);
    });
    if (targetClass[0] == null) return null;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !psiClass.hasTypeParameters()) {

      final Editor editor = CodeInsightUtil.positionCursorAtLBrace(project, targetClass[0].getContainingFile(), targetClass[0]);
      if (editor == null) return targetClass[0];

      chooseAndImplement(psiClass, project, targetClass[0], editor, showChooser);
    }
    return targetClass[0];
  }

  private static void startTemplate(PsiTypeParameterList oldTypeParameterList,
                                    final Project project,
                                    final PsiClass psiClass,
                                    final PsiClass targetClass,
                                    final boolean includeClassName) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    PsiJavaCodeReferenceElement ref = elementFactory.createClassReferenceElement(psiClass);
    try {
      if (psiClass.isInterface()) {
        ref = (PsiJavaCodeReferenceElement)targetClass.getImplementsList().add(ref);
      }
      else {
        ref = (PsiJavaCodeReferenceElement)targetClass.getExtendsList().add(ref);
      }
      if (psiClass.hasModifierProperty(PsiModifier.SEALED) && psiClass.getContainingFile() != targetClass.getContainingFile()) {
        String createdClassName = Objects.requireNonNull(targetClass.getQualifiedName());
        SealedUtils.addClassToPermitsList(psiClass, createdClassName);
      }
      if (psiClass.hasTypeParameters() || includeClassName) {
        final Editor editor = CodeInsightUtil.positionCursorAtLBrace(project, targetClass.getContainingFile(), targetClass);
        final TemplateBuilderImpl templateBuilder = editor != null
                   ? (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().createTemplateBuilder(targetClass) : null;

        if (includeClassName && templateBuilder != null) {
          templateBuilder.replaceElement(targetClass.getNameIdentifier(), targetClass.getName());
        }

        if (oldTypeParameterList != null) {
          for (PsiTypeParameter parameter : oldTypeParameterList.getTypeParameters()) {
            final PsiElement param = ref.getParameterList().add(elementFactory.createTypeElement(elementFactory.createType(parameter)));
            if (templateBuilder != null) {
              templateBuilder.replaceElement(param, param.getText());
            }
          }
        }

        replaceTypeParamsList(targetClass, oldTypeParameterList);
        if (templateBuilder != null) {
          templateBuilder.setEndVariableBefore(ref);
          final Template template = templateBuilder.buildTemplate();
          template.addEndVariable();

          PsiClassOwner containingFile = (PsiClassOwner)targetClass.getContainingFile();
          int idxInFile = ArrayUtil.find(containingFile.getClasses(), targetClass);

          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());

          final TextRange textRange = targetClass.getTextRange();
          final RangeMarker startClassOffset = editor.getDocument().createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
          editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
          CreateFromUsageBaseFix.startTemplate(editor, template, project, new TemplateEditingAdapter() {
            @Override
            public void templateFinished(@NotNull Template template, boolean brokenOff) {
              try {
                LOG.assertTrue(startClassOffset.isValid(), startClassOffset);
                final PsiClass aTargetClass;
                if (idxInFile >= 0) {
                  PsiClass[] classes = containingFile.getClasses();
                  LOG.assertTrue(idxInFile < classes.length, "idx: " + idxInFile + "; len: " + classes.length);
                  aTargetClass = classes[idxInFile];
                }
                else {
                  final PsiElement psiElement = containingFile.findElementAt(startClassOffset.getStartOffset());
                  aTargetClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
                  LOG.assertTrue(aTargetClass != null, psiElement);
                }
                if (!brokenOff) {
                  chooseAndImplement(psiClass, project, aTargetClass, editor);
                }
              }
              finally {
                startClassOffset.dispose();
              }
            }
          }, getTitle(psiClass));
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static PsiElement replaceTypeParamsList(PsiClass psiClass, PsiTypeParameterList oldTypeParameterList) {
    final PsiTypeParameterList typeParameterList = psiClass.getTypeParameterList();
    assert typeParameterList != null;
    return typeParameterList.replace(oldTypeParameterList);
  }

  protected static void chooseAndImplement(PsiClass psiClass, Project project, @NotNull PsiClass targetClass, Editor editor) {
    chooseAndImplement(psiClass, project, targetClass, editor, true);
  }

  protected static void chooseAndImplement(PsiClass psiClass, Project project, @NotNull PsiClass targetClass, Editor editor, boolean showChooser) {
    boolean hasNonTrivialConstructor = false;
    final PsiMethod[] constructors = psiClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      if (!constructor.getParameterList().isEmpty()) {
        hasNonTrivialConstructor = true;
        break;
      }
    }
    if (hasNonTrivialConstructor) {
      final int offset = editor.getCaretModel().getOffset();
      CreateConstructorMatchingSuperFix.chooseConstructor2Delegate(project, editor, targetClass);
      editor.getCaretModel().moveToOffset(offset);
    }

    if (psiClass.hasModifierProperty(PsiModifier.SEALED)) {
      PsiIdentifier targetNameIdentifier = Objects.requireNonNull(targetClass.getNameIdentifier());
      editor.getCaretModel().moveToOffset(targetNameIdentifier.getTextRange().getStartOffset());
    }

    if (showChooser) OverrideImplementUtil.chooseAndImplementMethods(project, editor, targetClass);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

}
