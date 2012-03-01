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

package com.intellij.ide.util.gotoByName;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.apache.oro.text.regex.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class GotoActionModel implements ChooseByNameModel, CustomMatcherModel, Comparator<Object> {
  private static final String SETTINGS_KEY = "$$$SETTINGS$$$";
  @Nullable private final Project myProject;
  private final Component myContextComponent;

  private final ActionManager myActionManager = ActionManager.getInstance();

  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;

  private String myPattern;

  private Pattern myCompiledPattern;
  private final PatternMatcher myMatcher = new Perl5Matcher();
  
  private Map<AnAction, String> myActionsMap = new HashMap<AnAction, String>();
  private final SearchableOptionsRegistrar myIndex;


  public GotoActionModel(@Nullable Project project, final Component component) {
    myProject = project;
    myContextComponent = component;
    final ActionGroup mainMenu = (ActionGroup)myActionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU);
    collectActions(myActionsMap, mainMenu, mainMenu.getTemplatePresentation().getText());
    myIndex = SearchableOptionsRegistrar.getInstance();
  }

  public String getPromptText() {
    return IdeBundle.message("prompt.gotoaction.enter.action");
  }

  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.other.included");
  }

  public char getCheckBoxMnemonic() {
    return 'd';
  }

  public String getNotInMessage() {
    return IdeBundle.message("label.no.menu.actions.found");
  }

  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.actions.found");
  }

  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = getPropertiesStorage();
    return Boolean.TRUE.toString().equals(propertiesComponent.getValue("GoToAction.toSaveAllIncluded")) &&
           propertiesComponent.isTrueValue("GoToAction.allIncluded");
  }

  private PropertiesComponent getPropertiesStorage() {
    return myProject != null ? PropertiesComponent.getInstance(myProject) : PropertiesComponent.getInstance();
  }

  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = getPropertiesStorage();
    if (Boolean.TRUE.toString().equals(propertiesComponent.getValue("GoToAction.toSaveAllIncluded"))) {
      propertiesComponent.setValue("GoToAction.allIncluded", Boolean.toString(state));
    }
  }

  public ListCellRenderer getListCellRenderer() {
    return new DefaultListCellRenderer() {

      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        final Color bg = isSelected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground();
        panel.setBackground(bg);


        if (value instanceof Map.Entry) {

          final Map.Entry actionWithParentGroup = (Map.Entry)value;

          final AnAction anAction = (AnAction)actionWithParentGroup.getKey();
          final Presentation templatePresentation = anAction.getTemplatePresentation();
          final Icon icon = templatePresentation.getIcon();

          final DataContext dataContext = DataManager.getInstance().getDataContext(myContextComponent);

          final AnActionEvent event = updateActionBeforeShow(anAction, dataContext);
          final Presentation presentation = event.getPresentation();

          final Color fg = defaultActionForeground(isSelected, presentation);

          final JLabel actionLabel = createActionLabel(anAction, templatePresentation.getText(), fg, bg, icon);
          panel.add(actionLabel, BorderLayout.WEST);

          final String groupName = (String)actionWithParentGroup.getValue();
          if (groupName != null) {
            final JLabel groupLabel = new JLabel(groupName);
            groupLabel.setBackground(bg);
            groupLabel.setForeground(fg);
            panel.add(groupLabel, BorderLayout.EAST);
          }
        } else if (value instanceof OptionDescription) {
          String hit = ((OptionDescription)value).getHit();
          if (hit == null) {
            hit = ((OptionDescription)value).getOption();
          }
          if (hit.length() > 30) {
            hit = hit.substring(0, 30) + "...";
          }
          final JBLabel label = new JBLabel(hit.trim());
          label.setIcon(EMPTY_ICON);
          panel.add(label, BorderLayout.WEST);
          panel.add(new JBLabel("Settings"), BorderLayout.EAST);
        } else if (value instanceof String) {
          final JBLabel label = new JBLabel((String)value);
          label.setIcon(EMPTY_ICON);
          panel.add(label, BorderLayout.WEST);
        }
        return panel;
      }
    };
  }

  protected String getActionId(final @NotNull AnAction anAction) {
    return myActionManager.getId(anAction);
  }

  protected JLabel createActionLabel(final AnAction anAction, final String anActionName,
                                     final Color fg, final Color bg,
                                     final Icon icon) {
    final LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    if (icon != null && icon.getIconWidth() <= EMPTY_ICON.getIconWidth() && icon.getIconHeight() <= EMPTY_ICON.getIconHeight()) {
      layeredIcon.setIcon(icon, 1, (- icon.getIconWidth() + EMPTY_ICON.getIconWidth())/2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight())/2);
    }

    final Shortcut[] shortcutSet = KeymapManager.getInstance().getActiveKeymap().getShortcuts(getActionId(anAction));
    final String actionName = anActionName + (shortcutSet != null && shortcutSet.length > 0
                                                                        ? " (" + KeymapUtil.getShortcutText(shortcutSet[0]) + ")"
                                                                        : "");
    final JLabel actionLabel = new JLabel(actionName, layeredIcon, SwingConstants.LEFT);
    actionLabel.setBackground(bg);
    actionLabel.setForeground(fg);
    return actionLabel;
  }

  @Override
  public int compare(Object o1, Object o2) {
    if (o1 instanceof Map.Entry && !(o2 instanceof Map.Entry)) {
      return -1;
    }
    if (o2 instanceof Map.Entry && !(o1 instanceof Map.Entry)) {
      return 1;
    }

    return StringUtil.compare(getFullName(o1), getFullName(o2), true);
  }

  protected static AnActionEvent updateActionBeforeShow(AnAction anAction, DataContext dataContext) {
    final AnActionEvent event = new AnActionEvent(null, dataContext,
                                                  ActionPlaces.UNKNOWN, new Presentation(), ActionManager.getInstance(),
                                                  0);
    ActionUtil.performDumbAwareUpdate(anAction, event, false);
    ActionUtil.performDumbAwareUpdate(anAction, event, true);
    return event;
  }

  protected static Color defaultActionForeground(boolean isSelected, Presentation presentation) {
    if (!presentation.isEnabled() || !presentation.isVisible()) {
      return UIUtil.getInactiveTextColor();
    }

    return isSelected ? UIUtil.getListSelectionForeground() : UIUtil.getListForeground();
  }

  public String[] getNames(boolean checkBoxState) {
    final ArrayList<String> result = new ArrayList<String>();
    for (AnAction action : myActionsMap.keySet()) {
      if (action instanceof ActionGroup) continue;
      result.add(getActionId(action));
    }
    if (checkBoxState) {
      final Set<String> ids = ((ActionManagerImpl)myActionManager).getActionIds();
      for (String id : ids) {
        final AnAction anAction = myActionManager.getAction(id);
        if (!(anAction instanceof ActionGroup)) {
          result.add(id);
        }
      }
    }
    result.add(SETTINGS_KEY);
    return ArrayUtil.toStringArray(result);
  }

  public Object[] getElementsByName(final String id, final boolean checkBoxState, final String pattern) {
    final HashMap<AnAction, String> map = new HashMap<AnAction, String>();
    final AnAction act = myActionManager.getAction(id);
    if (act != null) {
      map.put(act, myActionsMap.get(act));
      if (checkBoxState) {
        final Set<String> ids = ((ActionManagerImpl)myActionManager).getActionIds();
        for (AnAction action : map.keySet()) { //do not add already included actions
          ids.remove(getActionId(action));
        }
        if (ids.contains(id)) {
          final AnAction anAction = myActionManager.getAction(id);
          if (!(anAction instanceof ActionGroup)) {
            map.put(anAction, null);
          }
        }
      }
    }
    Object[] objects = map.entrySet().toArray(new Map.Entry[map.size()]);
    if (Comparing.strEqual(id, SETTINGS_KEY)) {
      final Set<String> words = myIndex.getProcessedWords(pattern);
      Set<OptionDescription> optionDescriptions = null;
      for (String word : words) {
        final Set<OptionDescription> descriptions = ((SearchableOptionsRegistrarImpl)myIndex).getAcceptableDescriptions(word);
        if (descriptions != null) {
          for (Iterator<OptionDescription> iterator = descriptions.iterator(); iterator.hasNext(); ) {
            OptionDescription description = iterator.next();
            if (description.getConfigurableId().equals("preferences.keymap")) {
              iterator.remove();
            }
          }
          if (!descriptions.isEmpty()) {
            if (optionDescriptions == null) {
              optionDescriptions = descriptions;
            } else {
              optionDescriptions.retainAll(descriptions);
            }
          }
        }
      }
      if (optionDescriptions != null && !optionDescriptions.isEmpty()) {
        Set<String> currentHits = new HashSet<String>();
        for (Iterator<OptionDescription> iterator = optionDescriptions.iterator(); iterator.hasNext(); ) {
          OptionDescription description = iterator.next();
          final String hit = description.getHit();
          if (hit == null || !currentHits.add(hit.trim())) {
            iterator.remove();
          }
        }
        final Object[] descriptions = optionDescriptions.toArray();
        objects = ArrayUtil.mergeArrays(objects, descriptions);
      }
    }
    return objects;
  }

  private static void collectActions(Map<AnAction, String> result, ActionGroup group, final String containingGroupName){
    final AnAction[] actions = group.getChildren(null);
    for (AnAction action : actions) {
      if (action != null) {
        if (action instanceof ActionGroup) {
          final ActionGroup actionGroup = (ActionGroup)action;
          final String groupName = actionGroup.getTemplatePresentation().getText();
          collectActions(result, actionGroup, groupName != null ? groupName : containingGroupName);
        } else {
          final String groupName = group.getTemplatePresentation().getText();
          result.put(action, groupName != null && groupName.length() > 0 ? groupName : containingGroupName);
        }
      }
    }
  }

  @Nullable
  public String getFullName(final Object element) {
    return getElementName(element);
  }

  public String getHelpId() {
    return "procedures.navigating.goto.action";
  }

  @NotNull
  public String[] getSeparators() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public String getElementName(final Object element) {
    if (element instanceof OptionDescription) return ((OptionDescription)element).getHit();
    if (!(element instanceof Map.Entry)) return null;
    return ((AnAction)((Map.Entry)element).getKey()).getTemplatePresentation().getText();
  }

  public boolean matches(@NotNull final String name, @NotNull final String pattern) {
    final AnAction anAction = myActionManager.getAction(name);
    final Pattern compiledPattern = getPattern(pattern);
    if (anAction != null) {
      if (!(anAction instanceof ActionGroup)) {
        final Presentation presentation = anAction.getTemplatePresentation();
        final String text = presentation.getText();
        final String description = presentation.getDescription();
        if ((text != null && myMatcher.matches(text, compiledPattern)) ||
            (description != null && myMatcher.matches(description, compiledPattern))) {
          return true;
        }
        final String groupName = myActionsMap.get(anAction);
        if (groupName != null && text != null && myMatcher.matches(groupName + " " + text, compiledPattern)) {
          return true;
        }
      }
      return false;
    }
    else {
      return true;
    }
  }

  protected Project getProject() {
    return myProject;
  }

  protected Component getContextComponent() {
    return myContextComponent;
  }

  @NotNull
  private Pattern getPattern(String pattern) {
    if (!Comparing.strEqual(pattern, myPattern)) {
      myCompiledPattern = null;
      myPattern = pattern;
    }
    if (myCompiledPattern == null) {
      final int eol = pattern.indexOf('\n');
      if (eol != -1) {
        pattern = pattern.substring(0, eol);
      }
      if (pattern.length() >= 80) {
        pattern = pattern.substring(0, 80);
      }

      final @NonNls StringBuilder buffer = new StringBuilder(".*");
      pattern = pattern.toLowerCase();
      for (int i = 0; i < pattern.length(); i++) {
        final char c = pattern.charAt(i);
        if (Character.isLetterOrDigit(c)) {
          if (Character.isLowerCase(c)) {
            buffer.append('[')
              .append(c)
              .append('|')
              .append(Character.toUpperCase(c))
              .append(']');
          } else {
            buffer.append(c);
          }
        }
        else if (c == '*') buffer.append(".*");
        else if (c == '.') buffer.append("\\.");
        else if (c == ' ') buffer.append("[^A-Z]*\\ ");
        else buffer.append("\\x").append(Integer.toHexString(c + 0x20000).substring(3));
      }

      buffer.append(".*");


      try {
        myCompiledPattern = new Perl5Compiler().compile(buffer.toString());
      }
      catch (MalformedPatternException ignore) {}
    }

    return myCompiledPattern;
  }


  @Override
  public boolean willOpenEditor() {
    return false;
  }
}
