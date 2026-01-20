// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.JavaTailTypes;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.MemberLookupHelper;
import com.intellij.codeInsight.daemon.impl.quickfix.BringVariableIntoScopeFix;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.PsiUpdateCompletionItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.JavaDeprecationUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

@NotNullByDefault
final class VariableCompletionItem extends PsiUpdateCompletionItem<PsiVariable> {
  private final @Nullable MemberLookupHelper myHelper;
  private final boolean myNegatable;
  @NlsSafe private final @Nullable String myTailText;
  private final PsiSubstitutor mySubstitutor;
  private final @Nullable PsiClass myQualifierClass;

  VariableCompletionItem(PsiVariable var) {
    this(var, null, null, PsiSubstitutor.EMPTY, null);
  }

  VariableCompletionItem(PsiField field, boolean shouldImport) {
    this(field, new MemberLookupHelper(field, field.getContainingClass(), shouldImport, false), null, PsiSubstitutor.EMPTY, null);
  }

  /**
   * @param var      variable to lookup
   * @param tailText specific tail text to insert (initializer text is used if not specified)
   */
  VariableCompletionItem(PsiVariable var, @Nls String tailText) {
    this(var, null, tailText, PsiSubstitutor.EMPTY, null);
  }

  private VariableCompletionItem(PsiVariable var, 
                                 @Nullable MemberLookupHelper helper, 
                                 @Nullable @Nls String tailText,
                                 PsiSubstitutor substitutor,
                                 @Nullable PsiClass qualifierClass) {
    super(Objects.requireNonNull(var.getName()), var);
    myHelper = helper;
    myTailText = tailText == null ? VariableLookupItem.getInitializerText(var) : tailText;
    myNegatable = TypeConversionUtil.isBooleanType(var.getType());
    mySubstitutor = substitutor;
    myQualifierClass = qualifierClass;
  }

  public VariableCompletionItem withSubstitutor(PsiSubstitutor substitutor) {
    return new VariableCompletionItem(contextObject(), myHelper, myTailText, substitutor, myQualifierClass);
  }

  @ApiStatus.Internal
  public VariableCompletionItem qualifyIfNeeded(@Nullable PsiReference position, @Nullable PsiClass origClass) {
    PsiVariable var = contextObject();
    if (var instanceof PsiField field && !shouldImport() && VariableLookupItem.shouldQualify(field, position)) {
      PsiClass aClass = field.getContainingClass();
      if (aClass != null && origClass != null &&
          !JavaResolveUtil.isAccessible(aClass, aClass.getContainingClass(), aClass.getModifierList(), position.getElement(), null, null) &&
          JavaResolveUtil.isAccessible(origClass, origClass.getContainingClass(), origClass.getModifierList(), position.getElement(), null,
                                       null) &&
          var.isEquivalentTo(origClass.findFieldByName(var.getName(), true))) {
        aClass = origClass;
      }
      if (aClass != null && aClass.getName() != null) {
        return new VariableCompletionItem(var, myHelper, myTailText, mySubstitutor, aClass);
      }
    }
    return this;
  }
  
  private boolean shouldImport() {
    return myHelper != null && myHelper.willBeImported();
  }
  
  @Override
  public AutoCompletionPolicy autoCompletionPolicy() {
    return shouldImport() ? AutoCompletionPolicy.NEVER_AUTOCOMPLETE : super.autoCompletionPolicy();
  }

  @Override
  public Set<@NlsSafe String> additionalLookupStrings() {
    if (myQualifierClass != null) {
      String infix = contextObject().hasModifierProperty(PsiModifier.STATIC) ? "." : ".this.";
      String suffix = infix + contextObject().getName();
      return ContainerUtil.map2Set(JavaCompletionUtil.getAllLookupStrings(myQualifierClass),
                                   s -> s + suffix);
    } 
    return Set.of();
  }

  private @Nullable String getForcedQualifier() {
    if (myQualifierClass == null) return null;
    String className = myQualifierClass.getName();
    if (className == null) return null;
    return contextObject().hasModifierProperty(PsiModifier.STATIC) ? className + "." : className + ".this.";
  }

