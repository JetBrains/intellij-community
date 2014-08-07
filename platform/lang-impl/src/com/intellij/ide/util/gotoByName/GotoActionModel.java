/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.apache.oro.text.regex.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;
import static com.intellij.ui.SimpleTextAttributes.STYLE_SEARCH_MATCH;

public class GotoActionModel implements ChooseByNameModel, CustomMatcherModel, Comparator<Object>, EdtSortingModel {
  @Nullable private final Project myProject;
  private final Component myContextComponent;

  protected final ActionManager myActionManager = ActionManager.getInstance();

  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;

  private Pattern myCompiledPattern;

  protected final SearchableOptionsRegistrar myIndex;
  protected final Map<AnAction, String> myActionGroups = ContainerUtil.newHashMap();

  protected final Map<String, ApplyIntentionAction> myIntentions = new TreeMap<String, ApplyIntentionAction>();
  private final Map<String, String> myConfigurablesNames = ContainerUtil.newTroveMap();

  public GotoActionModel(@Nullable Project project, final Component component) {
    this(project, component, null, null);
  }

  public GotoActionModel(@Nullable Project project, final Component component, @Nullable Editor editor, @Nullable PsiFile file) {
    myProject = project;
    myContextComponent = component;
    final ActionGroup mainMenu = (ActionGroup)myActionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU);
    collectActions(myActionGroups, mainMenu, mainMenu.getTemplatePresentation().getText());
    if (project != null && editor != null && file != null) {
      final ApplyIntentionAction[] children = ApplyIntentionAction.getAvailableIntentions(editor, file);
      if (children != null) {
        for (ApplyIntentionAction action : children) {
          myIntentions.put(action.getName(), action);
        }
      }
    }
    myIndex = SearchableOptionsRegistrar.getInstance();
    if (!EventQueue.isDispatchThread()) {
      return;
    }
    fillConfigurablesNames(ShowSettingsUtilImpl.getConfigurables(project, true));
  }

  private void fillConfigurablesNames(Configurable[] configurables) {
    for (Configurable configurable : configurables) {
      if (configurable instanceof SearchableConfigurable) {
        myConfigurablesNames.put(((SearchableConfigurable)configurable).getId(), configurable.getDisplayName());
      }
    }
  }

  @Override
  public String getPromptText() {
    return IdeBundle.message("prompt.gotoaction.enter.action");
  }

  @Override
  public String getCheckBoxName() {
    return null;
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
    return true;
  }

  @Override
  public void saveInitialCheckBoxState(boolean state) {
  }

  public static class MatchedValue implements Comparable<MatchedValue> {
    @NotNull public final Comparable value;
    @NotNull final String pattern;

    MatchedValue(@NotNull Comparable value, @NotNull String pattern) {
      this.value = value;
      this.pattern = pattern;
    }

    @Nullable
    private String getValueText() {
      if (value instanceof OptionDescription) return ((OptionDescription)value).getHit();
      if (!(value instanceof ActionWrapper)) return null;
      return ((ActionWrapper)value).getAction().getTemplatePresentation().getText();
    }

    private int getMatchingDegree() {
      String text = getValueText();
      if (text != null) {
        if (StringUtil.equalsIgnoreCase(StringUtil.trimEnd(text, "..."), pattern)) return 3;
        if (StringUtil.startsWithIgnoreCase(text, pattern)) return 2;
        if (StringUtil.containsIgnoreCase(text, pattern)) return 1;
      }
      return 0;
    }

    @Override
    public int compareTo(@NotNull MatchedValue o) {
      if (value instanceof OptionDescription && !(o.value instanceof OptionDescription)) {
        return 1;
      }
      if (o.value instanceof OptionDescription && !(value instanceof OptionDescription)) {
        return -1;
      }

      if (value instanceof ActionWrapper && o.value instanceof ActionWrapper && ApplicationManager.getApplication().isDispatchThread()) {
        boolean p1Enable = ((ActionWrapper)value).isAvailable();
        boolean p2enable = ((ActionWrapper)o.value).isAvailable();
        if (p1Enable && !p2enable) return -1;
        if (!p1Enable && p2enable) return 1;
      }

      int diff = o.getMatchingDegree() - getMatchingDegree();
      //noinspection unchecked
      return diff != 0 ? diff : value.compareTo(o.value);
    }
  }

  @Override
  public ListCellRenderer getListCellRenderer() {
    return new DefaultListCellRenderer() {

      @Override
      public Component getListCellRendererComponent(@NotNull final JList list,
                                                    final Object matchedValue,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(IdeBorderFactory.createEmptyBorder(2));
        panel.setOpaque(true);
        Color bg = UIUtil.getListBackground(isSelected);
        panel.setBackground(bg);

        if (matchedValue instanceof String) { //...
          final JBLabel label = new JBLabel((String)matchedValue);
          label.setIcon(EMPTY_ICON);
          panel.add(label, BorderLayout.WEST);
          return panel;
        }

        Color groupFg = isSelected ? UIUtil.getListSelectionForeground() : UIUtil.getLabelDisabledForeground();

        Object value = ((MatchedValue) matchedValue).value;
        String pattern = ((MatchedValue)matchedValue).pattern;

        SimpleColoredComponent nameComponent = new SimpleColoredComponent();
        nameComponent.setBackground(bg);
        panel.add(nameComponent, BorderLayout.CENTER);

        if (value instanceof ActionWrapper) {
          final ActionWrapper actionWithParentGroup = (ActionWrapper)value;

          final AnAction anAction = actionWithParentGroup.getAction();
          final Presentation templatePresentation = anAction.getTemplatePresentation();

          final Color fg = defaultActionForeground(isSelected, actionWithParentGroup.getPresentation());

          panel.add(createIconLabel(templatePresentation.getIcon()), BorderLayout.WEST);

          appendWithColoredMatches(nameComponent, templatePresentation.getText(), pattern, fg, isSelected);

          final Shortcut shortcut = preferKeyboardShortcut(KeymapManager.getInstance().getActiveKeymap().getShortcuts(getActionId(anAction)));
          if (shortcut != null) {
            nameComponent.append(" (" + KeymapUtil.getShortcutText(shortcut) + ")", new SimpleTextAttributes(STYLE_PLAIN, groupFg));
          }

          String groupName = actionWithParentGroup.getAction() instanceof ApplyIntentionAction ? null : actionWithParentGroup.getGroupName();
          if (groupName != null) {
            final JLabel groupLabel = new JLabel(groupName);
            groupLabel.setBackground(bg);
            groupLabel.setForeground(groupFg);
            panel.add(groupLabel, BorderLayout.EAST);
          }
        }
        else if (value instanceof OptionDescription) {
          if (!isSelected) {
            Color descriptorBg = UIUtil.isUnderDarcula() ? ColorUtil.brighter(UIUtil.getListBackground(), 1) : LightColors.SLIGHTLY_GRAY;
            panel.setBackground(descriptorBg);
            nameComponent.setBackground(descriptorBg);
          }
          String hit = ((OptionDescription)value).getHit();
          if (hit == null) {
            hit = ((OptionDescription)value).getOption();
          }
          hit = StringUtil.unescapeXml(hit);
          hit = StringUtil.first(hit, 50, true);
          hit = hit.replace("  ", " "); //avoid extra spaces from mnemonics and xml conversion

          final Color fg = UIUtil.getListForeground(isSelected);

          appendWithColoredMatches(nameComponent, hit.trim(), pattern, fg, isSelected);

          panel.add(new JLabel(EMPTY_ICON), BorderLayout.WEST);

          final JLabel settingsLabel = new JLabel(getGroupName((OptionDescription)value));
          settingsLabel.setForeground(groupFg);
          settingsLabel.setBackground(bg);
          panel.add(settingsLabel, BorderLayout.EAST);
        }
        return panel;
      }

      private void appendWithColoredMatches(SimpleColoredComponent nameComponent, String name, String pattern, Color fg, boolean selected) {
        final SimpleTextAttributes plain = new SimpleTextAttributes(STYLE_PLAIN, fg);
        final SimpleTextAttributes highlighted = new SimpleTextAttributes(null, fg, null, STYLE_SEARCH_MATCH);
        List<TextRange> fragments = ContainerUtil.newArrayList();
        if (selected) {
          int matchStart = StringUtil.indexOfIgnoreCase(name, pattern, 0);
          if (matchStart >= 0) {
            fragments.add(TextRange.from(matchStart, pattern.length()));
          }
        }
        SpeedSearchUtil.appendColoredFragments(nameComponent, name, fragments, plain, highlighted);
      }
    };
  }

  protected String getActionId(@NotNull final AnAction anAction) {
    return myActionManager.getId(anAction);
  }

  private static JLabel createIconLabel(final Icon icon) {
    final LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    if (icon != null && icon.getIconWidth() <= EMPTY_ICON.getIconWidth() && icon.getIconHeight() <= EMPTY_ICON.getIconHeight()) {
      layeredIcon
        .setIcon(icon, 1, (-icon.getIconWidth() + EMPTY_ICON.getIconWidth()) / 2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight()) / 2);
    }

    return new JLabel(layeredIcon);
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
  public int compare(@NotNull Object o1, @NotNull Object o2) {
    if (ChooseByNameBase.EXTRA_ELEM.equals(o1)) return 1;
    if (ChooseByNameBase.EXTRA_ELEM.equals(o2)) return -1;
    return ((MatchedValue) o1).compareTo((MatchedValue)o2);
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
    if (isSelected) return UIUtil.getListSelectionForeground();
    if (!presentation.isEnabled() || !presentation.isVisible()) return UIUtil.getInactiveTextColor();
    return UIUtil.getListForeground();
  }

  @Override
  @NotNull
  public String[] getNames(boolean checkBoxState) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  @NotNull
  public Object[] getElementsByName(final String id, final boolean checkBoxState, final String pattern) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @NotNull
  String getGroupName(@NotNull OptionDescription description) {
    String id = description.getConfigurableId();
    String name = myConfigurablesNames.get(id);
    String settings = SystemInfo.isMac ? "Preferences" : "Settings";
    if (name == null) return settings;
    return settings + " > " + name;
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
  public String getElementName(final Object mv) {
    return ((MatchedValue) mv).getValueText();
  }

  @Override
  public boolean matches(@NotNull final String name, @NotNull final String pattern) {
    final AnAction anAction = myActionManager.getAction(name);
    if (anAction == null) return true;
    return actionMatches(pattern, anAction) != MatchMode.NONE;
  }

  protected MatchMode actionMatches(String pattern, @NotNull AnAction anAction) {
    final Pattern compiledPattern = getPattern(pattern);
    final Presentation presentation = anAction.getTemplatePresentation();
    final String text = presentation.getText();
    final String description = presentation.getDescription();
    PatternMatcher matcher = getMatcher();
    if (text != null && matcher.matches(text, compiledPattern)) {
      return MatchMode.NAME;
    }
    else if (description != null && !description.equals(text) && matcher.matches(description, compiledPattern)) {
      return MatchMode.DESCRIPTION;
    }
    if (text == null) {
      return MatchMode.NONE;
    }
    final String groupName = myActionGroups.get(anAction);
    if (groupName == null) {
      return matcher.matches(text, compiledPattern) ? MatchMode.NON_MENU : MatchMode.NONE;
    }
    return matcher.matches(groupName + " " + text, compiledPattern) ? MatchMode.GROUP : MatchMode.NONE;
  }

  @Nullable
  protected Project getProject() {
    return myProject;
  }

  protected Component getContextComponent() {
    return myContextComponent;
  }

  @NotNull
  Pattern getPattern(@NotNull String pattern) {
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

  @NotNull
  @Override
  public SortedSet<Object> sort(@NotNull Set<Object> elements) {
    TreeSet<Object> objects = ContainerUtil.newTreeSet(this);
    objects.addAll(elements);
    return objects;
  }

  protected enum MatchMode {
    NONE, INTENTION, NAME, DESCRIPTION, GROUP, NON_MENU
  }

  static String convertPattern(String pattern) {
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
    return true;
  }

  private final ThreadLocal<PatternMatcher> myMatcher = new ThreadLocal<PatternMatcher>() {
    @Override
    protected PatternMatcher initialValue() {
      return new Perl5Matcher();
    }
  };
  PatternMatcher getMatcher() {
    return myMatcher.get();
  }

  public static class ActionWrapper implements Comparable<ActionWrapper>{
    private final AnAction myAction;
    private final MatchMode myMode;
    private final String myGroupName;
    private final DataContext myDataContext;
    private Presentation myPresentation;

    public ActionWrapper(@NotNull AnAction action, String groupName, MatchMode mode, DataContext dataContext) {
      myAction = action;
      myMode = mode;
      myGroupName = groupName;
      myDataContext = dataContext;
    }

    public AnAction getAction() {
      return myAction;
    }

    public MatchMode getMode() {
      return myMode;
    }

    @Override
    public int compareTo(@NotNull ActionWrapper o) {
      int compared = myMode.compareTo(o.getMode());
      return compared != 0
             ? compared
             : StringUtil.compare(myAction.getTemplatePresentation().getText(), o.getAction().getTemplatePresentation().getText(), true);
    }

    private boolean isAvailable() {
      return getPresentation().isEnabledAndVisible();
    }

    public Presentation getPresentation() {
      if (myPresentation != null) return myPresentation;
      return myPresentation = updateActionBeforeShow(myAction, myDataContext).getPresentation();
    }

    public String getGroupName() {
      return myGroupName;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ActionWrapper && compareTo((ActionWrapper)obj) == 0;
    }

    @Override
    public int hashCode() {
      return myAction.getTemplatePresentation().getText().hashCode();
    }
  }
}
