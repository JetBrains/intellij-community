// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for global inspections.
 * Global inspections work only in batch mode (when "Analyze / Inspect Code" is invoked)
 * and can access the complete graph of references between classes, methods and other elements
 * in the scope selected for the analysis.
 * <p>
 * Global inspections can use a shared local inspection tool for highlighting the cases
 * that do not need global analysis in the editor by implementing {@link #getSharedLocalInspectionTool()}
 * The shared local inspection tools shares settings and documentation with the global inspection tool.
 *
 * @author anna
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/code-inspections.html">Code Inspections (IntelliJ Platform Docs)</a>
 * @see LocalInspectionTool
 */
public abstract class GlobalInspectionTool extends InspectionProfileEntry {
  @Override
  public final @NotNull String getSuppressId() {
    return super.getSuppressId();
  }

  /**
   * Returns the annotator which will receive callbacks while the reference graph
   * is being built. The annotator can be used to add additional markers to reference
   * graph nodes, through calls to {@link RefEntity#putUserData(com.intellij.openapi.util.Key, Object)}.
   *
   * @param refManager the reference graph manager instance
   * @return the annotator instance, or null if the inspection does not need any
   * additional markers or does not use the reference graph at all.
   * @see #isGraphNeeded
   */
  public @Nullable RefGraphAnnotator getAnnotator(@NotNull RefManager refManager) {
    return null;
  }

  /**
   * Runs the global inspection. If building of the reference graph was requested by one of the
   * global inspection tools, this method is called after the graph has been built and before the
   * external usages are processed. The default implementation of the method passes each node
   * of the graph for processing to {@link #checkElement(RefEntity, AnalysisScope, InspectionManager, GlobalInspectionContext)}.
   *
   * @param scope                        the scope on which the inspection was run.
   * @param manager                      the inspection manager instance for the project on which the inspection was run.
   * @param globalContext                the context for the current global inspection run.
   * @param problemDescriptionsProcessor the collector for problems reported by the inspection
   */
  public void runInspection(final @NotNull AnalysisScope scope,
                            final @NotNull InspectionManager manager,
                            final @NotNull GlobalInspectionContext globalContext,
                            final @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (!globalContext.shouldCheck(refEntity, GlobalInspectionTool.this)) return;
        if (!isInScope(refEntity)) return;
        CommonProblemDescriptor[] descriptors = checkElement(refEntity, scope, manager, globalContext, problemDescriptionsProcessor);
        if (descriptors != null) {
          problemDescriptionsProcessor.addProblemElement(refEntity, descriptors);
        }
      }

      private boolean isInScope(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefElement) {
          SmartPsiElementPointer pointer = ((RefElement)refEntity).getPointer();
          if (pointer != null) {
            VirtualFile virtualFile = pointer.getVirtualFile();
            if (virtualFile != null && !scope.contains(virtualFile)) return false;
          }
          else {
            RefEntity owner = refEntity.getOwner();
            return owner == null || isInScope(owner);
          }
        }
        if (refEntity instanceof RefModule) {
          return scope.containsModule(((RefModule)refEntity).getModule());
        }
        return true;
      }
    });
  }

  /**
   * Processes and reports problems for a single element of the completed reference graph.
   *
   * @param refEntity     the reference graph element to check for problems.
   * @param scope         the scope on which analysis was invoked.
   * @param manager       the inspection manager instance for the project on which the inspection was run.
   * @param globalContext the context for the current global inspection run.
   * @return the problems found for the element, or null if no problems were found.
   */
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext) {
    return null;
  }

  /**
   * Processes and reports problems for a single element of the completed reference graph.
   *
   * @param refEntity     the reference graph element to check for problems.
   * @param scope         the scope on which analysis was invoked.
   * @param manager       the inspection manager instance for the project on which the inspection was run.
   * @param globalContext the context for the current global inspection run.
   * @param processor     the collector for problems reported by the inspection
   * @return the problems found for the element, or null if no problems were found.
   */
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext,
                                                           @NotNull ProblemDescriptionsProcessor processor) {
    return checkElement(refEntity, scope, manager, globalContext);
  }

  /**
   * Only called when {@link #isGlobalSimpleInspectionTool()} returns true.
   * Processes and reports problems for a single psi file without using the reference graph.
   *
   * @param psiFile           the file to check
   * @param manager        the inspection manager instance for the project on which the inspection was run.
   * @param problemsHolder used to register problems found.
   * @param globalContext  the context for the current global inspection run.
   * @param processor      the collector for problems reported by the inspection (see also {@code problemsHolder}).
   */
  public void checkFile(@NotNull PsiFile psiFile,
                        @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext,
                        @NotNull ProblemDescriptionsProcessor processor) {
    assert isGlobalSimpleInspectionTool();
  }

  /**
   * Should this global inspection be run as a global simple inspection tool?
   * When true {@link #checkFile} will be called for every file in the inspection scope.
   * When false {@link #checkElement} and {@link #runInspection} will be called.
   *
   * @return true, when this inspection should be run as a global simple inspection tool, false otherwise.
   */
  public boolean isGlobalSimpleInspectionTool() {
    return false;
  }

  /**
   * Checks if this inspection requires building of the reference graph. The reference graph
   * is built if at least one of the global inspections has requested it.
   *
   * @return true if the reference graph is required, false if the inspection does not use a
   * reference graph (refEntities) and uses some other APIs for its processing.
   */
  public boolean isGraphNeeded() {
    return !isGlobalSimpleInspectionTool();
  }

  /**
   * True by default to ensure third party plugins are not broken.
   *
   * @return true if inspection should be started ({@link #runInspection(AnalysisScope, InspectionManager, GlobalInspectionContext, ProblemDescriptionsProcessor)}) in ReadAction,
   * false if ReadAction is taken by inspection itself
   */
  public boolean isReadActionNeeded() {
    return true;
  }


  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  /**
   * Allows the inspection to process usages of analyzed classes outside the analysis scope.
   * This method is called after the reference graph has been built and after
   * the {@link #runInspection(AnalysisScope, InspectionManager, GlobalInspectionContext, ProblemDescriptionsProcessor)}
   * method has collected the list of problems for the current scope.
   * In order to save time when multiple inspections need to process
   * usages of the same classes and methods, usage searches are not performed directly, but
   * instead are queued for batch processing through
   * {@link GlobalJavaInspectionContext#enqueueClassUsagesProcessor} and similar methods. The method
   * can add new problems to {@code problemDescriptionsProcessor} or remove some of the problems
   * collected by {@link #runInspection(AnalysisScope, InspectionManager, GlobalInspectionContext, ProblemDescriptionsProcessor)}
   * by calling {@link ProblemDescriptionsProcessor#ignoreElement(RefEntity)}.
   * <p>
   * NOTE: if you want to check references in files which are not included in the graph e.g., in xml, which may be located in the scope,
   * you need to explicitly disable optimization and override {@link #getAdditionalJobs(GlobalInspectionContext)}
   *
   * @param manager                      the inspection manager instance for the project on which the inspection was run.
   * @param globalContext                the context for the current global inspection run.
   * @param problemDescriptionsProcessor the collector for problems reported by the inspection.
   * @return true if a repeated call to this method is required after the queued usage processors
   *         have completed work, false otherwise.
   */
  public boolean queryExternalUsagesRequests(@NotNull InspectionManager manager,
                                             @NotNull GlobalInspectionContext globalContext,
                                             @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor){
    return false;
  }

  /**
   * Allows TeamCity plugin to reconstruct quickfixes from server side data
   * @param hint a hint to distinguish different quick fixes for one problem
   * @return quickfix to show in the editor when server side inspections are enabled
   */
  public @Nullable QuickFix getQuickFix(final String hint) {
    return null;
  }

  /**
   * Allows TeamCity plugin to serialize quick fixes on server in order to reconstruct them in idea
   * @param fix fix to be serialized
   * @return hint to be stored on server
   */
  public @Nullable String getHint(@NotNull QuickFix fix) {
    return null;
  }

  /**
   * Allows additional description to refEntity problems
   * @param buf page content with problem description
   * @param refEntity entity to describe
   * @param composer provides sample api to compose html
   */
  public void compose(@NotNull StringBuilder buf, @NotNull RefEntity refEntity, @NotNull HTMLComposer composer) {
  }

  /**
   * @return JobDescriptors array to show inspection progress correctly. TotalAmount should be set (e.g. in
   * {@link #runInspection(AnalysisScope, InspectionManager, GlobalInspectionContext, ProblemDescriptionsProcessor)})
   * ProgressIndicator should progress with {@link GlobalInspectionContext#incrementJobDoneAmount(JobDescriptor, String)}
   */
  public JobDescriptor @Nullable [] getAdditionalJobs() {
    return null;
  }

  /**
   * @return JobDescriptors array to show inspection progress correctly. TotalAmount should be set (e.g. in
   * {@link #runInspection(AnalysisScope, InspectionManager, GlobalInspectionContext, ProblemDescriptionsProcessor)})
   * ProgressIndicator should progress with {@link GlobalInspectionContext#incrementJobDoneAmount(JobDescriptor, String)}
   */
  public JobDescriptor @Nullable [] getAdditionalJobs(@NotNull GlobalInspectionContext context) {
    return getAdditionalJobs();
  }

  /**
   * In some cases we can do highlighting in annotator or high. visitor based on global inspection or use a shared local inspection tool
   */
  public boolean worksInBatchModeOnly() {
    return getSharedLocalInspectionTool() == null;
  }

  /**
   * Returns the local inspection tool used for highlighting in the editor. Meant for global inspections which have a local component.
   * The local inspection tool is not required to report on the exact same problems, and naturally can't use global analysis. The local
   * inspection tool is not used in batch mode.
   *
   * For example a global inspection that reports a package could have a local inspection tool which highlights
   * the package statement in a file.
   */
  public @Nullable LocalInspectionTool getSharedLocalInspectionTool() {
    return null;
  }
}
