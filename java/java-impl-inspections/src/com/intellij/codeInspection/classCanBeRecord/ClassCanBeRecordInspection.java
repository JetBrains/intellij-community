// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.classCanBeRecord.ConvertToRecordFix.RecordCandidate;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public class ClassCanBeRecordInspection extends BaseInspection {
  private static final List<String> IGNORED_ANNOTATIONS = List.of("io.micronaut.*", "jakarta.*", "javax.*", "org.springframework.*");

  public @NotNull ConversionStrategy myConversionStrategy = ConversionStrategy.DO_NOT_SUGGEST;
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
  public boolean shouldInspect(@NotNull PsiFile file) {
    return HighlightingFeature.RECORDS.isAvailable(file);
  }

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return JavaBundle.message("class.can.be.record.display.name");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassCanBeRecordVisitor(myConversionStrategy != ConversionStrategy.DO_NOT_SUGGEST, suggestAccessorsRenaming,
                                       myIgnoredAnnotations);
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return myConversionStrategy == ConversionStrategy.SHOW_AFFECTED_MEMBERS;
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    List<InspectionGadgetsFix> fixes = new SmartList<>();
    fixes.add(new ConvertToRecordFix(myConversionStrategy == ConversionStrategy.SHOW_AFFECTED_MEMBERS, suggestAccessorsRenaming,
                                     myIgnoredAnnotations));
    boolean isOnTheFly = (boolean)infos[0];
    if (isOnTheFly) {
      PsiClass psiClass = ObjectUtils.tryCast(infos[1], PsiClass.class);
      if (psiClass != null) {
        PsiAnnotation[] annotations = psiClass.getAnnotations();
        // rare corner case: we don't want to make a quick-fix list too wide
        if (annotations.length > 0 && annotations.length < 4) {
          for (PsiAnnotation annotation : annotations) {
            String fqn = annotation.getQualifiedName();
            if (fqn != null) {
              fixes.add(new AddIgnoredAnnotationFix(fqn));
            }
          }
        }
      }
    }
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("suggestAccessorsRenaming", JavaBundle.message("class.can.be.record.suggest.renaming.accessors")),
      dropdown("myConversionStrategy", JavaBundle.message("class.can.be.record.conversion.make.member.more.accessible"),
               ConversionStrategy.class, ConversionStrategy::getMessage),
      stringList("myIgnoredAnnotations", JavaBundle.message("class.can.be.record.suppress.conversion.if.annotated"))
        .description(HtmlChunk.raw(JavaCompilerBundle.message("class.can.be.record.suppress.conversion.if.annotated.description"))));
  }

  private static class ClassCanBeRecordVisitor extends BaseInspectionVisitor {
    private final boolean myRenameMembersThatBecomeMoreAccessible;
    private final boolean mySuggestAccessorsRenaming;
    private final List<String> myIgnoredAnnotations;

    private ClassCanBeRecordVisitor(boolean renameMembersThatBecomeMoreAccessible,
                                    boolean suggestAccessorsRenaming,
                                    @NotNull List<String> ignoredAnnotations) {
      myRenameMembersThatBecomeMoreAccessible = renameMembersThatBecomeMoreAccessible;
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
      if (!myRenameMembersThatBecomeMoreAccessible && !ConvertToRecordProcessor.findAffectedMembersUsages(recordCandidate).isEmpty()) return;
      registerError(classIdentifier, isOnTheFly(), aClass);
    }
  }

  public enum ConversionStrategy {
    DO_NOT_SUGGEST("class.can.be.record.conversion.strategy.do.not.convert"),
    SHOW_AFFECTED_MEMBERS("class.can.be.record.conversion.strategy.show.members"),
    SILENTLY("class.can.be.record.conversion.strategy.convert.silently");

    @Nls
    private final String messageKey;

    ConversionStrategy(@Nls String messageKey) {
      this.messageKey = messageKey;
    }

    @Nls
    String getMessage() {
      return JavaBundle.message(messageKey);
    }
  }

  private class AddIgnoredAnnotationFix extends InspectionGadgetsFix {
    @NlsSafe private final @NotNull String myPackagePrefix;

    private AddIgnoredAnnotationFix(@NotNull String packagePrefix) {
      myPackagePrefix = packagePrefix;
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("class.can.be.record.suppress.conversion.if.annotated.fix.name", myPackagePrefix);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("class.can.be.record.suppress.conversion.if.annotated.fix.family.name");
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      List<@NlsSafe String> prefixes = StreamEx.of(myIgnoredAnnotations).append(myPackagePrefix).sorted().toList();
      return IntentionPreviewInfo.addListOption(prefixes, myPackagePrefix, JavaBundle.message("class.can.be.record.suppress.conversion.if.annotated"));
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myIgnoredAnnotations.add(myPackagePrefix);
      ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
    }
  }
}
