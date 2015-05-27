/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * Time: 8:43:17 PM
 */

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedPaneContentUI;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class InspectionManagerEx extends InspectionManagerBase {
  private final NotNullLazyValue<ContentManager> myContentManager;
  private final Set<GlobalInspectionContextImpl> myRunningContexts = new HashSet<GlobalInspectionContextImpl>();
  private GlobalInspectionContextImpl myGlobalInspectionContext;

  public InspectionManagerEx(final Project project) {
    super(project);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myContentManager = new NotNullLazyValue<ContentManager>() {
        @NotNull
        @Override
        protected ContentManager compute() {
          ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
          toolWindowManager.registerToolWindow(ToolWindowId.INSPECTION, true, ToolWindowAnchor.BOTTOM, project);
          return ContentFactory.SERVICE.getInstance().createContentManager(new TabbedPaneContentUI(), true, project);
        }
      };
    }
    else {
      myContentManager = new NotNullLazyValue<ContentManager>() {
        @NotNull
        @Override
        protected ContentManager compute() {
          ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
          ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.INSPECTION, true, ToolWindowAnchor.BOTTOM, project);
          ContentManager contentManager = toolWindow.getContentManager();
          toolWindow.setIcon(AllIcons.Toolwindows.ToolWindowInspection);
          new ContentManagerWatcher(toolWindow, contentManager);
          return contentManager;
        }
      };
    }
  }

  @Nullable
  public static SuppressIntentionAction[] getSuppressActions(@NotNull InspectionToolWrapper toolWrapper) {
    final InspectionProfileEntry tool = toolWrapper.getTool();
    if (tool instanceof CustomSuppressableInspectionTool) {
      return ((CustomSuppressableInspectionTool)tool).getSuppressActions(null);
    }
    final List<LocalQuickFix> actions = new ArrayList<LocalQuickFix>(Arrays.asList(tool.getBatchSuppressActions(null)));
    if (actions.isEmpty()) {
      final Language language = Language.findLanguageByID(toolWrapper.getLanguage());
      if (language != null) {
        final List<InspectionSuppressor> suppressors = LanguageInspectionSuppressors.INSTANCE.allForLanguage(language);
        for (InspectionSuppressor suppressor : suppressors) {
          final SuppressQuickFix[] suppressActions = suppressor.getSuppressActions(null, toolWrapper.getID());
          Collections.addAll(actions, suppressActions);
        }
      }
    }
    return ContainerUtil.map2Array(actions, SuppressIntentionAction.class, new Function<LocalQuickFix, SuppressIntentionAction>() {
      @Override
      public SuppressIntentionAction fun(final LocalQuickFix fix) {
        return SuppressIntentionActionFromFix.convertBatchToSuppressIntentionAction((SuppressQuickFix)fix);
      }
    });
  }


  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                   @NotNull final String descriptionTemplate,
                                                   @NotNull final ProblemHighlightType highlightType,
                                                   @Nullable final HintAction hintAction,
                                                   boolean onTheFly,
                                                   @Nullable LocalQuickFix... fixes) {
    return new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, null, hintAction, onTheFly);
  }

  @Override
  @NotNull
  public GlobalInspectionContextImpl createNewGlobalContext(boolean reuse) {
    final GlobalInspectionContextImpl inspectionContext;
    if (reuse) {
      if (myGlobalInspectionContext == null) {
        myGlobalInspectionContext = inspectionContext = new GlobalInspectionContextImpl(getProject(), myContentManager);
      }
      else {
        inspectionContext = myGlobalInspectionContext;
      }
    }
    else {
      inspectionContext = new GlobalInspectionContextImpl(getProject(), myContentManager);
    }
    myRunningContexts.add(inspectionContext);
    return inspectionContext;
  }

  public void setProfile(@NotNull String name) {
    myCurrentProfileName = name;
  }

  void closeRunningContext(@NotNull GlobalInspectionContextImpl globalInspectionContext){
    myRunningContexts.remove(globalInspectionContext);
  }

  @NotNull
  public Set<GlobalInspectionContextImpl> getRunningContexts() {
    return myRunningContexts;
  }

  @TestOnly
  @NotNull
  public NotNullLazyValue<ContentManager> getContentManager() {
    return myContentManager;
  }
}
