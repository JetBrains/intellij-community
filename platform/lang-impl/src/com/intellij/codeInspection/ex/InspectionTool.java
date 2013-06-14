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

/*
 * Author: max
 * Date: Oct 9, 2001
 * Time: 8:50:56 PM
 */

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectionNode;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManagerImpl;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public abstract class InspectionTool extends InspectionProfileEntry {
  private GlobalInspectionContextImpl myContext;
  protected static String ourOutputPath;
  protected InspectionNode myToolNode;

  public void initialize(@NotNull GlobalInspectionContextImpl context) {
    myContext = context;
    projectOpened(context.getProject());
  }

  @NotNull
  public GlobalInspectionContextImpl getContext() {
    return myContext;
  }

  @NotNull
  public RefManager getRefManager() {
    return getContext().getRefManager();
  }

  public abstract void runInspection(@NotNull AnalysisScope scope, @NotNull InspectionManager manager);

  public void exportResults(@NotNull final Element parentNode) {
    getRefManager().iterate(new RefVisitor(){
      @Override
      public void visitElement(@NotNull RefEntity elem) {
        exportResults(parentNode, elem);
      }
    });
  }

  public abstract void exportResults(@NotNull Element parentNode, @NotNull RefEntity refEntity);

  public abstract boolean isGraphNeeded();
  @Nullable
  public QuickFixAction[] getQuickFixes(@NotNull RefEntity[] refElements) {
    return null;
  }

  @NotNull
  public abstract JobDescriptor[] getJobDescriptors(@NotNull GlobalInspectionContext globalInspectionContext);

  public boolean queryExternalUsagesRequests(@NotNull InspectionManager manager) {
    return false;
  }

  @Override
  public boolean isEnabledByDefault() {
    return getDefaultLevel() != HighlightDisplayLevel.DO_NOT_SHOW;
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public final String getDescriptionFileName() {
    return getShortName() + ".html";
  }

  public final String getFolderName() {
    return getShortName();
  }

  @Override
  public void cleanup() {
    if (myContext != null) {
      projectClosed(myContext.getProject());
    }
    myContext = null;
  }

  public void finalCleanup(){
    cleanup();
  }

  @NotNull
  public abstract HTMLComposerImpl getComposer();

  public abstract boolean hasReportedProblems();

  public abstract void updateContent();

  public abstract Map<String, Set<RefEntity>> getContent();

  @Nullable
  public abstract Map<String, Set<RefEntity>> getOldContent();

  public boolean isOldProblemsIncluded() {
    final GlobalInspectionContextImpl context = getContext();
    return context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN && getOldContent() != null;
  }

  @Nullable
  public Set<RefModule> getModuleProblems(){
    return null;
  }

  public abstract void ignoreCurrentElement(RefEntity refElement);

  @NotNull
  public abstract Collection<RefEntity> getIgnoredRefElements();

  public abstract void amnesty(RefEntity refEntity);

  public abstract boolean isElementIgnored(final RefEntity element);

  @NotNull
  public abstract FileStatus getElementStatus(final RefEntity element);

  @NotNull
  protected static FileStatus calcStatus(boolean old, boolean current) {
    if (old) {
      if (!current) {
        return FileStatus.DELETED;
      }
    }
    else if (current) {
      return FileStatus.ADDED;
    }
    return FileStatus.NOT_CHANGED;
  }

  protected static boolean contains(RefEntity element, @NotNull Collection<RefEntity> entities){
    for (RefEntity refEntity : entities) {
      if (Comparing.equal(refEntity, element)) {
        return true;
      }
    }
    return false;
  }

  protected HighlightSeverity getCurrentSeverity(@NotNull RefElement element) {
    final PsiElement psiElement = element.getPointer().getContainingFile();
    if (psiElement != null) {
      if (myContext != null) {
        final Tools tools = myContext.getTools().get(getShortName());
        if (tools != null) {
          for (ScopeToolState state : tools.getTools()) {
            InspectionToolWrapper toolWrapper = (InspectionToolWrapper)state.getTool();
            if (toolWrapper == this) {
              return myContext.getCurrentProfile().getErrorLevel(HighlightDisplayKey.find(getShortName()), psiElement).getSeverity();
            }
          }
        }

        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(getContext().getProject()).getInspectionProfile();
        final HighlightDisplayLevel level = profile.getErrorLevel(HighlightDisplayKey.find(getShortName()), psiElement);
        return level.getSeverity();
      }
    }
    return null;
  }

  protected static String getTextAttributeKey(@NotNull Project project, @NotNull HighlightSeverity severity, @NotNull ProblemHighlightType highlightType) {
    if (highlightType == ProblemHighlightType.LIKE_DEPRECATED) {
      return HighlightInfoType.DEPRECATED.getAttributesKey().getExternalName();
    }
    if (highlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL && severity == HighlightSeverity.ERROR) {
      return HighlightInfoType.WRONG_REF.getAttributesKey().getExternalName();
    }
    if (highlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) {
      return HighlightInfoType.UNUSED_SYMBOL.getAttributesKey().getExternalName();
    }
    SeverityRegistrar registrar = InspectionProjectProfileManagerImpl.getInstanceImpl(project).getSeverityRegistrar();
    return registrar.getHighlightInfoTypeBySeverity(severity).getAttributesKey().getExternalName();
  }

  public static void setOutputPath(final String output) {
    ourOutputPath = output;
  }

  @SuppressWarnings({"UnusedDeclaration"})
  @Nullable
  public IntentionAction findQuickFixes(final CommonProblemDescriptor descriptor, final String hint) {
    return null;
  }

  @Nullable
  public SuppressIntentionAction[] getSuppressActions() {
    return null;
  }

  @NotNull
  public InspectionNode createToolNode(@NotNull InspectionRVContentProvider provider, @NotNull InspectionTreeNode parentNode, final boolean showStructure) {
    myToolNode = new InspectionNode(this);
    provider.appendToolNodeContent(myToolNode, parentNode, showStructure);
    return myToolNode;
  }
}