  @Override
  public void update(ActionContext actionContext, InsertionContext insertionContext, ModPsiUpdater updater) {
    PsiVariable variable = contextObject();
    Document document = updater.getDocument();
    PsiFile file = updater.getPsiFile();
    Project project = updater.getProject();

    PsiDocumentManager.getInstance(project).commitDocument(document);
    if (variable instanceof PsiField field) {
      if (shouldImport()) {
        RangeMarker toDelete = JavaCompletionUtil.insertTemporary(updater.getCaretOffset(), document, " ");
        PsiDocumentManager.getInstance(project).commitDocument(document);
        PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(
          file, actionContext.offset(), PsiReferenceExpression.class, false);
        if (ref != null) {
          PsiClass containingClass = field.getContainingClass();
          if (!ref.isQualified() && containingClass != null) {
            ref.bindToElementViaStaticImport(containingClass);
            PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
          }
        }
        if (toDelete != null && toDelete.isValid()) {
          document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
        }
      }
      else if (VariableLookupItem.shouldQualify(field, file.findReferenceAt(updater.getCaretOffset() - 1))) {
        qualifyFieldReference(actionContext.selection().getStartOffset(), updater, field);
      }
    }
    PsiDocumentManager.getInstance(project).commitDocument(document);

    PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(file, updater.getCaretOffset() - 1, PsiReferenceExpression.class, false);
    if (ref != null) {
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
    }

    ref = PsiTreeUtil.findElementOfClassAtOffset(file, updater.getCaretOffset() - 1, PsiReferenceExpression.class, false);
    PsiElement target = ref == null ? null : ref.resolve();
    if (PsiUtil.isJvmLocalVariable(target)) {
      makeFinalIfNeeded(ref, (PsiVariable)target);
    }
    if (target == null && ref != null &&
        JavaPsiFacade.getInstance(project).getResolveHelper()
          .resolveReferencedVariable(Objects.requireNonNull(variable.getName()), ref) == null) {
      BringVariableIntoScopeFix fix = BringVariableIntoScopeFix.fromReference(ref);
      if (fix != null) {
        if (fix.getPresentation(actionContext) != null) {
          ModCommandExecutor.getInstance().executeForFileCopy(fix.perform(actionContext), file);
        }
      }
    }
    if (JavaCompletionContributor.IN_SWITCH_LABEL.accepts(ref)) {
      PsiSwitchBlock block = Objects.requireNonNull(PsiTreeUtil.getParentOfType(ref, PsiSwitchBlock.class));
      JavaTailTypes.forSwitchLabel(block).processTail(updater, updater.getCaretOffset());
    }

    //final char completionChar = insertionContext.insertionCharacter();
    //if (completionChar == '=') {
    //  //context.setAddCompletionChar(false);
    //  EqTailType.INSTANCE.processTail(context.getEditor(), context.getTailOffset());
    //}
    //else if (completionChar == ',' && getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailTypes.unknownType()) {
    //  //context.setAddCompletionChar(false);
    //  CommaTailType.INSTANCE.processTail(context.getEditor(), context.getTailOffset());
    //  AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), null);
    //}
    //else if (completionChar == ':' && getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailTypes.unknownType() && isTernaryCondition(ref)) {
    //  //context.setAddCompletionChar(false);
    //  TailTypes.conditionalExpressionColonType().processTail(context.getEditor(), context.getTailOffset());
    //}
    //else if (completionChar == '.') {
    //  AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
    //}
    //else if (completionChar == '!' && myNegatable) {
    //  //context.setAddCompletionChar(false);
    //  if (ref != null) {
    //    FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
    //    document.insertString(ref.getTextRange().getStartOffset(), "!");
    //  }
    //}
    //else if (completionChar == Lookup.REPLACE_SELECT_CHAR) {
    //  removeEmptyCallParentheses(context);
    //}
  }

  private void qualifyFieldReference(int startOffset, ModPsiUpdater updater, PsiField field) {
    Document document = updater.getDocument();
    PsiFile file = updater.getPsiFile();
    final PsiReference reference = file.findReferenceAt(startOffset);
    if (reference instanceof PsiJavaCodeReferenceElement codeRef && codeRef.isQualified()) {
      return;
    }

    PsiClass containingClass = myQualifierClass != null && myQualifierClass.isValid() ? myQualifierClass : field.getContainingClass();
    if (containingClass != null && containingClass.getName() != null) {
      document.insertString(startOffset, field.hasModifierProperty(PsiModifier.STATIC) ? "." : ".this.");
      JavaCompletionUtil.insertClassReference(containingClass, file, startOffset);
      PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);
    }
  }

  public static void makeFinalIfNeeded(PsiElement place, PsiVariable variable) {
    if (PsiUtil.isAvailable(JavaFeature.EFFECTIVELY_FINAL, place) || JspPsiUtil.isInJspFile(place)) {
      return;
    }

    if (ControlFlowUtil.getScopeEnforcingEffectiveFinality(variable, place) != null &&
        !ControlFlowUtil.isReassigned(variable, new HashMap<>())) {
      PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, true);
    }
  }

  @Override
  public ModCompletionItemPresentation presentation() {
    PsiVariable variable = contextObject();
    String forcedQualifier = getForcedQualifier();
    boolean qualify = myHelper != null && !myHelper.willBeImported() || forcedQualifier != null;

    String name = Objects.requireNonNull(variable.getName());
    String locationTail = "";
    if (variable instanceof PsiField field) {
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null) {
        String className = containingClass.getName();
        if (qualify) {
          name = (forcedQualifier != null ? forcedQualifier : className + ".") + name;
        }
        String qname = containingClass.getQualifiedName();
        String pkg = qname == null ? "" : StringUtil.getPackageName(qname);
        locationTail = StringUtil.isNotEmpty(pkg) ? " (" + pkg + ")" : "";
        
        if (shouldImport() && StringUtil.isNotEmpty(className)) {
          locationTail = JavaBundle.message("member.in.class", className) + locationTail;
        }
      }
    }
    MarkupText main = MarkupText.plainText(name);
    if (JavaDeprecationUtils.isDeprecated(variable, null)) {
      main = main.highlightAll(MarkupText.Kind.STRIKEOUT);
    }
    if (myTailText != null) {
      if (myTailText.startsWith(VariableLookupItem.EQ)) {
        main = main.concat(" (" + myTailText + ")", MarkupText.Kind.EMPHASIZED);
      } else {
        main = main.concat(myTailText, MarkupText.Kind.GRAYED);
      }
    }
    main = main.concat(locationTail, MarkupText.Kind.GRAYED);
    ModCompletionItemPresentation presentation = new ModCompletionItemPresentation(main)
      .withMainIcon(() -> variable.getIcon(0));
    return presentation.withDetailText(JavaModCompletionUtils.typeMarkup(mySubstitutor.substitute(variable.getType())));
  }
}
