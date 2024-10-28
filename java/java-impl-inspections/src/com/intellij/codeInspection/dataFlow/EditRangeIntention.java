// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.StringValidator;
import com.intellij.codeInspection.ui.OptPaneUtils;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EditRangeIntention extends BaseIntentionAction implements LowPriorityAction {
  private static final String JETBRAINS_RANGE = "org.jetbrains.annotations.Range";

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.family.edit.range");
  }

  @Nullable
  private static PsiModifierListOwner getTarget(Editor editor, PsiFile file) {
    final PsiModifierListOwner owner =  AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    LongRangeSet rangeFromType = rangeFromType(owner);
    if (rangeFromType == null || !ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) return null;
    PsiElement original = owner.getOriginalElement();
    return original instanceof PsiModifierListOwner ? (PsiModifierListOwner)original : owner;
  }

  @Contract("null -> null")
  private static LongRangeSet rangeFromType(PsiModifierListOwner owner) {
    PsiType type;
    if (owner instanceof PsiMethod) {
      type = ((PsiMethod)owner).getReturnType();
    } else if (owner instanceof PsiField || owner instanceof PsiParameter) {
      type = ((PsiVariable)owner).getType();
    } else {
      type = null;
    }
    return JvmPsiRangeSetUtil.typeRange(type);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiModifierListOwner owner = getTarget(editor, file);
    if (owner != null) {
      boolean hasRange = !JvmPsiRangeSetUtil.fromPsiElement(owner).equals(LongRangeSet.all());
      String name = getElementName(owner);
      setText(hasRange
              ? JavaBundle.message("intention.text.edit.range.of.0", name)
              : JavaBundle.message("intention.text.add.range.to.0", name));
      return true;
    }
    return false;
  }

  @Nullable
  private static String getElementName(PsiModifierListOwner owner) {
    String name = ((PsiNamedElement)owner).getName();
    if (owner instanceof PsiMethod) name += "()";
    return name;
  }
  
  private static class RangeData implements OptionContainer {
    private final LongRangeSet myType;
    String min;
    String max;

    RangeData(@NotNull LongRangeSet type, @NotNull String min, @NotNull String max) {
      myType = type;
      this.min = min;
      this.max= max;
    }

    static @NotNull RangeData from(@NotNull PsiModifierListOwner owner) {
      LongRangeSet existingRange = JvmPsiRangeSetUtil.fromPsiElement(owner);
      LongRangeSet fromType = rangeFromType(owner);
      assert fromType != null;

      return new RangeData(fromType, existingRange.min() > fromType.min() ? String.valueOf(existingRange.min()) : "",
                                     existingRange.max() < fromType.max() ? String.valueOf(existingRange.max()) : "");
    }
    
    @NotNull OptPane getOptionPane() {
      return OptPane.pane(
        OptPane.string("min", JavaBundle.message("label.from.inclusive"),
                       StringValidator.of("java.range.min", value -> getErrorMessages(value, max, myType).first))
          .description(JavaBundle.message("edit.range.dialog.message")),
        OptPane.string("max", JavaBundle.message("label.to.inclusive"),
                       StringValidator.of("java.range.max", value -> getErrorMessages(min, value, myType).second))
          .description(JavaBundle.message("edit.range.dialog.message"))
      );
    }
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiModifierListOwner owner = getTarget(editor, file);
    assert owner != null;
    RangeData data = RangeData.from(owner);
    OptPaneUtils.editOptions(project, data, data.getOptionPane(),
                             JavaBundle.message("dialog.title.edit.range", getElementName(owner)),
                             "define_range_dialog",
                             () -> updateRange(owner, data.myType, data.min, data.max));
  }

  private static void updateRange(PsiModifierListOwner owner, LongRangeSet fromType, String min, String max) {
    Project project = owner.getProject();
    ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(project);
    manager.deannotate(owner, JETBRAINS_RANGE);
    min = min.trim();
    max = max.trim();
    Long minValue = parseValue(min, fromType, true);
    Long maxValue = parseValue(max, fromType, false);
    if (minValue == null || maxValue == null || minValue == fromType.min() && maxValue == fromType.max()) {
      return;
    }
    if (minValue == Long.MIN_VALUE) {
      min = CommonClassNames.JAVA_LANG_LONG + ".MIN_VALUE";
    }
    if (minValue == Integer.MIN_VALUE) {
      min = CommonClassNames.JAVA_LANG_INTEGER + ".MIN_VALUE";
    }
    if (maxValue == Long.MAX_VALUE) {
      max = CommonClassNames.JAVA_LANG_LONG + ".MAX_VALUE";
    }
    if (maxValue == Integer.MAX_VALUE) {
      max = CommonClassNames.JAVA_LANG_INTEGER + ".MAX_VALUE";
    }
    if (min.isEmpty()) {
      min = minValue.toString();
    }
    if (max.isEmpty()) {
      max = maxValue.toString();
    }
    PsiAnnotation mockAnno = JavaPsiFacade.getElementFactory(project).createAnnotationFromText(
      "@"+JETBRAINS_RANGE+"(from="+min+", to="+max+")", null);
    try {
      manager.annotateExternally(owner, JETBRAINS_RANGE, owner.getContainingFile(),
                                 mockAnno.getParameterList().getAttributes());
    }
    catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {}
    DaemonCodeAnalyzerEx.getInstanceEx(project).restart("EditRangeIntention.updateRange");
  }

  @NotNull
  private static Couple<@Nls String> getErrorMessages(String minText, String maxText, LongRangeSet fromType) {
    Long minValue = parseValue(minText, fromType, true);
    Long maxValue = parseValue(maxText, fromType, false);
    String minError = null;
    String maxError = null;
    if (minValue == null) {
      minError = JavaBundle.message("edit.range.error.invalid.value");
    } else if (minValue < fromType.min()) {
      minError = JavaBundle.message("edit.range.value.should.be.less.than", fromType.min());
    }
    if (maxValue == null) {
      maxError = JavaBundle.message("edit.range.error.invalid.value");
    } else if (maxValue > fromType.max()) {
      maxError = JavaBundle.message("edit.range.value.should.be.bigger.than", fromType.max());
    } else if (minValue != null && maxValue < minValue) {
      minError = JavaBundle.message("edit.range.should.not.be.greater.than.to");
      maxError = JavaBundle.message("edit.range.should.not.be.less.than.from");
    }
    return Couple.of(minError, maxError);
  }

  @Nullable
  private static Long parseValue(String text, LongRangeSet fromType, boolean isMin) {
    text = text.trim();
    if (text.isEmpty()) {
      return isMin ? fromType.min() : fromType.max();
    }
    Long value = PsiLiteralUtil.parseLong(text);
    if (value != null) return value;
    Integer intValue = PsiLiteralUtil.parseInteger(text);
    return intValue != null ? Long.valueOf(intValue.longValue()) : value;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
