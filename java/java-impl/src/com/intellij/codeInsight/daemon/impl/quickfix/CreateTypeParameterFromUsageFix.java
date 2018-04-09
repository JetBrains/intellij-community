// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

public class CreateTypeParameterFromUsageFix extends BaseIntentionAction {
  private final SmartPsiElementPointer<PsiJavaCodeReferenceElement> myRef;

  public CreateTypeParameterFromUsageFix(PsiJavaCodeReferenceElement refElement) {
    myRef = SmartPointerManager.getInstance(refElement.getProject()).createSmartPsiElementPointer(refElement);
  }

  @Nullable
  private PsiJavaCodeReferenceElement getElement() {
    return myRef.getElement();
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("create.type.parameter.from.usage.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiJavaCodeReferenceElement element = getElement();
    if (element == null) return false;
    Context context = Context.from(element);
    boolean available = context != null;
    if (available) {
      setText(QuickFixBundle.message("create.type.parameter.from.usage.text", context.typeName));
    }
    return available;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement element = getElement();
    if (element == null) return;
    Context context = Context.from(element);
    if (context == null) return;
    List<PsiNameIdentifierOwner> placesToAdd = context.myPlacesToAdd;

    Application application = ApplicationManager.getApplication();
    if (placesToAdd.size() == 1 || application.isUnitTestMode()) {
      PsiElement first = placesToAdd.get(0);
      createTypeParameter(first, context.typeName);
    }
    else {
      List<String> toShow = new ArrayList<>();
      for (PsiNameIdentifierOwner owner : placesToAdd) {
        toShow.add(owner.getName());
      }
      AtomicReference<RangeHighlighter> rangeHighlighter = new AtomicReference<>(); // to change in lambda
      MarkupModel markupModel = editor.getMarkupModel();

      JBList<String> list = new JBList<>(toShow);
      list.addListSelectionListener(e -> {
        dropHighlight(rangeHighlighter);
        int selectedIndex = list.getSelectedIndex();
        if (selectedIndex < 0) return;
        PsiNameIdentifierOwner elementToHighlight = placesToAdd.get(selectedIndex);
        TextRange range = elementToHighlight.getTextRange();
        final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(range.getStartOffset());
        editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
        TextAttributes attributes =
          EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
        RangeHighlighter highlighter = markupModel.addRangeHighlighter(range.getStartOffset(), range.getEndOffset(),
                                                                       HighlighterLayer.SELECTION - 1, attributes,
                                                                       HighlighterTargetArea.EXACT_RANGE);
        rangeHighlighter.set(highlighter);
      });
      JBPopupFactory.getInstance()
                    .createListPopupBuilder(list)
                    .setTitle("Select place to add type parameter")
                    .setMovable(false)
                    .setResizable(false)
                    .setRequestFocus(true)
                    .setItemChoosenCallback(() -> {
                      int selectedIndex = list.getSelectedIndex();
                      PsiNameIdentifierOwner owner = placesToAdd.get(selectedIndex);
                      createTypeParameter(owner, context.typeName);
                    })
                    .addListener(new JBPopupAdapter() {
                      @Override
                      public void onClosed(LightweightWindowEvent event) {
                        dropHighlight(rangeHighlighter);
                      }
                    })
                    .createPopup()
                    .showInBestPositionFor(editor);

    }
  }

  private static void dropHighlight(AtomicReference<RangeHighlighter> rangeHighlighter) {
    RangeHighlighter old = rangeHighlighter.get();
    if (old != null) {
      old.dispose();
    }
  }

  private static void createTypeParameter(@NotNull PsiElement methodOrClass, @NotNull String name) {
    Project project = methodOrClass.getProject();
    WriteCommandAction.runWriteCommandAction(project, () -> {
      PsiTypeParameterListOwner typeParameterListOwner = tryCast(methodOrClass, PsiTypeParameterListOwner.class);
      if (typeParameterListOwner == null) {
        throw new IllegalStateException("Only methods and classes allowed here, but was: " + methodOrClass.getClass());
      }
      PsiTypeParameterList typeParameterList = typeParameterListOwner.getTypeParameterList();
      final String typeParameterListText;
      if (typeParameterList == null) {
        typeParameterListText = "<" + name + ">";
      }
      else {
        String existingTypeParameterText = typeParameterList.getText();
        if (typeParameterList.getTypeParameters().length == 0) {
          typeParameterListText = "<" + name + ">";
        }
        else {
          String prefix = existingTypeParameterText.substring(0, existingTypeParameterText.length() - 1);
          typeParameterListText = prefix + ", " + name + ">";
        }
      }
      PsiTypeParameterList newTypeParameterList = createTypeParameterList(typeParameterListText, project);
      replaceOrAddTypeParameterList(methodOrClass, typeParameterList, newTypeParameterList);
    });
  }

  private static void replaceOrAddTypeParameterList(@NotNull PsiElement methodOrClass,
                                                    @Nullable PsiTypeParameterList typeParameterList,
                                                    @NotNull PsiTypeParameterList newTypeParameterList) {
    if (methodOrClass instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)methodOrClass;
      if (typeParameterList == null) {
        PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        if (returnTypeElement == null) return;
        method.addBefore(newTypeParameterList, returnTypeElement);
      }
      else {
        typeParameterList.replace(newTypeParameterList);
      }
    }
    else {
      PsiClass aClass = (PsiClass)methodOrClass;
      if (typeParameterList == null) {
        PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
        if (nameIdentifier == null) return;
        aClass.addAfter(newTypeParameterList, nameIdentifier);
      }
      else {
        typeParameterList.replace(newTypeParameterList);
      }
    }
  }

  private static PsiTypeParameterList createTypeParameterList(@NotNull String text, Project project) {
    PsiJavaFile javaFile = (PsiJavaFile)PsiFileFactory.getInstance(project)
                                                      .createFileFromText("_DUMMY_", JavaFileType.INSTANCE,
                                                                          "class __DUMMY__ " + text + " {}");
    PsiClass[] classes = javaFile.getClasses();
    return classes[0].getTypeParameterList();
  }

  private static class Context {
    @NotNull final List<PsiNameIdentifierOwner> myPlacesToAdd;
    @NotNull final String typeName;

    Context(@NotNull List<PsiNameIdentifierOwner> add, @NotNull String name) {
      myPlacesToAdd = add;
      typeName = name;
    }

    @Nullable
    static Context from(@NotNull PsiJavaCodeReferenceElement element) {
      if (!PsiUtil.isLanguageLevel5OrHigher(element)) return null;
      if (element.isQualified()) return null;
      List<PsiNameIdentifierOwner> candidates = collectParentClassesAndMethodUntilStatic(element);
      if (candidates.isEmpty()) return null;
      String name = element.getReferenceName();
      if (name == null) return null;
      candidates = candidates.stream().filter(owner -> owner.getName() != null).collect(Collectors.toList());
      return new Context(candidates, name);
    }
  }


  static List<PsiNameIdentifierOwner> collectParentClassesAndMethodUntilStatic(PsiElement element) {
    element = element.getParent();
    List<PsiNameIdentifierOwner> parents = new SmartList<>();
    while (element != null) {
      if (element instanceof PsiField && ((PsiField)element).hasModifierProperty(PsiModifier.STATIC)) {
        break;
      }
      if (element instanceof PsiMethod || element instanceof PsiClass) {
        parents.add((PsiNameIdentifierOwner)element);
        if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) break;
      }
      element = element.getParent();
    }
    return parents;
  }
}
