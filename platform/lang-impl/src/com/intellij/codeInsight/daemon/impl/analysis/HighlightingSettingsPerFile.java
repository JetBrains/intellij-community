// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.lang.Language;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.editor.markup.InspectionsLevel;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

@State(name = "HighlightingSettingsPerFile", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class HighlightingSettingsPerFile extends HighlightingLevelManager implements PersistentStateComponent<Element> {
  @NonNls private static final String SETTING_TAG = "setting";
  @NonNls private static final String ROOT_ATT_PREFIX = "root";
  @NonNls private static final String FILE_ATT = "file";
  private final MessageBus myBus;
  private final Set<String> vcsIgnoreFileNames;

  private final Map<VirtualFile, FileHighlightingSetting[]> myHighlightSettings = new HashMap<>();

  public HighlightingSettingsPerFile(@NotNull Project project, @NotNull MessageBus bus) {
    myBus = bus;
    vcsIgnoreFileNames = VcsFacade.getInstance().getVcsIgnoreFileNames(project);
  }

  public static HighlightingSettingsPerFile getInstance(@NotNull Project project) {
    return (HighlightingSettingsPerFile)project.getService(HighlightingLevelManager.class);
  }

  private static int getRootIndex(@NotNull PsiFile file) {
    FileViewProvider provider = file.getViewProvider();
    Set<Language> languages = provider.getLanguages();
    if (languages.size() == 1) {
      return 0;
    }
    List<Language> array = new ArrayList<>(languages);
    array.sort(PsiUtilBase.LANGUAGE_COMPARATOR);
    for (int i = 0; i < array.size(); i++) {
      Language language = array.get(i);
      if (provider.getPsi(language) == file) return i;
    }
    throw new RuntimeException("Cannot find root for: " + file);
  }

  @NotNull
  public FileHighlightingSetting getHighlightingSettingForRoot(@NotNull PsiElement root) {
    PsiFile containingFile = root.getContainingFile();
    VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return FileHighlightingSetting.FORCE_HIGHLIGHTING;
    FileHighlightingSetting[] fileHighlightingSettings = myHighlightSettings.get(virtualFile);
    int index = getRootIndex(containingFile);
    if (fileHighlightingSettings != null && index < fileHighlightingSettings.length) {
      return fileHighlightingSettings[index];
    }
    return getDefaultHighlightingSetting(root.getProject(), virtualFile);
  }

  @NotNull
  private static FileHighlightingSetting getDefaultHighlightingSetting(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    DefaultHighlightingSettingProvider[] providers = DefaultHighlightingSettingProvider.EP_NAME.getExtensions();
    List<DefaultHighlightingSettingProvider> filtered = DumbService.getInstance(project).filterByDumbAwareness(providers);
    for (DefaultHighlightingSettingProvider p : filtered) {
      FileHighlightingSetting setting = p.getDefaultSetting(project, virtualFile);
      if (setting != null) {
        return setting;
      }
    }
    return FileHighlightingSetting.FORCE_HIGHLIGHTING;
  }

  private static FileHighlightingSetting @NotNull [] getDefaults(@NotNull PsiFile file) {
    int rootsCount = file.getViewProvider().getLanguages().size();
    FileHighlightingSetting[] fileHighlightingSettings = new FileHighlightingSetting[rootsCount];
    Arrays.fill(fileHighlightingSettings, FileHighlightingSetting.FORCE_HIGHLIGHTING);
    return fileHighlightingSettings;
  }

  public void setHighlightingSettingForRoot(@NotNull PsiElement root, @NotNull FileHighlightingSetting setting) {
    PsiFile containingFile = root.getContainingFile();
    VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return;
    FileHighlightingSetting[] defaults = myHighlightSettings.get(virtualFile);
    int rootIndex = getRootIndex(containingFile);
    if (defaults != null && rootIndex >= defaults.length) {
      defaults = null;
    }
    if (defaults == null) {
      defaults = getDefaults(containingFile);
    }
    defaults[rootIndex] = setting;
    boolean toRemove = true;
    for (FileHighlightingSetting aDefault : defaults) {
      if (aDefault != FileHighlightingSetting.NONE) {
        toRemove = false;
        break;
      }
    }
    if (toRemove) {
      myHighlightSettings.remove(virtualFile);
    }
    else {
      myHighlightSettings.put(virtualFile, defaults);
    }

    myBus.syncPublisher(FileHighlightingSettingListener.SETTING_CHANGE).settingChanged(root, setting);

    InspectionWidgetUsageCollector.HIGHLIGHT_LEVEL_CHANGED.log(
      root.getProject(), root.getLanguage(), FileHighlightingSetting.toInspectionsLevel(setting));
  }

  @Override
  public void loadState(@NotNull Element element) {
    List<Element> children = element.getChildren(SETTING_TAG);
    for (Element child : children) {
      String url = child.getAttributeValue(FILE_ATT);
      if (url == null) continue;
      VirtualFile fileByUrl = VirtualFileManager.getInstance().findFileByUrl(url);
      if (fileByUrl != null) {
        List<FileHighlightingSetting> settings = new ArrayList<>();
        int index = 0;
        while (child.getAttributeValue(ROOT_ATT_PREFIX + index) != null) {
          String attributeValue = child.getAttributeValue(ROOT_ATT_PREFIX + index++);
          settings.add(Enum.valueOf(FileHighlightingSetting.class, attributeValue));
        }
        myHighlightSettings.put(fileByUrl, settings.toArray(new FileHighlightingSetting[0]));
      }
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    List<Map.Entry<VirtualFile, FileHighlightingSetting[]>> entries = new ArrayList<>(myHighlightSettings.entrySet());
    entries.sort(Comparator.comparing(entry -> entry.getKey().getPath()));
    for (Map.Entry<VirtualFile, FileHighlightingSetting[]> entry : entries) {
      Element child = new Element(SETTING_TAG);

      VirtualFile vFile = entry.getKey();
      if (!vFile.isValid()) continue;
      child.setAttribute(FILE_ATT, vFile.getUrl());
      for (int i = 0; i < entry.getValue().length; i++) {
        FileHighlightingSetting fileHighlightingSetting = entry.getValue()[i];
        child.setAttribute(ROOT_ATT_PREFIX + i, fileHighlightingSetting.toString());
      }
      element.addContent(child);
    }
    return element;
  }

  @Override
  public boolean shouldHighlight(@NotNull PsiElement psiRoot) {
    FileHighlightingSetting settingForRoot = getHighlightingSettingForRoot(psiRoot);
    return settingForRoot != FileHighlightingSetting.SKIP_HIGHLIGHTING;
  }

  @Override
  public boolean shouldInspect(@NotNull PsiElement psiRoot) {
    FileHighlightingSetting settingForRoot = getHighlightingSettingForRoot(psiRoot);
    if (settingForRoot == FileHighlightingSetting.SKIP_HIGHLIGHTING ||
        settingForRoot == FileHighlightingSetting.SKIP_INSPECTION) {
      return false;
    }
    Project project = psiRoot.getProject();
    VirtualFile virtualFile = psiRoot.getContainingFile().getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return false;

    if (ProjectUtil.isProjectOrWorkspaceFile(virtualFile) && !vcsIgnoreFileNames.contains(virtualFile.getName())) {
      return false;
    }

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (ProjectScope.getLibrariesScope(project).contains(virtualFile) && !fileIndex.isInContent(virtualFile)) return false;

    return !SingleRootFileViewProvider.isTooLargeForIntelligence(virtualFile);
  }

  @Override
  public boolean runEssentialHighlightingOnly(@NotNull PsiElement psiRoot) {
    return getHighlightingSettingForRoot(psiRoot) == FileHighlightingSetting.ESSENTIAL
           && !((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(psiRoot.getProject())).isRestartToCompleteEssentialHighlightingRequested();
  }

  public int countRoots(@NotNull FileHighlightingSetting setting) {
    return myHighlightSettings.values()
      .stream()
      .flatMap(array -> Stream.of(array))
      .mapToInt(s -> s == setting ? 1 : 0)
      .sum();
  }

  private static final class InspectionWidgetUsageCollector extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("inspection.widget",3);
    private static final EventId2<Language, InspectionsLevel> HIGHLIGHT_LEVEL_CHANGED =
      GROUP.registerEvent("highlight.level.changed", EventFields.Language, EventFields.Enum("level", InspectionsLevel.class));

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }
  }
}
