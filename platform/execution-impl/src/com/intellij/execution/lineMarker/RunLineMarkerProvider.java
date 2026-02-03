// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info;
import com.intellij.icons.AllIcons;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColorUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.JBUI;
import com.intellij.xml.CommonXmlStrings;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class RunLineMarkerProvider extends LineMarkerProviderDescriptor implements DumbAware {
  private static final Comparator<Info> COMPARATOR = (a, b) -> {
    if (b.shouldReplace(a)) {
      return 1;
    }
    if (a.shouldReplace(b)) {
      return -1;
    }
    return 0;
  };

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(element.getProject());
    if (injectedLanguageManager.isInjectedFragment(element.getContainingFile())) return null;

    List<RunLineMarkerContributor> contributors =
      DumbService.getInstance(element.getProject()).filterByDumbAwareness(RunLineMarkerContributor.EXTENSION.allForLanguageOrAny(element.getLanguage()));
    Icon icon = null;
    List<Info> infos = null;
    for (RunLineMarkerContributor contributor : contributors) {
      ProgressManager.checkCanceled();
      Info info = contributor.getInfo(element);
      if (info == null) {
        continue;
      }
      if (icon == null) {
        icon = info.icon;
      }

      if (infos == null) {
        infos = new SmartList<>();
      }
      infos.add(info);
    }
    if (icon == null) return null;

    return createLineMarker(element, icon, infos);
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                     @NotNull Collection<? super LineMarkerInfo<?>> result) {
    for (PsiElement element : elements) {
      List<RunLineMarkerContributor> contributors = DumbService.getInstance(element.getProject())
        .filterByDumbAwareness(RunLineMarkerContributor.EXTENSION.allForLanguageOrAny(element.getLanguage()));
      Icon icon = null;
      List<Info> infos = null;
      for (RunLineMarkerContributor contributor : contributors) {
        Info info = contributor.getSlowInfo(element);
        if (info == null) {
          continue;
        }
        if (icon == null) {
          icon = info.icon;
        }

        if (infos == null) {
          infos = new SmartList<>();
        }
        infos.add(info);
      }
      if (icon != null) {
         result.add(createLineMarker(element, icon, infos));
      }
    }

  }

  public static @NotNull LineMarkerInfo<PsiElement> createLineMarker(@NotNull PsiElement element,
                                                                     @NotNull Icon icon,
                                                                     @NotNull List<? extends Info> infos) {
    if (infos.size() > 1) {
      infos = new ArrayList<>(infos);
      infos.sort(COMPARATOR);
      final Info first = infos.get(0);
      infos.removeIf(info -> info != first && first.shouldReplace(info));
    }

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (Info info : infos) {
      for (AnAction action : info.actions) {
        actionGroup.add(action instanceof Separator ? action : new LineMarkerActionWrapper(element, action));
      }

      if (info != infos.get(infos.size() - 1)) {
        actionGroup.add(new Separator());
      }
    }

    Function<PsiElement, String> tooltipProvider = new EquatableTooltipProvider(infos);
    return new RunLineMarkerInfo(element, icon, tooltipProvider, actionGroup);
  }

  // must provide sensible equals() to be able to reuse LineMarker on change
  private static final class EquatableTooltipProvider implements Function<PsiElement, String> {
    private final @NotNull List<? extends Info> myInfos;

    EquatableTooltipProvider(@NotNull List<? extends Info> infos) { myInfos = infos; }

    @Override
    public int hashCode() {
      return myInfos.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof EquatableTooltipProvider other && myInfos.equals(other.myInfos);
    }

    @Override
    public String fun(PsiElement element1) {
      StringBuilder tooltip = new StringBuilder();
      for (Info info : myInfos) {
        if (info.tooltipProvider != null) {
          String string = info.tooltipProvider.apply(element1);
          if (string == null) continue;
          if (!tooltip.isEmpty()) {
            tooltip.append("\n");
          }
          tooltip.append(string);
        }
      }
      return tooltip.isEmpty() ? null : appendShortcut(tooltip.toString());
    }
  }


  private static String appendShortcut(String tooltip) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return tooltip;
    Executor executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
    if (executor != null) {
      String actionId = executor.getContextActionId();
      String shortcutText = KeymapUtil.getShortcutText(actionId);
      @NotNull String shortcutColor = ColorUtil.toHex(JBUI.CurrentTheme.Tooltip.shortcutForeground());
      return XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(tooltip).replaceAll("\n", "<br>") + CommonXmlStrings.NBSP + CommonXmlStrings.NBSP + "<font color='#" + shortcutColor + "'>" + XmlStringUtil.escapeString(shortcutText) + "</font>");
    }
    else {
      return tooltip;
    }
  }

  @ApiStatus.Internal
  public static final class RunLineMarkerInfo extends MergeableLineMarkerInfo<PsiElement> {
    private final DefaultActionGroup myActionGroup;

    RunLineMarkerInfo(PsiElement element, Icon icon, Function<? super PsiElement, @Nls String> tooltipProvider, DefaultActionGroup actionGroup) {
      super(element, element.getTextRange(), icon, tooltipProvider, null, GutterIconRenderer.Alignment.CENTER,
            () -> tooltipProvider.fun(element));
      myActionGroup = actionGroup;
    }

    @Override
    public GutterIconRenderer createGutterRenderer() {
      return new LineMarkerGutterIconRenderer<>(this) {
        @Override
        public AnAction getClickAction() {
          return myActionGroup.getChildrenCount() == 1 ? myActionGroup.getChildActionsOrStubs()[0] : null;
        }

        @Override
        public boolean isNavigateAction() {
          return true;
        }

        @Override
        public ActionGroup getPopupMenuActions() {
          return myActionGroup;
        }

        @Override
        public boolean isDumbAware() {
          return myActionGroup.isDumbAware();
        }
      };
    }

    @Override
    public @NotNull MarkupEditorFilter getEditorFilter() {
      return MarkupEditorFilterFactory.createIsNotDiffFilter();
    }

    @Override
    public boolean canMergeWith(@NotNull MergeableLineMarkerInfo<?> info) {
      return info instanceof RunLineMarkerInfo && info.getIcon() == getIcon();
    }

    @Override
    public Icon getCommonIcon(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
      return getIcon();
    }
  }

  @Override
  public @NotNull String getName() {
    return ExecutionBundle.message("run.line.marker.name");
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.RunConfigurations.TestState.Run;
  }

  private static final Key<Boolean> HAS_ANYTHING_RUNNABLE = Key.create("HAS_ANYTHING_RUNNABLE");

  public static @NotNull ThreeState hadAnythingRunnable(@NotNull VirtualFile file) {
    Boolean data = file.getUserData(HAS_ANYTHING_RUNNABLE);
    return data == null ? ThreeState.UNSURE : ThreeState.fromBoolean(data);
  }

  public static void markRunnable(@NotNull VirtualFile file, boolean isRunnable) {
    file.putUserData(HAS_ANYTHING_RUNNABLE, isRunnable);
  }

}