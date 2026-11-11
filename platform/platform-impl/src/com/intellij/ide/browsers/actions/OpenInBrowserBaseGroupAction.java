// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers.actions;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.EditorTabWebBrowser;
import com.intellij.ide.browsers.EditorTabWebBrowserProvider;
import com.intellij.ide.browsers.KnownBrowser;
import com.intellij.ide.browsers.SystemDefaultBrowser;
import com.intellij.ide.browsers.SystemDefaultBrowserDetector;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.ide.browsers.WebBrowserXmlService;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public abstract class OpenInBrowserBaseGroupAction extends ActionGroup implements DumbAware {
  private static final String OPEN_HTML_IN_EMBEDDED_BROWSER_ACTION_ID = "OpenHtmlInEmbeddedBrowser";

  private final ConcurrentMap<WebBrowser, AnAction> myBrowserActions =
    ConcurrentCollectionFactory.createConcurrentWeakKeyWeakValueMap(HashingStrategy.canonical());
  private final AnAction mySystemDefaultBrowserAction = new OpenInSystemDefaultBrowserAction();

  protected OpenInBrowserBaseGroupAction(boolean popup) {
    Presentation p = getTemplatePresentation();
    p.setPopupGroup(popup);
    p.setHideGroupIfEmpty(true);
    p.setText(IdeBundle.messagePointer("open.in.browser"));
    p.setDescription(IdeBundle.messagePointer("open.selected.file.in.browser"));
    p.setIconSupplier(() -> AllIcons.Nodes.PpWeb);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project != null && TrustedProjects.isProjectTrusted(project));
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    List<EditorTabWebBrowser> providedBrowsers = EditorTabWebBrowserProvider.getAvailableBrowsers();
    List<WebBrowser> browsers = WebBrowserManager.getInstance().getBrowsers();
    List<AnAction> actions = new ArrayList<>(providedBrowsers.size() + browsers.size() + 3);
    for (EditorTabWebBrowser browser : providedBrowsers) {
      actions.add(myBrowserActions.computeIfAbsent(browser, __ -> new OpenInProvidedBrowserAction(browser)));
    }
    AnAction localBrowserAction = providedBrowsers.isEmpty() ? getLocalBrowserAction() : null;
    if (localBrowserAction != null) {
      actions.add(localBrowserAction);
    }
    // the browsers in the IDE come before the external browsers; the action system hides a separator at the edge
    actions.add(Separator.getInstance());
    KnownBrowser systemDefaultBrowser = null;
    if (usesSystemDefaultBrowser()) {
      actions.add(mySystemDefaultBrowserAction);
      systemDefaultBrowser = SystemDefaultBrowserDetector.getInstance().getKnownBrowser();
    }
    for (WebBrowser browser : browsers) {
      if (systemDefaultBrowser != null && SystemDefaultBrowserDetector.opensSameAs(browser, systemDefaultBrowser)) {
        continue;
      }
      actions.add(myBrowserActions.computeIfAbsent(browser, BaseOpenInBrowserAction::new));
    }
    return actions.toArray(EMPTY_ARRAY);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public static boolean hasLocalBrowser() {
    return getLocalBrowserAction() != null;
  }

  /**
   * Returns {@code true} when the group has an action to show.
   * Such an action opens an {@link EditorTabWebBrowser}, the local browser, the system default browser, or an active browser.
   */
  public static boolean hasBrowserActions() {
    return !EditorTabWebBrowserProvider.getAvailableBrowsers().isEmpty() ||
           hasLocalBrowser() ||
           usesSystemDefaultBrowser() ||
           !WebBrowserManager.getInstance().getActiveBrowsers().isEmpty();
  }

  /**
   * Returns {@code true} when the default browser of the IDE is {@link SystemDefaultBrowser}.
   * That browser is not in the browser list, so the group adds an action for it.
   * The group then hides a browser of the list that opens a page in the same way, see {@link SystemDefaultBrowserDetector#opensSameAs}.
   * For any other default browser, the group already has the action of that browser.
   */
  private static boolean usesSystemDefaultBrowser() {
    return OpenFileInDefaultBrowserActionKt.findUsingBrowser() == null;
  }

  private static @Nullable AnAction getLocalBrowserAction() {
    return Registry.is("ide.web.preview.enabled", true)
           ? ActionManager.getInstance().getAction(OPEN_HTML_IN_EMBEDDED_BROWSER_ACTION_ID)
           : null;
  }

  @ApiStatus.Internal
  public static final class OpenInBrowserGroupAction extends OpenInBrowserBaseGroupAction {
    public OpenInBrowserGroupAction() {
      super(true);
    }
  }

  @ApiStatus.Internal
  public static class OpenInBrowserEditorContextBarGroupAction extends OpenInBrowserBaseGroupAction {
    public OpenInBrowserEditorContextBarGroupAction() {
      super(false);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      final WebBrowserManager browserManager = WebBrowserManager.getInstance();
      PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
      Language language = psiFile != null ? psiFile.getViewProvider().getBaseLanguage() : null;
      if (language == null || language == Language.ANY || language == PlainTextLanguage.INSTANCE) {
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (virtualFile != null && virtualFile.getFileType() instanceof LanguageFileType fileType) {
          language = fileType.getLanguage();
        }
      }

      boolean needShowOnHover = language != null && WebBrowserXmlService.getInstance().isXmlLanguage(language)
              ? browserManager.isShowBrowserHoverXml()
              : browserManager.isShowBrowserHover();
      boolean enabled = needShowOnHover &&
                        hasBrowserActions()
                        && editor != null && !DiffUtil.isDiffEditor(editor);
      e.getPresentation().setEnabledAndVisible(enabled);
    }
  }
}