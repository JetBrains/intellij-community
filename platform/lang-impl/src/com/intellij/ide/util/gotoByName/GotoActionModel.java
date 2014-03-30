/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.LightColors;
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
  @NonNls public static final String SETTINGS_KEY = "$$$SETTINGS$$$";
  @NonNls public static final String INTENTIONS_KEY = "$$$INTENTIONS_KEY$$$";
  @Nullable private final Project myProject;
  private final Component myContextComponent;

  protected final ActionManager myActionManager = ActionManager.getInstance();

  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;

  private Pattern myCompiledPattern;

  protected final SearchableOptionsRegistrar myIndex;
  protected final Map<AnAction, String> myActionsMap = new TreeMap<AnAction, String>(new Comparator<AnAction>() {
    @Override
    public int compare(AnAction o1, AnAction o2) {
      int compare = Comparing.compare(o1.getTemplatePresentation().getText(), o2.getTemplatePresentation().getText());
      if (compare == 0 && !o1.equals(o2)) {
        return o1.hashCode() - o2.hashCode();
      }
      return compare;
    }
  });

  protected final Map<String, ApplyIntentionAction> myIntentions = new TreeMap<String, ApplyIntentionAction>();

  public GotoActionModel(@Nullable Project project, final Component component) {
    this(project, component, null, null);
  }

  public GotoActionModel(@Nullable Project project, final Component component, @Nullable Editor editor, @Nullable PsiFile file) {
    myProject = project;
    myContextComponent = component;
    final ActionGroup mainMenu = (ActionGroup)myActionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU);
    collectActions(myActionsMap, mainMenu, mainMenu.getTemplatePresentation().getText());
    if (project != null && editor != null && file != null) {
      final ApplyIntentionAction[] children = ApplyIntentionAction.getAvailableIntentions(editor, file);
      if (children != null) {
        for (ApplyIntentionAction action : children) {
          myIntentions.put(action.getName(), action);
        }
      }
    }
    myIndex = SearchableOptionsRegistrar.getInstance();
  }

  @Override
  public String getPromptText() {
    return IdeBundle.message("prompt.gotoaction.enter.action");
  }

  @Override
  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.other.included");
  }

  @Override
  public char getCheckBoxMnemonic() {
    return 'd';
  }

  @Override
  public String getNotInMessage() {
    return IdeBundle.message("label.no.menu.actions.found");
  }

  @Override
  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.actions.found");
  }

  @Override
  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = getPropertiesStorage();
    return Boolean.TRUE.toString().equals(propertiesComponent.getValue("GoToAction.toSaveAllIncluded")) &&
           propertiesComponent.isTrueValue("GoToAction.allIncluded");
  }

  private PropertiesComponent getPropertiesStorage() {
    return myProject != null ? PropertiesComponent.getInstance(myProject) : PropertiesComponent.getInstance();
  }

  @Override
  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = getPropertiesStorage();
    if (Boolean.TRUE.toString().equals(propertiesComponent.getValue("GoToAction.toSaveAllIncluded"))) {
      propertiesComponent.setValue("GoToAction.allIncluded", Boolean.toString(state));
    }
  }

  @Override
  public ListCellRenderer getListCellRenderer() {
    return new DefaultListCellRenderer() {

      @Override
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(IdeBorderFactory.createEmptyBorder(2));
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
        }
        else if (value instanceof OptionDescription) {
          if (!isSelected && !UIUtil.isUnderDarcula()) {
            panel.setBackground(LightColors.SLIGHTLY_GRAY);
          }
          String hit = ((OptionDescription)value).getHit();
          if (hit == null) {
            hit = ((OptionDescription)value).getOption();
          }
          hit = StringUtil.unescapeXml(hit);
          if (hit.length() > 60) {
            hit = hit.substring(0, 60) + "...";
          }
          hit = hit.replace("  ", " "); //avoid extra spaces from mnemonics and xml conversion

          final Color fg = isSelected ? UIUtil.getListSelectionForeground() : UIUtil.getListForeground();
          final JLabel label = new JLabel(hit.trim());
          label.setIcon(EMPTY_ICON);
          label.setForeground(fg);
          label.setBackground(bg);
          panel.add(label, BorderLayout.WEST);
          final JLabel settingsLabel = new JLabel("Settings");
          settingsLabel.setForeground(fg);
          settingsLabel.setBackground(bg);
          panel.add(settingsLabel, BorderLayout.EAST);
        }
        else if (value instanceof String) {
          final JBLabel label = new JBLabel((String)value);
          label.setIcon(EMPTY_ICON);
          panel.add(label, BorderLayout.WEST);
        }
        return panel;
      }
    };
  }

  protected String getActionId(@NotNull final AnAction anAction) {
    return myActionManager.getId(anAction);
  }

  protected JLabel createActionLabel(final AnAction anAction, final String anActionName,
                                     final Color fg, final Color bg,
                                     final Icon icon) {
    final LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    if (icon != null && icon.getIconWidth() <= EMPTY_ICON.getIconWidth() && icon.getIconHeight() <= EMPTY_ICON.getIconHeight()) {
      layeredIcon
        .setIcon(icon, 1, (-icon.getIconWidth() + EMPTY_ICON.getIconWidth()) / 2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight()) / 2);
    }

    final Shortcut shortcut = preferKeyboardShortcut(KeymapManager.getInstance().getActiveKeymap().getShortcuts(getActionId(anAction)));
    final String actionName = anActionName + (shortcut != null ? " (" + KeymapUtil.getShortcutText(shortcut) + ")" : "");
    final JLabel actionLabel = new JLabel(actionName, layeredIcon, SwingConstants.LEFT);
    actionLabel.setBackground(bg);
    actionLabel.setForeground(fg);
    return actionLabel;
  }

  private static Shortcut preferKeyboardShortcut(Shortcut[] shortcuts) {
    if (shortcuts != null) {
      for (Shortcut shortcut : shortcuts) {
        if (shortcut.isKeyboard()) return shortcut;
      }
      return shortcuts.length > 0 ? shortcuts[0] : null;
    }
    return null;
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

  public static AnActionEvent updateActionBeforeShow(AnAction anAction, DataContext dataContext) {
    final AnActionEvent event = new AnActionEvent(null, dataContext,
                                                  ActionPlaces.UNKNOWN, new Presentation(), ActionManager.getInstance(),
                                                  0);
    ActionUtil.performDumbAwareUpdate(anAction, event, false);
    ActionUtil.performDumbAwareUpdate(anAction, event, true);
    return event;
  }

  protected static Color defaultActionForeground(boolean isSelected, Presentation presentation) {
    if (isSelected) {
      return UIUtil.getListSelectionForeground();
    }

    if (!presentation.isEnabled() || !presentation.isVisible()) {
      return UIUtil.getInactiveTextColor();
    }

    return UIUtil.getListForeground();
  }

  @Override
  @NotNull
  public String[] getNames(boolean checkBoxState) {
    final ArrayList<String> result = new ArrayList<String>();
    result.add(INTENTIONS_KEY);
    for (AnAction action : myActionsMap.keySet()) {
      result.add(getActionId(action));
    }
    if (checkBoxState) {
      final Set<String> ids = ((ActionManagerImpl)myActionManager).getActionIds();
      for (String id : ids) {
        result.add(id);
      }
    }
    result.add(SETTINGS_KEY);
    return ArrayUtil.toStringArray(result);
  }

  @Override
  @NotNull
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
          map.put(anAction, null);
        }
      }
    } else if (Comparing.strEqual(id, INTENTIONS_KEY)) {
      for (String intentionText : myIntentions.keySet()) {
        final ApplyIntentionAction intentionAction = myIntentions.get(intentionText);
        if (actionMatches(pattern, intentionAction)) {
          map.put(intentionAction, intentionText);
        }
      }
    }
    Object[] objects = map.entrySet().toArray(new Map.Entry[map.size()]);
    if (Comparing.strEqual(id, SETTINGS_KEY)) {
      final Set<String> words = myIndex.getProcessedWords(pattern);
      Set<OptionDescription> optionDescriptions = null;
      final String actionManagerName = myActionManager.getComponentName();
      for (String word : words) {
        final Set<OptionDescription> descriptions = ((SearchableOptionsRegistrarImpl)myIndex).getAcceptableDescriptions(word);
        if (descriptions != null) {
          for (Iterator<OptionDescription> iterator = descriptions.iterator(); iterator.hasNext(); ) {
            OptionDescription description = iterator.next();
            if (actionManagerName.equals(description.getPath())) {
              iterator.remove();
            }
          }
          if (!descriptions.isEmpty()) {
            if (optionDescriptions == null) {
              optionDescriptions = descriptions;
            }
            else {
              optionDescriptions.retainAll(descriptions);
            }
          }
        } else {
          optionDescriptions = null;
          break;
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
        Arrays.sort(descriptions);
        objects = ArrayUtil.mergeArrays(objects, descriptions);
      }
    }
    return objects;
  }

  private void collectActions(Map<AnAction, String> result, ActionGroup group, final String containingGroupName) {
    final AnAction[] actions = group.getChildren(null);
    includeGroup(result, group, actions, containingGroupName);
    for (AnAction action : actions) {
      if (action != null) {
        if (action instanceof ActionGroup) {
          final ActionGroup actionGroup = (ActionGroup)action;
          final String groupName = actionGroup.getTemplatePresentation().getText();
          collectActions(result, actionGroup, StringUtil.isEmpty(groupName) || !actionGroup.isPopup() ? containingGroupName : groupName);
        }
        else {
          final String groupName = group.getTemplatePresentation().getText();
          if (result.containsKey(action)) {
            result.put(action, null);
          }
          else {
            result.put(action, StringUtil.isEmpty(groupName) ? containingGroupName : groupName);
          }
        }
      }
    }
  }

  private void includeGroup(Map<AnAction, String> result,
                            ActionGroup group,
                            AnAction[] actions,
                            String containingGroupName) {
    boolean showGroup = true;
    for (AnAction action : actions) {
      if (myActionManager.getId(action) != null) {
        showGroup = false;
        break;
      }
    }
    if (showGroup) {
      result.put(group, containingGroupName);
    }
  }

  @Override
  @Nullable
  public String getFullName(final Object element) {
    return getElementName(element);
  }

  @NonNls
  @Override
  public String getHelpId() {
    return "procedures.navigating.goto.action";
  }

  @Override
  @NotNull
  public String[] getSeparators() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public String getElementName(final Object element) {
    if (element instanceof OptionDescription) return ((OptionDescription)element).getHit();
    if (!(element instanceof Map.Entry)) return null;
    return ((AnAction)((Map.Entry)element).getKey()).getTemplatePresentation().getText();
  }

  @Override
  public boolean matches(@NotNull final String name, @NotNull final String pattern) {
    final AnAction anAction = myActionManager.getAction(name);
    if (anAction == null) return true;
    return actionMatches(pattern, anAction);
  }

  protected boolean actionMatches(String pattern, @NotNull AnAction anAction) {
    final Pattern compiledPattern = getPattern(pattern);
    final Presentation presentation = anAction.getTemplatePresentation();
    final String text = presentation.getText();
    final String description = presentation.getDescription();
    PatternMatcher matcher = getMatcher();
    if (text != null && matcher.matches(text, compiledPattern) ||
        description != null && !description.equals(text) && matcher.matches(description, compiledPattern)) {
      return true;
    }
    final String groupName = myActionsMap.get(anAction);
    return groupName != null && text != null && matcher.matches(groupName + " " + text, compiledPattern);
  }

  @Nullable
  protected Project getProject() {
    return myProject;
  }

  protected Component getContextComponent() {
    return myContextComponent;
  }

  @NotNull
  private Pattern getPattern(@NotNull String pattern) {
    String converted = convertPattern(pattern.trim());
    Pattern compiledPattern = myCompiledPattern;
    if (compiledPattern != null && !Comparing.strEqual(converted, compiledPattern.getPattern())) {
      compiledPattern = null;
    }
    if (compiledPattern == null) {
      try {
        myCompiledPattern = compiledPattern = new Perl5Compiler().compile(converted, Perl5Compiler.READ_ONLY_MASK);
      }
      catch (MalformedPatternException e) {
        //do nothing
      }
    }

    return compiledPattern;
  }

  private String convertPattern(String pattern) {
    final int eol = pattern.indexOf('\n');
    if (eol != -1) {
      pattern = pattern.substring(0, eol);
    }
    if (pattern.length() >= 80) {
      pattern = pattern.substring(0, 80);
    }

    @NonNls final StringBuilder buffer = new StringBuilder();

    boolean allowToLower = true;
    if (containsOnlyUppercaseLetters(pattern)) {
      allowToLower = false;
    }

    if (allowToLower) {
      buffer.append(".*");
    }

    boolean firstIdentifierLetter = true;
    for (int i = 0; i < pattern.length(); i++) {
      final char c = pattern.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        // This logic allows to use uppercase letters only to catch the name like PDM for PsiDocumentManager
        if (Character.isUpperCase(c) || Character.isDigit(c)) {

          if (!firstIdentifierLetter) {
            buffer.append("[^A-Z]*");
          }

          buffer.append("[");
          buffer.append(c);
          if (allowToLower || i == 0) {
            buffer.append('|');
            buffer.append(Character.toLowerCase(c));
          }
          buffer.append("]");
        }
        else if (Character.isLowerCase(c)) {
          buffer.append('[');
          buffer.append(c);
          buffer.append('|');
          buffer.append(Character.toUpperCase(c));
          buffer.append(']');
        }
        else {
          buffer.append(c);
        }

        firstIdentifierLetter = false;
      }
      else if (c == '*') {
        buffer.append(".*");
        firstIdentifierLetter = true;
      }
      else if (c == '.') {
        buffer.append("\\.");
        firstIdentifierLetter = true;
      }
      else if (c == ' ') {
        buffer.append("[^A-Z]*\\ ");
        firstIdentifierLetter = true;
      }
      else {
        firstIdentifierLetter = true;
        // for standard RegExp engine
        // buffer.append("\\u");
        // buffer.append(Integer.toHexString(c + 0x20000).substring(1));

        // for OROMATCHER RegExp engine
        buffer.append("\\x");
        buffer.append(Integer.toHexString(c + 0x20000).substring(3));
      }
    }

    buffer.append(".*");
    return buffer.toString();
  }

  private static boolean containsOnlyUppercaseLetters(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c != '*' && c != ' ' && !Character.isUpperCase(c)) return false;
    }
    return true;
  }

  @Override
  public boolean willOpenEditor() {
    return false;
  }

  @Override
  public boolean useMiddleMatching() {
    return false;
  }

  private final ThreadLocal<PatternMatcher> myMatcher = new ThreadLocal<PatternMatcher>() {
    @Override
    protected PatternMatcher initialValue() {
      return new Perl5Matcher();
    }
  };
  private PatternMatcher getMatcher() {
    return myMatcher.get();
  }
}
