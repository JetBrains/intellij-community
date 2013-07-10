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
 * Time: 8:43:17 PM
 */

package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.impl.actions.AbstractBatchSuppressByNoInspectionCommentFix;
import com.intellij.codeInspection.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.profile.codeInspection.ui.InspectionToolsConfigurable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedPaneContentUI;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class InspectionManagerEx extends InspectionManagerBase {
  private GlobalInspectionContextImpl myGlobalInspectionContext = null;
  private final NotNullLazyValue<ContentManager> myContentManager;
  private final Set<GlobalInspectionContextImpl> myRunningContexts = new HashSet<GlobalInspectionContextImpl>();

  public InspectionManagerEx(final Project project) {
    super(project);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myContentManager = new NotNullLazyValue<ContentManager>() {
        @NotNull
        @Override
        protected ContentManager compute() {
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
          ToolWindow toolWindow =
            toolWindowManager.registerToolWindow(ToolWindowId.INSPECTION, true, ToolWindowAnchor.BOTTOM, project);
          ContentManager contentManager = toolWindow.getContentManager();
          toolWindow.setIcon(AllIcons.Toolwindows.ToolWindowInspection);
          new ContentManagerWatcher(toolWindow, contentManager);
          return contentManager;
        }
      };
    }
  }

  @NotNull
  public static SuppressIntentionAction convertBatchToSuppressIntentionAction(@NotNull final SuppressQuickFix fix) {
    return new SuppressIntentionAction() {
      @Override
      public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        PsiElement container = fix instanceof AbstractBatchSuppressByNoInspectionCommentFix
                               ? ((AbstractBatchSuppressByNoInspectionCommentFix )fix).getContainer(element) : null;
        boolean caretWasBeforeStatement = editor != null && container != null && editor.getCaretModel().getOffset() == container.getTextRange().getStartOffset();
        try {
          ProblemDescriptor descriptor =
            new ProblemDescriptorImpl(element, element, "", null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false, null, false);
          fix.applyFix(project, descriptor);
        }
        catch (IncorrectOperationException e) {
          if (!ApplicationManager.getApplication().isUnitTestMode() && editor != null) {
            Messages.showErrorDialog(editor.getComponent(),
                                     InspectionsBundle.message("suppress.inspection.annotation.syntax.error", e.getMessage()));
          }
          else {
            throw e;
          }
        }

        if (caretWasBeforeStatement) {
          editor.getCaretModel().moveToOffset(container.getTextRange().getStartOffset());
        }
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        return fix.isAvailable(project, element);
      }

      @NotNull
      @Override
      public String getText() {
        return fix.getName();
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return fix.getFamilyName();
      }
    };
  }

  @Nullable
  public static SuppressIntentionAction[] getSuppressActions(@NotNull InspectionProfileEntry tool) {
    if (tool instanceof CustomSuppressableInspectionTool) {
      return ((CustomSuppressableInspectionTool)tool).getSuppressActions(null);
    }
    if (tool instanceof BatchSuppressableTool) {
      LocalQuickFix[] actions = ((BatchSuppressableTool)tool).getBatchSuppressActions(null);
      return ContainerUtil.map2Array(actions, SuppressIntentionAction.class, new Function<LocalQuickFix, SuppressIntentionAction>() {
        @Override
        public SuppressIntentionAction fun(final LocalQuickFix fix) {
          return convertBatchToSuppressIntentionAction((SuppressQuickFix)fix);
        }
      });
    }
    return null;
  }


  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                   @NotNull final String descriptionTemplate,
                                                   @NotNull final ProblemHighlightType highlightType,
                                                   @Nullable final HintAction hintAction,
                                                   boolean onTheFly,
                                                   final LocalQuickFix... fixes) {
    return new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, null, hintAction, onTheFly);
  }

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

  public void setProfile(final String name) {
    myCurrentProfileName = name;
  }

  public void closeRunningContext(GlobalInspectionContextImpl globalInspectionContext){
    myRunningContexts.remove(globalInspectionContext);
  }

  public Set<GlobalInspectionContextImpl> getRunningContexts() {
    return myRunningContexts;
  }

  public static boolean inspectionResultSuppressed(@NotNull PsiElement place, @NotNull LocalInspectionTool tool) {
    if (tool instanceof CustomSuppressableInspectionTool) {
      return ((CustomSuppressableInspectionTool)tool).isSuppressedFor(place);
    }
    if (tool instanceof BatchSuppressableTool) {
      return ((BatchSuppressableTool)tool).isSuppressedFor(place);
    }
    String alternativeId;
    String id;

    return SuppressionUtil.isSuppressed(place, id = tool.getID()) ||
           (alternativeId = tool.getAlternativeID()) != null &&
           !alternativeId.equals(id) &&
           SuppressionUtil.isSuppressed(place, alternativeId);
  }

  @NotNull
  @Deprecated
  public ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                   @NotNull final String descriptionTemplate,
                                                   @NotNull final ProblemHighlightType highlightType,
                                                   @Nullable final HintAction hintAction,
                                                   final LocalQuickFix... fixes) {

    return new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, null, hintAction, true);
  }

  @TestOnly
  public NotNullLazyValue<ContentManager> getContentManager() {
    return myContentManager;
  }

  private final AtomicBoolean myToolsAreInitialized = new AtomicBoolean(false);
  private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]*>");
  public void buildInspectionSearchIndexIfNecessary() {
    if (!myToolsAreInitialized.getAndSet(true)) {
      final SearchableOptionsRegistrar myOptionsRegistrar = SearchableOptionsRegistrar.getInstance();
      final InspectionToolRegistrar toolRegistrar = InspectionToolRegistrar.getInstance();
      final Application app = ApplicationManager.getApplication();
      if (app.isUnitTestMode() || app.isHeadlessEnvironment()) return;

      app.executeOnPooledThread(new Runnable(){
        @Override
        public void run() {
          List<InspectionToolWrapper> tools = toolRegistrar.createTools();
          for (InspectionToolWrapper toolWrapper : tools) {
            processText(toolWrapper.getDisplayName().toLowerCase(), toolWrapper, myOptionsRegistrar);

            final String description = toolWrapper.loadDescription();
            if (description != null) {
              @NonNls String descriptionText = HTML_PATTERN.matcher(description).replaceAll(" ");
              processText(descriptionText, toolWrapper, myOptionsRegistrar);
            }
          }
        }
      });
    }
  }

  private static void processText(@NotNull @NonNls String descriptionText,
                                  @NotNull InspectionToolWrapper tool,
                                  @NotNull SearchableOptionsRegistrar myOptionsRegistrar) {
    if (ApplicationManager.getApplication().isDisposed()) return;
    final Set<String> words = myOptionsRegistrar.getProcessedWordsWithoutStemming(descriptionText);
    for (String word : words) {
      myOptionsRegistrar.addOption(word, tool.getShortName(), tool.getDisplayName(), InspectionToolsConfigurable.ID, InspectionToolsConfigurable.DISPLAY_NAME);
    }
  }
}
