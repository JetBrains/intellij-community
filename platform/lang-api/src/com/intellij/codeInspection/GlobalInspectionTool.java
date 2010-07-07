/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefVisitor;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for global inspections. Global inspections work only in batch mode
 * (when the &quot;Analyze / Inspect Code&quot; is invoked) and can access the
 * complete graph of references between classes, methods and other elements in the scope
 * selected for the analysis.
 *
 * @author anna
 * @see LocalInspectionTool
 * @since 6.0
 */
public abstract class GlobalInspectionTool extends InspectionProfileEntry {
  /**
   * Returns the annotator which will receive callbacks while the reference graph
   * is being built. The annotator can be used to add additional markers to reference
   * graph nodes, through calls to {@link RefEntity#putUserData}.
   *
   * @param refManager the reference graph manager instance
   * @return the annotator instance, or null if the inspection does not need any
   * additional markers or does not use the reference graph at all.
   * @see #isGraphNeeded
   */
  @Nullable
  public RefGraphAnnotator getAnnotator(final RefManager refManager) {
    return null;
  }

  /**
   * Runs the global inspection. If building of the reference graph was requested by one of the
   * global inspection tools, this method is called after the graph has been built and before the
   * external usages are processed. The default implementation of the method passes each node
   * of the graph for processing to {@link #checkElement}.
   *
   * @param scope                        the scope on which the inspection was run.
   * @param manager                      the inspection manager instance for the project on which the inspection was run.
   * @param globalContext                the context for the current global inspection run.
   * @param problemDescriptionsProcessor the collector for problems reported by the inspection
   */
  public void runInspection(final AnalysisScope scope,
                            final InspectionManager manager,
                            final GlobalInspectionContext globalContext,
                            final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefVisitor() {
      @Override public void visitElement(RefEntity refEntity) {
        if (!globalContext.shouldCheck(refEntity, GlobalInspectionTool.this)) return;
        CommonProblemDescriptor[] descriptors = checkElement(refEntity, scope, manager, globalContext, problemDescriptionsProcessor);
        if (descriptors != null) {
          problemDescriptionsProcessor.addProblemElement(refEntity, descriptors);
        }
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
  @Nullable
  public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope scope, InspectionManager manager, GlobalInspectionContext globalContext) {
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
  @Nullable
  public CommonProblemDescriptor[] checkElement(RefEntity refEntity,
                                                AnalysisScope scope,
                                                InspectionManager manager,
                                                GlobalInspectionContext globalContext,
                                                ProblemDescriptionsProcessor processor) {
    return checkElement(refEntity, scope, manager, globalContext);
  }

  /**
   * Checks if this inspection requires building of the reference graph. The reference graph
   * is built if at least one of the global inspection has requested that.
   *
   * @return true if the reference graph is required, false if the inspection does not use a
   * reference graph (refEntities) and uses some other APIs for its processing.
   */
  public boolean isGraphNeeded() {
    return true;
  }


  public boolean isEnabledByDefault() {
    return true;
  }

  /**
   * Allows the inspection to process usages of analyzed classes outside the analysis scope.
   * This method is called after the reference graph has been built and after
   * the {@link #runInspection} method has collected the list of problems for the current scope.
   * In order to save time when multiple inspections need to process
   * usages of the same classes and methods, usage searches are not performed directly, but
   * instead are queued for batch processing through
   * {@link GlobalJavaInspectionContext#enqueueClassUsagesProcessor} and similar methods. The method
   * can add new problems to <code>problemDescriptionsProcessor</code> or remove some of the problems
   * collected by {@link #runInspection} by calling {@link ProblemDescriptionsProcessor#ignoreElement}.
   *
   * @param manager                      the inspection manager instance for the project on which the inspection was run.
   * @param globalContext                the context for the current global inspection run.
   * @param problemDescriptionsProcessor the collector for problems reported by the inspection.
   * @return true if a repeated call to this method is required after the queued usage processors
   *         have completed work, false otherwise.
   */
  public boolean queryExternalUsagesRequests(final InspectionManager manager,
                                             final GlobalInspectionContext globalContext,
                                             final ProblemDescriptionsProcessor problemDescriptionsProcessor){
    return false;
  }

  /**
   * Allows TeamCity plugin to reconstract quickfixes from server side data
   * @param hint a hint to distinguish different quick fixes for one problem
   * @return quickfix to be shown in editor when server side insepctions are enabled
   */
  @Nullable
  public QuickFix getQuickFix(final String hint) {
    return null;
  }

  /**
   * Allows TeamCity plugin to serialize quick fixes on server in order to reconstract them in idea
   * @param fix fix to be serialized
   * @return hint to be stored on server
   */
  @Nullable
  public String getHint(final QuickFix fix) {
    return null;
  }

  /**
   * Allows additional description to refEntity problems
   * @param buf page content with problem description
   * @param refEntity entity to describe
   * @param composer provides sample api to compose html
   */
  public void compose(StringBuffer buf, RefEntity refEntity, HTMLComposer composer) {
  }

  /**
   * @return JobDescriptors array to show inspection progress correctly. TotalAmount should be set (e.g. in
   * {@link #runInspection(com.intellij.analysis.AnalysisScope, InspectionManager, GlobalInspectionContext, ProblemDescriptionsProcessor)})
   * ProgressIndicator should progress with {@link com.intellij.codeInspection.GlobalInspectionContext#incrementJobDoneAmount(com.intellij.codeInspection.ex.JobDescriptor, String)}  
   */
  @Nullable
  public JobDescriptor[] getAdditionalJobs() {
    return null;
  }
}
