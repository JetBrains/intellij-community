// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchFieldStatisticsCollector;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.FontUtil;
import com.intellij.util.JavaCoroutines;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Konstantin Bulenkov
 */
public class SearchEverywhereAction extends SearchEverywhereBaseAction
  implements CustomComponentAction, RightAlignedToolbarAction, DumbAware {
  private static final Logger LOG = Logger.getInstance(SearchEverywhereAction.class);

  public static final Key<ConcurrentHashMap<ClientId, JBPopup>> SEARCH_EVERYWHERE_POPUP = new Key<>("SearchEverywherePopup");

  static final class ShortcutTracker implements ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
    @Override
    public @Nullable Object customize(@NotNull ActionRuntimeRegistrar actionRegistrar,
                                      @NotNull Continuation<? super Unit> $completion) {
      return JavaCoroutines.suspendJava(jc -> {
        ModifierKeyDoubleClickHandler.getInstance().registerAction(IdeActions.ACTION_SEARCH_EVERYWHERE, KeyEvent.VK_SHIFT, -1, false);
        jc.resume(Unit.INSTANCE);
      }, $completion);
    }
  }

  public SearchEverywhereAction() {
    setEnabledInModalContext(false);
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return new ActionButton(this, presentation, place, () -> getMinimumSize(place)) {
      @Override protected void updateToolTipText() {
        String shortcutText = getShortcut();

        String classesTabName = String.join("/",GotoClassPresentationUpdater.getActionTitlePluralized());
        if (UISettings.isIdeHelpTooltipEnabled()) {
          HelpTooltip.dispose(this);

          new HelpTooltip()
            .setTitle(myPresentation.getText())
            .setShortcut(shortcutText)
            .setDescription(IdeBundle.message("search.everywhere.action.tooltip.description.text", classesTabName))
            .installOn(this);
        }
        else {
          setToolTipText(IdeBundle.message("search.everywhere.action.tooltip.text", shortcutText, classesTabName));
        }
      }
    };
  }

  private static boolean isExperimentalToolbar(@NotNull String place) {
    return ExperimentalUI.isNewUI() && ActionPlaces.MAIN_TOOLBAR.equals(place);
  }

  private static @NotNull Dimension getMinimumSize(@NotNull String place) {
    return isExperimentalToolbar(place) ? ActionToolbar.experimentalToolbarMinimumButtonSize()
                                        : ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;
  }

  protected static String getShortcut() {
    Shortcut[] shortcuts = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE).getShortcuts();
    if (shortcuts.length == 0) {
      return "Double" + (SystemInfo.isMac ? FontUtil.thinSpace() + MacKeymapUtil.SHIFT : " Shift"); //NON-NLS
    }
    return KeymapUtil.getShortcutsText(shortcuts);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    AnActionEvent newEvent = SearchFieldStatisticsCollector.wrapEventWithActionStartData(e);
    if (LightEdit.owns(newEvent.getProject())) return;

    if (AdvancedSettings.getBoolean("ide.suppress.double.click.handler") && newEvent.getInputEvent() instanceof KeyEvent) {
      if (((KeyEvent)newEvent.getInputEvent()).getKeyCode() == KeyEvent.VK_SHIFT) {
        return;
      }
    }

    ReadAction.run(() -> showInSearchEverywherePopup(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID, newEvent, true, true));
  }
}

