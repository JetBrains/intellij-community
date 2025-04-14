// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.codeInspection.AddToInspectionOptionListFix;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.classCanBeRecord.ConvertToRecordFix.RecordCandidate;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiIdentifier;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static com.intellij.codeInspection.ProblemHighlightType.INFORMATION;
import static com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy.*;
import static com.intellij.codeInspection.options.OptPane.*;

public final class ClassCanBeRecordInspection extends BaseInspection implements CleanupLocalInspectionTool {
  private static final List<String> IGNORED_ANNOTATIONS = List.of("io.micronaut.*", "jakarta.*", "javax.*", "org.springframework.*");

  public @NotNull ConversionStrategy myConversionStrategy = SHOW_AFFECTED_MEMBERS;
  public boolean suggestAccessorsRenaming = true;

  public List<@NlsSafe String> myIgnoredAnnotations = new ArrayList<>();

  public ClassCanBeRecordInspection() {
    myIgnoredAnnotations.addAll(IGNORED_ANNOTATIONS);
  }

  @TestOnly
  public ClassCanBeRecordInspection(@NotNull ConversionStrategy conversionStrategy, boolean suggestAccessorsRenaming) {
    myConversionStrategy = conversionStrategy;
    this.suggestAccessorsRenaming = suggestAccessorsRenaming;
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.RECORDS);
  }

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return JavaBundle.message("class.can.be.record.display.name");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassCanBeRecordVisitor(myConversionStrategy, suggestAccessorsRenaming, myIgnoredAnnotations);
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    List<LocalQuickFix> fixes = new SmartList<>();

    boolean suggestQuickFix = (boolean)infos[0];
    if (suggestQuickFix) {
      fixes.add(new ConvertToRecordFix(suggestAccessorsRenaming, myIgnoredAnnotations));
      PsiClass psiClass = ObjectUtils.tryCast(infos[1], PsiClass.class);
      if (psiClass != null) {
        PsiAnnotation[] annotations = psiClass.getAnnotations();
        // rare corner case: we don't want to make a quick-fix list too wide
        if (annotations.length > 0 && annotations.length < 4) {
          for (PsiAnnotation annotation : annotations) {
            String fqn = annotation.getQualifiedName();
            if (fqn != null) {
              fixes.add(new AddToInspectionOptionListFix<>(
                this, JavaBundle.message("class.can.be.record.suppress.conversion.if.annotated.fix.name", fqn),
                fqn, tool -> tool.myIgnoredAnnotations)
              );
            }
          }
        }
      }
    }
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    //noinspection InjectedReferences
    return pane(
      checkbox("suggestAccessorsRenaming", JavaBundle.message("class.can.be.record.suggest.renaming.accessors")),
      checkbox("noHighlightingFixAvailable", JavaBundle.message("class.can.be.record.record.highlight.when.semantics.change")).description(
        JavaBundle.message("class.can.be.record.record.highlight.when.semantics.change.description")),
      stringList("myIgnoredAnnotations", JavaBundle.message("class.can.be.record.suppress.conversion.if.annotated"))
        .description(HtmlChunk.raw(JavaCompilerBundle.message("class.can.be.record.suppress.conversion.if.annotated.description"))));
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController()
      .onValue(
        "noHighlightingFixAvailable",
        () -> myConversionStrategy == DO_NOT_SUGGEST,
        (newValue) -> {
          myConversionStrategy = newValue ? DO_NOT_SUGGEST : SHOW_AFFECTED_MEMBERS;
        }
      );
  }

  private static class ClassCanBeRecordVisitor extends BaseInspectionVisitor {
    private final ConversionStrategy myConversionStrategy;
    private final boolean mySuggestAccessorsRenaming;
    private final List<String> myIgnoredAnnotations;

    private ClassCanBeRecordVisitor(ConversionStrategy conversionStrategy,
                                    boolean suggestAccessorsRenaming,
                                    @NotNull List<String> ignoredAnnotations) {
      myConversionStrategy = conversionStrategy;
      mySuggestAccessorsRenaming = suggestAccessorsRenaming;
      myIgnoredAnnotations = ignoredAnnotations;
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      PsiIdentifier classIdentifier = aClass.getNameIdentifier();
      if (classIdentifier == null) return;
      RecordCandidate recordCandidate = ConvertToRecordFix.getClassDefinition(aClass, mySuggestAccessorsRenaming, myIgnoredAnnotations);
      if (recordCandidate == null) return;

      boolean suggestQuickFix = true;
      boolean noHighlightingButKeepFixAvailable = myConversionStrategy == DO_NOT_SUGGEST || myConversionStrategy == SILENTLY;
      boolean isConflictFree = ConvertToRecordProcessor.findConflicts(recordCandidate).isEmpty();
      ProblemHighlightType highlightType = GENERIC_ERROR_OR_WARNING;
      if (!isConflictFree) {
        if (noHighlightingButKeepFixAvailable) {
          highlightType = INFORMATION;
        }
        if (!isOnTheFly()) {
          // Don't suggest a quick-fix if there are conflicts and we're running in batch (=not "on the fly") mode
          suggestQuickFix = false;
        }
      }

      registerError(classIdentifier, highlightType, suggestQuickFix, aClass);
    }
  }

  /// This enum (and its values) has been preserved for backward compatibility.
  ///
  /// Highlighting is always enabled when conversion to record will not weaken accessibility (of course, as long as the inspection is enabled).
  /// Conflicts view is always shown when accessibility would be weakened.
  public enum ConversionStrategy {
    /// Do not report a problem but keep the fix available.
    DO_NOT_SUGGEST,

    /// Report a problem, together with the fix.
    SHOW_AFFECTED_MEMBERS,

    /**
     * Kept for backward compatibility but no longer visible in the UI.
     */
    @ApiStatus.Obsolete SILENTLY,
  }
}
