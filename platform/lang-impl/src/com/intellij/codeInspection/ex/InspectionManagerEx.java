/*
 * Author: max
 * Date: Oct 9, 2001
 * Time: 8:43:17 PM
 */

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedPaneContentUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class InspectionManagerEx extends InspectionManager {
  private GlobalInspectionContextImpl myGlobalInspectionContext = null;
  private final Project myProject;
  @NonNls private String myCurrentProfileName;
  private final NotNullLazyValue<ContentManager> myContentManager;

  private final Set<GlobalInspectionContextImpl> myRunningContexts = new HashSet<GlobalInspectionContextImpl>();

  public InspectionManagerEx(Project project) {
    myProject = project;
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myContentManager = new NotNullLazyValue<ContentManager>() {
        @NotNull
        @Override
        protected ContentManager compute() {
          ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
          ToolWindow toolWindow =
            toolWindowManager.registerToolWindow(ToolWindowId.INSPECTION, true, ToolWindowAnchor.BOTTOM, myProject);
          ContentManager contentManager = toolWindow.getContentManager();
          toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowInspection.png"));
          new ContentManagerWatcher(toolWindow, contentManager);
          return contentManager;
        }
      };
    }
    else {
      myContentManager = new NotNullLazyValue<ContentManager>() {
        @NotNull
        @Override
        protected ContentManager compute() {
          return ContentFactory.SERVICE.getInstance().createContentManager(new TabbedPaneContentUI(), true, myProject);
        }
      };
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public CommonProblemDescriptor createProblemDescriptor(@NotNull String descriptionTemplate, QuickFix... fixes) {
    return new CommonProblemDescriptorImpl(fixes, descriptionTemplate);
  }

  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   LocalQuickFix fix,
                                                   ProblemHighlightType highlightType) {
    LocalQuickFix[] quickFixes = fix != null ? new LocalQuickFix[]{fix} : null;
    return createProblemDescriptor(psiElement, descriptionTemplate, quickFixes, highlightType);
  }

  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   LocalQuickFix[] fixes,
                                                   ProblemHighlightType highlightType) {
    return createProblemDescriptor(psiElement, descriptionTemplate, fixes, highlightType, false);
  }

  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement psiElement,
                                                   @NotNull String descriptionTemplate,
                                                   LocalQuickFix[] fixes,
                                                   ProblemHighlightType highlightType,
                                                   boolean isAfterEndOfLine) {
    return new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, isAfterEndOfLine, null);
  }

  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull PsiElement startElement,
                                                   @NotNull PsiElement endElement,
                                                   @NotNull String descriptionTemplate,
                                                   ProblemHighlightType highlightType,
                                                   LocalQuickFix... fixes) {
    return new ProblemDescriptorImpl(startElement, endElement, descriptionTemplate, fixes, highlightType, false, null);
  }

  public ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                   final TextRange rangeInElement,
                                                   @NotNull final String descriptionTemplate,
                                                   final ProblemHighlightType highlightType,
                                                   final LocalQuickFix... fixes) {
    return new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, rangeInElement);
  }

  public ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement, @NotNull final String descriptionTemplate, final ProblemHighlightType highlightType,
                                                   @Nullable final HintAction hintAction,
                                                   final LocalQuickFix... fixes) {

    return new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, null, hintAction);
  }

  public GlobalInspectionContextImpl createNewGlobalContext(boolean reuse) {
    if (reuse) {
      if (myGlobalInspectionContext == null) {
        myGlobalInspectionContext = new GlobalInspectionContextImpl(myProject, myContentManager);
      }
      myRunningContexts.add(myGlobalInspectionContext);
      return myGlobalInspectionContext;
    }
    final GlobalInspectionContextImpl inspectionContext = new GlobalInspectionContextImpl(myProject, myContentManager);
    myRunningContexts.add(inspectionContext);
    return inspectionContext;
  }

  public void setProfile(final String name) {
    myCurrentProfileName = name;
  }

  public String getCurrentProfile() {
    if (myCurrentProfileName == null) {
      final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(myProject);
      myCurrentProfileName = profileManager.getProjectProfile();
      if (myCurrentProfileName == null) {
        myCurrentProfileName = InspectionProfileManager.getInstance().getRootProfile().getName();
      }
    }
    return myCurrentProfileName;
  }

  public void closeRunningContext(GlobalInspectionContextImpl globalInspectionContext){
    myRunningContexts.remove(globalInspectionContext);
  }

  public Set<GlobalInspectionContextImpl> getRunningContexts() {
    return myRunningContexts;
  }

  public static boolean inspectionResultSuppressed(PsiElement place, LocalInspectionTool tool) {
    if (tool instanceof CustomSuppressableInspectionTool) {
      return ((CustomSuppressableInspectionTool)tool).isSuppressedFor(place);
    }
    return isSuppressed(place, tool.getID()) || isSuppressed(place, tool.getAlternativeID());
  }

  public static boolean canRunInspections(final Project project, final boolean online) {
    for (InspectionExtensionsFactory factory : Extensions.getExtensions(InspectionExtensionsFactory.EP_NAME)) {
      if (!factory.isProjectConfiguredToRunInspections(project, online)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isSuppressed(PsiElement psiElement, String id) {
    if (id == null) return false;
    for (InspectionExtensionsFactory factory : Extensions.getExtensions(InspectionExtensionsFactory.EP_NAME)) {
      if (!factory.isToCheckMember(psiElement, id)) {
        return true;
      }
    }
    return false;
  }
}
