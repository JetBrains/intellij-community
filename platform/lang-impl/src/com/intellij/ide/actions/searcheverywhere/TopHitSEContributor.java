// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.actions.ActivateToolWindowAction;
import com.intellij.ide.actions.GotoActionAction;
import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.Changeable;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.OnOffButton;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class TopHitSEContributor implements SearchEverywhereContributor {

  private final Collection<SearchTopHitProvider> myTopHitProviders = Arrays.asList(SearchTopHitProvider.EP_NAME.getExtensions());
  private final Consumer<String> searchStringSetter;

  public TopHitSEContributor(Consumer<String> setter) {
    searchStringSetter = setter;
  }

  @NotNull
  @Override
  public String getSearchProviderId() {
    return TopHitSEContributor.class.getSimpleName();
  }

  @NotNull
  @Override
  public String getGroupName() {
    return "Top Hit";
  }

  @Override
  public String includeNonProjectItemsText() {
    return null;
  }

  @Override
  public int getSortWeight() {
    return 50;
  }

  @Override
  public boolean showInFindResults() {
    return false;
  }

  @Override
  public ContributorSearchResult<Object> search(Project project,
                                                String pattern,
                                                boolean everywhere,
                                                ProgressIndicator progressIndicator,
                                                int elementsLimit) {
    Collection<Object> res = new LinkedHashSet<>();
    final Function<Object, Boolean> consumer = o -> {
      if (elementsLimit < 0 || res.size() < elementsLimit) {
        res.add(o);
        return true;
      }
      else {
        return false;
      }
    };

    boolean interrupted = fill(project, pattern, consumer);
    return new ContributorSearchResult<>(new ArrayList<>(res), interrupted);
  }

  @NotNull
  @Override
  public DataContext getDataContextForItem(Object element) {
    return DataContext.EMPTY_CONTEXT;
  }

  private boolean fill(Project project, String pattern, Function<Object, Boolean> consumer) {
    if (pattern.startsWith("#") && !pattern.contains(" ")) {
      return fillOptionProviders(pattern, consumer);
    } else {
      if (fillActions(project, pattern, consumer)) {
        return true;
      }
      return fillFromExtensions(project, pattern, consumer);
    }
  }

  private boolean fillFromExtensions(Project project, String pattern, Function<Object, Boolean> consumer) {
    for (SearchTopHitProvider provider : myTopHitProviders) {
      if (provider instanceof OptionsTopHitProvider && !((OptionsTopHitProvider)provider).isEnabled(project)) {
        continue;
      }
      boolean[] interrupted = {false};
      provider.consumeTopHits(pattern, o -> interrupted[0] = consumer.apply(o), project);
      if (interrupted[0]) {
        return true;
      }
    }

    return false;
  }

  private boolean fillActions(Project project, String pattern, Function<Object, Boolean> consumer) {
    ActionManager actionManager = ActionManager.getInstance();
    List<String> actions = AbbreviationManager.getInstance().findActions(pattern);
    for (String actionId : actions) {
      AnAction action = actionManager.getAction(actionId);
      if (!isEnabled(project, action)) {
        continue;
      }

      if (!consumer.apply(action)) {
        return true;
      }
    }

    return false;
  }

  private boolean fillOptionProviders(String pattern, Function<Object, Boolean> consumer) {
    String id = pattern.substring(1);
    final HashSet<String> ids = new HashSet<>();
    for (SearchTopHitProvider provider : SearchTopHitProvider.EP_NAME.getExtensions()) {
      if (provider instanceof OptionsTopHitProvider) {
        final String providerId = ((OptionsTopHitProvider)provider).getId();
        if (!ids.contains(providerId) && StringUtil.startsWithIgnoreCase(providerId, id)) {
          if (!consumer.apply(provider)) {
            return true;
          }
          ids.add(providerId);
        }
      }
    }

    return false;
  }

  private boolean isEnabled(Project project, final AnAction action) {
    //todo actions from SeaEverywhereAction
    Presentation presentation = action.getTemplatePresentation();
    if (ActionUtil.isDumbMode(project) && !action.isDumbAware()) {
      return false;
    }

    return presentation.isEnabled() && presentation.isVisible() && !StringUtil.isEmpty(presentation.getText());
  }

  @Override
  public boolean processSelectedItem(Project project, Object selected, int modifiers) {
    if (selected instanceof BooleanOptionDescription) {
      final BooleanOptionDescription option = (BooleanOptionDescription) selected;
      option.setOptionState(!option.isOptionEnabled());
      return false;
    }

    if (selected instanceof OptionsTopHitProvider) {
      setSearchString("#" + ((OptionsTopHitProvider) selected).getId() + " ");
      return false;
    }

    if (isActionValue(selected) || isSetting(selected)) {
      Component component = getProjectCurrentEditor(project);
      GotoActionAction.openOptionOrPerformAction(selected, "", project, component);
      return true;
    }

    return false;
  }

  private Component getProjectCurrentEditor(Project project) {
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    return editor == null ? null : editor.getComponent();
  }

  @Override
  public ListCellRenderer getElementsRenderer(Project project) {
    return new TopHitRenderer(project);
  }

  private void setSearchString(String str) {
    searchStringSetter.accept(str);
  }

  private static class TopHitRenderer extends ColoredListCellRenderer<Object> {

    private final Project myProject;
    private final MyAccessiblePanel myRendererPanel = new MyAccessiblePanel();

    private TopHitRenderer(Project project) {
      myProject = project;
    }

    private static class MyAccessiblePanel extends JPanel {
      private Accessible myAccessible;
      public MyAccessiblePanel() {
        super(new BorderLayout());
        setOpaque(false);
      }
      void setAccessible(Accessible comp) {
        myAccessible = comp;
      }
      @Override
      public AccessibleContext getAccessibleContext() {
        return accessibleContext = (myAccessible != null ? myAccessible.getAccessibleContext() : super.getAccessibleContext());
      }
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean hasFocus) {
      Component cmp = super.getListCellRendererComponent(list, value, index, selected, hasFocus);

      if (value instanceof BooleanOptionDescription) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIUtil.getListBackground(selected));
        panel.add(cmp, BorderLayout.CENTER);
        final Component rightComponent;

        final OnOffButton button = new OnOffButton();
        button.setSelected(((BooleanOptionDescription)value).isOptionEnabled());
        rightComponent = button;

        panel.add(rightComponent, BorderLayout.EAST);
        cmp = panel;
      }

      Color bg = cmp.getBackground();
      if (bg == null) {
        cmp.setBackground(UIUtil.getListBackground(selected));
        bg = cmp.getBackground();
      }

      myRendererPanel.removeAll();

      JPanel wrapped = new JPanel(new BorderLayout());
      wrapped.setBackground(bg);
      wrapped.add(cmp, BorderLayout.CENTER);
      myRendererPanel.add(wrapped, BorderLayout.CENTER);
      if (cmp instanceof Accessible) {
        myRendererPanel.setAccessible((Accessible)cmp);
      }

      return myRendererPanel;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, final Object value, int index, final boolean selected, boolean hasFocus) {
      setPaintFocusBorder(false);
      setIcon(EmptyIcon.ICON_16);
      ApplicationManager.getApplication().runReadAction(() -> {
        if (isActionValue(value)) {
          final AnAction anAction = (AnAction)value;
          final Presentation templatePresentation = anAction.getTemplatePresentation();
          Icon icon = templatePresentation.getIcon();
          if (anAction instanceof ActivateToolWindowAction) {
            final String id = ((ActivateToolWindowAction)anAction).getToolWindowId();
            ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(id);
            if (toolWindow != null) {
              icon = toolWindow.getIcon();
            }
          }
          append(String.valueOf(templatePresentation.getText()));
          if (icon != null && icon.getIconWidth() <= 16 && icon.getIconHeight() <= 16) {
            setIcon(IconUtil.toSize(icon, 16, 16));
          }
        }
        else if (isSetting(value)) {
          String text = getSettingText((OptionDescription)value);
          SimpleTextAttributes attrs = SimpleTextAttributes.REGULAR_ATTRIBUTES;
          if (value instanceof Changeable && ((Changeable)value).hasChanged()) {
            if (selected) {
              attrs = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
            }
            else {
              SimpleTextAttributes base = SimpleTextAttributes.LINK_BOLD_ATTRIBUTES;
              attrs = base.derive(SimpleTextAttributes.STYLE_BOLD, base.getFgColor(), null, null);
            }
          }
          append(text, attrs);
        }
        else if (value instanceof OptionsTopHitProvider) {
          append("#" + ((OptionsTopHitProvider)value).getId());
        }
        else {
          ItemPresentation presentation = null;
          if (value instanceof ItemPresentation) {
            presentation = (ItemPresentation)value;
          }
          else if (value instanceof NavigationItem) {
            presentation = ((NavigationItem)value).getPresentation();
          }
          if (presentation != null) {
            final String text = presentation.getPresentableText();
            append(text == null ? value.toString() : text);
            Icon icon = presentation.getIcon(false);
            if (icon != null) setIcon(icon);
          }
        }
      });
    }
  }

  private static boolean isActionValue(Object o) {
    return o instanceof AnAction;
  }

  private static boolean isSetting(Object o) {
    return o instanceof OptionDescription;
  }

  private static String getSettingText(OptionDescription value) {
    String hit = value.getHit();
    if (hit == null) {
      hit = value.getOption();
    }
    hit = StringUtil.unescapeXml(hit);
    if (hit.length() > 60) {
      hit = hit.substring(0, 60) + "...";
    }
    hit = hit.replace("  ", " "); //avoid extra spaces from mnemonics and xml conversion
    String text = hit.trim();
    text = StringUtil.trimEnd(text, ":");
    return text;
  }
}
