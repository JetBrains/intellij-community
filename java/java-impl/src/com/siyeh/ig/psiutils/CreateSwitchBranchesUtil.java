// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import one.util.streamex.Joining;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public final class CreateSwitchBranchesUtil {
  /**
   * @param names names of individual branches to create (non-empty)
   * @return a name of the action which creates missing switch branches.
   */
  public static @NotNull @Nls String getActionName(Collection<String> names) {
    if (names.size() == 1) {
      return InspectionGadgetsBundle.message("create.missing.switch.branch",
                                             StreamEx.of(names).collect(Joining.with("").maxChars(50).cutAfterDelimiter()));
    }
    return InspectionGadgetsBundle.message("create.missing.switch.branches", formatMissingBranches(names));
  }

  /**
   * @param names names of individual branches to create (non-empty)
   * @return a string which contains all the names (probably abbreviated if too long)
   */
  public static String formatMissingBranches(Collection<String> names) {
    return StreamEx.of(names).map(name -> name.startsWith("'") || name.startsWith("\"") ? name : "'" + name + "'").mapLast("and "::concat)
      .collect(Joining.with(", ").maxChars(50).cutAfterDelimiter());
  }

  /**
   * Create missing switch branches
   *
   * @param switchBlock a switch block to process
   * @param allNames an ordered list of all expected switch branches (e.g. list of all possible enum values)
   * @param missingNames a collection of missing branch names which should be created
   * @param caseExtractor a function which extracts list of the case string representations from the given switch label.
   *                      The resulting strings should appear in the allNames list if the label matches the same constant,
   *                      thus some kind of normalization could be necessary.
   * @return a list of created branches
   */
  public static List<PsiSwitchLabelStatementBase> createMissingBranches(@NotNull PsiSwitchBlock switchBlock,
                                                                        @NotNull List<String> allNames,
                                                                        @NotNull Collection<String> missingNames,
                                                                        @NotNull Function<? super PsiSwitchLabelStatementBase, ? extends List<String>> caseExtractor) {
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(switchBlock.getProject());
    boolean isRuleBasedFormat = SwitchUtils.isRuleFormatSwitch(switchBlock);
    final PsiCodeBlock body = switchBlock.getBody();
    final PsiExpression switchExpression = switchBlock.getExpression();
    PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(switchExpression != null ? switchExpression.getType() : null);
    boolean hasSealedClass = selectorClass != null &&
                             (selectorClass.hasModifierProperty(PsiModifier.SEALED) ||
                              selectorClass.getPermitsList() != null ||
                              (selectorClass instanceof PsiTypeParameter typeParameter &&
                               ContainerUtil.exists(typeParameter.getExtendsListTypes(), extType -> {
                                 PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(extType);
                                 return psiClass != null &&
                                        (psiClass.hasModifierProperty(PsiModifier.SEALED) ||
                                         psiClass.getPermitsList() != null);
                               })));
    boolean isPatternsGenerated = selectorClass != null && !selectorClass.isEnum() && hasSealedClass;
    if (body == null) {
      // replace entire switch statement if no code block is present
      @NonNls final StringBuilder newStatementText = new StringBuilder();
      CommentTracker commentTracker = new CommentTracker();
      newStatementText.append("switch(").append(switchExpression == null ? "" : commentTracker.text(switchExpression)).append("){");
      for (String missingName : missingNames) {
        newStatementText.append(String.join("", generateStatements(missingName, switchBlock, isRuleBasedFormat, isPatternsGenerated)));
      }
      newStatementText.append('}');
      PsiSwitchBlock block = (PsiSwitchBlock)commentTracker.replaceAndRestoreComments(switchBlock, newStatementText.toString());
      javaCodeStyleManager.shortenClassReferences(block);
      return PsiTreeUtil.getChildrenOfTypeAsList(block.getBody(), PsiSwitchLabelStatementBase.class);
    }
    Map<String, String> prevToNext = StreamEx.of(allNames).pairMap(Couple::of).toMap(c -> c.getFirst(), c -> c.getSecond());
    List<String> missingLabels = ContainerUtil.filter(allNames, missingNames::contains);
    String nextLabel = getNextLabel(prevToNext, missingLabels);
    PsiElement bodyElement = body.getFirstBodyElement();
    List<PsiSwitchLabelStatementBase> addedLabels = new ArrayList<>();
    if (bodyElement instanceof PsiWhiteSpace && bodyElement == body.getLastBodyElement()) {
      bodyElement.delete();
      bodyElement = null;
    }
    while (bodyElement != null) {
      PsiSwitchLabelStatementBase label = ObjectUtils.tryCast(bodyElement, PsiSwitchLabelStatementBase.class);
      if (label != null) {
        List<String> caseLabelNames = caseExtractor.apply(label);
        while (nextLabel != null && caseLabelNames.contains(nextLabel)) {
          addedLabels.add(addSwitchLabelStatementBefore(missingLabels.get(0), bodyElement, switchBlock, isRuleBasedFormat, isPatternsGenerated));
          missingLabels = missingLabels.subList(1, missingLabels.size());
          if (missingLabels.isEmpty()) {
            break;
          }
          nextLabel = getNextLabel(prevToNext, missingLabels);
        }
        if (SwitchUtils.isDefaultLabel(label)) {
          for (String missingElement : missingLabels) {
            addedLabels.add(addSwitchLabelStatementBefore(missingElement, bodyElement, switchBlock, isRuleBasedFormat, isPatternsGenerated));
          }
          missingLabels = Collections.emptyList();
          break;
        }
      }
      bodyElement = bodyElement.getNextSibling();
    }
    if (!missingLabels.isEmpty()) {
      final PsiElement lastChild = body.getLastChild();
      for (String missingElement : missingLabels) {
        addedLabels.add(addSwitchLabelStatementBefore(missingElement, lastChild, switchBlock, isRuleBasedFormat, isPatternsGenerated));
      }
    }
    addedLabels.replaceAll(label -> (PsiSwitchLabelStatementBase)javaCodeStyleManager.shortenClassReferences(label));
    return addedLabels;
  }

  /**
   * If necessary, starts a template to modify the bodies of created switch branches
   * @param block parent switch block
   * @param addedLabels list of created labels (returned from {@link #createMissingBranches(PsiSwitchBlock, List, Collection, Function)}).
   * @param updater updater to use to record template information
   */
  public static void createTemplate(PsiSwitchBlock block, List<PsiSwitchLabelStatementBase> addedLabels, ModPsiUpdater updater) {
    if (!(block instanceof PsiSwitchExpression)) return;
    List<PsiExpression> elementsToReplace = getElementsToReplace(addedLabels);
    ModTemplateBuilder builder = updater.templateBuilder();
    for (PsiExpression expression : elementsToReplace) {
      builder.field(expression, new ConstantNode(expression.getText()));
    }
  }

  @NotNull
  private static List<PsiExpression> getElementsToReplace(@NotNull List<@NotNull PsiSwitchLabelStatementBase> labels) {
    List<PsiExpression> elementsToReplace = new ArrayList<>();
    for (PsiSwitchLabelStatementBase label : labels) {
      if (label instanceof PsiSwitchLabeledRuleStatement rule) {
        PsiStatement body = rule.getBody();
        if (body instanceof PsiExpressionStatement statement) {
          ContainerUtil.addIfNotNull(elementsToReplace, statement.getExpression());
        }
      }
      else {
        PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(label);
        if (next instanceof PsiYieldStatement yieldStatement) {
          ContainerUtil.addIfNotNull(elementsToReplace, yieldStatement.getExpression());
        }
      }
    }
    return elementsToReplace;
  }

  /**
   * @param caseLabelName will be added after case keyword, i.e. case "caseLabelName"
   * @param switchBlock a switch block, that is going to be changed
   * @param isRuleBasedFormat true, if a switch block consists of arrows
   * @param isPatternsGenerated specify whether patterns are generated. If true, then pattern variable identifiers
   *                            will be generated as well i.e. case "caseLabelName" "patternVariableName"
   * @return list of generated statements depending on type of switch block
   */
  public static @NonNls List<String> generateStatements(@NotNull String caseLabelName, @NotNull PsiSwitchBlock switchBlock,
                                                        boolean isRuleBasedFormat, boolean isPatternsGenerated) {
    final String patternVariableName =
      isPatternsGenerated ? " " + new VariableNameGenerator(switchBlock, VariableKind.PARAMETER).byName(
        StringUtil.getShortName(caseLabelName)).generate(false) : "";
    if (switchBlock instanceof PsiSwitchExpression) {
      String value = TypeUtils.getDefaultValue(((PsiSwitchExpression)switchBlock).getType());
      if (isRuleBasedFormat) {
        return Collections.singletonList("case " + caseLabelName + patternVariableName + " -> " + value + ";");
      }
      else {
        return Arrays.asList("case " + caseLabelName + patternVariableName + ":", "yield " + value + ";");
      }
    }
    if (isRuleBasedFormat) {
      return Collections.singletonList("case " + caseLabelName + patternVariableName + " -> {}");
    }
    return Arrays.asList("case " + caseLabelName + patternVariableName + ":", "break;");
  }

  private static PsiSwitchLabelStatementBase addSwitchLabelStatementBefore(String labelExpression,
                                                                           PsiElement anchor,
                                                                           PsiSwitchBlock switchBlock,
                                                                           boolean isRuleBasedFormat,
                                                                           boolean isPatternsGenerated) {
    if (anchor instanceof PsiSwitchLabelStatement) {
      PsiElement sibling = PsiTreeUtil.skipWhitespacesBackward(anchor);
      while (sibling instanceof PsiSwitchLabelStatement) {
        anchor = sibling;
        sibling = PsiTreeUtil.skipWhitespacesBackward(anchor);
      }
    }
    PsiElement correctedAnchor = anchor;
    final PsiElement parent = anchor.getParent();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
    PsiSwitchLabelStatementBase result = null;
    for (String text : generateStatements(labelExpression, switchBlock, isRuleBasedFormat, isPatternsGenerated)) {
      PsiStatement statement = factory.createStatementFromText(text, parent);
      PsiElement inserted = parent.addBefore(statement, correctedAnchor);
      if (inserted instanceof PsiSwitchLabelStatementBase) {
        result = (PsiSwitchLabelStatementBase)inserted;
      }
      correctedAnchor = inserted.getNextSibling();
    }
    return result;
  }

  private static String getNextLabel(Map<String, String> nextLabels, List<String> missingLabels) {
    String nextLabel = nextLabels.get(missingLabels.get(0));
    while (missingLabels.contains(nextLabel)) {
      nextLabel = nextLabels.get(nextLabel);
    }
    return nextLabel;
  }

  /**
   * Prepares the document for starting the template and returns the editor.
   *
   * @param element any element from the document
   * @return an editor, or null if not found.
   */
  public static @Nullable Editor prepareForTemplateAndObtainEditor(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (!file.isPhysical()) return null;
    Project project = file.getProject();
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) return null;
    Document document = editor.getDocument();
    PsiFile topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
    if (topLevelFile == null || document != topLevelFile.getViewProvider().getDocument()) return null;
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    return editor;
  }
}
