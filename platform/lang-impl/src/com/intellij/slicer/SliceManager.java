// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.BundleBase;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.RegisterToolWindowTask;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.RefactoringDescriptionLocation;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
@State(name = "SliceManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class SliceManager implements PersistentStateComponent<SliceManager.StoredSettingsBean> {
  private final Project myProject;
  private ContentManager myBackContentManager;
  private ContentManager myForthContentManager;
  private final @NotNull StoredSettingsBean myStoredSettings = new StoredSettingsBean();
  private static final @NonNls String BACK_TOOLWINDOW_ID = "Analyze Dataflow to";
  private static final @NonNls String FORTH_TOOLWINDOW_ID = "Analyze Dataflow from";

  static final class StoredSettingsBean {
    boolean showDereferences = true; // to show in dataflow/from dialog
    AnalysisUIOptions analysisUIOptions = new AnalysisUIOptions();
  }

  public static SliceManager getInstance(@NotNull Project project) {
    return project.getService(SliceManager.class);
  }

  public SliceManager(@NotNull Project project) {
    myProject = project;
  }

  private ContentManager getContentManager(boolean dataFlowToThis) {
    if (dataFlowToThis) {
      if (myBackContentManager == null) {
        ToolWindow backToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(RegisterToolWindowTask.closable(
          BACK_TOOLWINDOW_ID, LangBundle.messagePointer("toolwindow.name.dataflow.to.here"), AllIcons.Toolwindows.ToolWindowAnalyzeDataflow, ToolWindowAnchor.BOTTOM));
        myBackContentManager = backToolWindow.getContentManager();
        ContentManagerWatcher.watchContentManager(backToolWindow, myBackContentManager);
      }
      return myBackContentManager;
    }

    if (myForthContentManager == null) {
      ToolWindow forthToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(RegisterToolWindowTask.closable(
        FORTH_TOOLWINDOW_ID, LangBundle.messagePointer("toolwindow.name.dataflow.from.here"), AllIcons.Toolwindows.ToolWindowAnalyzeDataflow, ToolWindowAnchor.BOTTOM));
      myForthContentManager = forthToolWindow.getContentManager();
      ContentManagerWatcher.watchContentManager(forthToolWindow, myForthContentManager);
    }
    return myForthContentManager;
  }

  public void slice(@NotNull PsiElement element, boolean dataFlowToThis, @NotNull SliceHandler handler) {
    @SuppressWarnings("UnresolvedPropertyKey") 
    String dialogTitle = getElementDescription(dataFlowToThis ? LangBundle.message("tab.title.analyze.dataflow.to.here")
                                                              : LangBundle.message("tab.title.analyze.dataflow.from"), element, null);

    dialogTitle = filterStyle(dialogTitle);
    SliceAnalysisParams params = handler.askForParams(element, myStoredSettings, StringUtil.unescapeXmlEntities(dialogTitle));
    if (params == null) return;

    createToolWindow(element, params);
  }

  @Contract(pure = true)
  private static String filterStyle(String dialogTitle) {
    return Pattern.compile("(<style>.*</style>)|<[^<>]*>", Pattern.DOTALL).matcher(dialogTitle).replaceAll("");
  }

  /**
   * Opens the dataflow analysis toolwindow starting from the given element
   *
   * @param element root element
   * @param params analysis parameters
   */
  public void createToolWindow(@NotNull PsiElement element, @NotNull SliceAnalysisParams params) {
    SliceRootNode rootNode = new SliceRootNode(myProject, new DuplicateMap(),
                                               LanguageSlicing.getProvider(element).createRootUsage(element, params));
    createToolWindow(params.dataFlowToThis, rootNode, false, getElementDescription(null, element, null));
  }

  public void createToolWindow(boolean dataFlowToThis, @NotNull SliceRootNode rootNode, boolean splitByLeafExpressions, @NotNull @NlsContexts.TabTitle String displayName) {
    final SliceToolwindowSettings sliceToolwindowSettings = SliceToolwindowSettings.getInstance(myProject);
    final ContentManager contentManager = getContentManager(dataFlowToThis);
    final Content[] myContent = new Content[1];
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(dataFlowToThis ? BACK_TOOLWINDOW_ID : FORTH_TOOLWINDOW_ID);
    final SlicePanel slicePanel = new SlicePanel(myProject, dataFlowToThis, rootNode, splitByLeafExpressions, toolWindow) {
      @Override
      protected void close() {
        super.close();
        contentManager.removeContent(myContent[0], true);
      }

      @Override
      public boolean isAutoScroll() {
        return sliceToolwindowSettings.isAutoScroll();
      }

      @Override
      public void setAutoScroll(boolean autoScroll) {
        sliceToolwindowSettings.setAutoScroll(autoScroll);
      }

      @Override
      public boolean isPreview() {
        return sliceToolwindowSettings.isPreview();
      }

      @Override
      public void setPreview(boolean preview) {
        sliceToolwindowSettings.setPreview(preview);
      }
    };

    myContent[0] = contentManager.getFactory().createContent(slicePanel, displayName, true);
    contentManager.addContent(myContent[0]);
    contentManager.setSelectedContent(myContent[0]);

    toolWindow.activate(null);
  }

  public static @NlsContexts.TabTitle String getElementDescription(@NlsContexts.TabTitle String prefix, PsiElement element, @NlsContexts.TabTitle String suffix) {
    SliceLanguageSupportProvider provider = LanguageSlicing.getProvider(element);
    if(provider != null){
      element = provider.getElementForDescription(element);
    }
    String desc = ElementDescriptionUtil.getElementDescription(element, RefactoringDescriptionLocation.WITHOUT_PARENT);
    String firstPartOfDescription = StringUtil.first(desc, 100, true);
    return "<html><body>" + 
           ((prefix == null ? firstPartOfDescription : BundleBase.format(prefix, firstPartOfDescription)) + (suffix == null ? "" : suffix)) +
           "</body></html>";
  }

  @Override
  public StoredSettingsBean getState() {
    return myStoredSettings;
  }

  @Override
  public void loadState(@NotNull StoredSettingsBean state) {
    myStoredSettings.analysisUIOptions.loadState(state.analysisUIOptions);
  }
}
