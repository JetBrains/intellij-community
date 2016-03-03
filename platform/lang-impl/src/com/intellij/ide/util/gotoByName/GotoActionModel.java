/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.search.BooleanOptionDescription;
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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.OnOffButton;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.apache.oro.text.regex.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;
import static com.intellij.ui.SimpleTextAttributes.STYLE_SEARCH_MATCH;

@SuppressWarnings("TestOnlyProblems")
public class GotoActionModel implements ChooseByNameModel, CustomMatcherModel, Comparator<Object>, EdtSortingModel, DumbAware {
  @NotNull private static java.util.regex.Pattern INNER_GROUP_WITH_IDS = java.util.regex.Pattern.compile("(.*) \\(\\d+\\)");

  @Nullable private final Project myProject;
  private final Component myContextComponent;

  protected final ActionManager myActionManager = ActionManager.getInstance();

  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;

  private Pattern myCompiledPattern;

  protected final SearchableOptionsRegistrar myIndex;
  protected final Map<AnAction, String> myActionGroups = ContainerUtil.newHashMap();

  protected final Map<String, ApplyIntentionAction> myIntentions = new TreeMap<String, ApplyIntentionAction>();
  private final Map<String, String> myConfigurablesNames = ContainerUtil.newTroveMap();

  public GotoActionModel(@Nullable Project project, Component component) {
    this(project, component, null, null);
  }

  public GotoActionModel(@Nullable Project project, Component component, @Nullable Editor editor, @Nullable PsiFile file) {
    myProject = project;
    myContextComponent = component;
    ActionGroup mainMenu = (ActionGroup)myActionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU);
    collectActions(myActionGroups, mainMenu, mainMenu.getTemplatePresentation().getText());
    if (project != null && editor != null && file != null) {
      ApplyIntentionAction[] children = ApplyIntentionAction.getAvailableIntentions(editor, file);
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

  private void fillConfigurablesNames(@NotNull Configurable[] configurables) {
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

  @Nullable
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

    public MatchedValue(@NotNull Comparable value, @NotNull String pattern) {
      this.value = value;
      this.pattern = pattern;
    }

    @Nullable
    @VisibleForTesting
    public String getValueText() {
      if (value instanceof OptionDescription) return ((OptionDescription)value).getHit();
      if (!(value instanceof ActionWrapper)) return null;
      return ((ActionWrapper)value).getAction().getTemplatePresentation().getText();
    }

    @Nullable
    @Override
    public String toString() {
      return getMatchingDegree() + " " + getValueText();
    }

    private int getMatchingDegree() {
      String text = getValueText();
      if (text != null) {
        int degree = getRank(text);
        return value instanceof ActionWrapper && !((ActionWrapper)value).isGroupAction() ? degree + 1 : degree;
      }
      return 0;
    }

    private int getRank(@NotNull String text) {
      if (StringUtil.equalsIgnoreCase(StringUtil.trimEnd(text, "..."), pattern)) return 3;
      if (StringUtil.startsWithIgnoreCase(text, pattern)) return 2;
      if (StringUtil.containsIgnoreCase(text, pattern)) return 1;
      return 0;
    }

    @Override
    public int compareTo(@NotNull MatchedValue o) {
      int diff = o.getMatchingDegree() - getMatchingDegree();
      if (diff != 0) return diff;

      boolean edt = ApplicationManager.getApplication().isDispatchThread();

      if (value instanceof ActionWrapper && o.value instanceof ActionWrapper) {
        if (edt) {
          boolean p1Enable = ((ActionWrapper)value).isAvailable();
          boolean p2enable = ((ActionWrapper)o.value).isAvailable();
          if (p1Enable && !p2enable) return -1;
          if (!p1Enable && p2enable) return 1;
        }
        //noinspection unchecked
        int compared = value.compareTo(o.value);
        if (compared != 0) return compared;
      }
      
      if (value instanceof ActionWrapper && o.value instanceof BooleanOptionDescription) {
        return edt && ((ActionWrapper)value).isAvailable() ? -1 : 1;
      }
      
      if (o.value instanceof ActionWrapper && value instanceof BooleanOptionDescription) {
        return edt && ((ActionWrapper)o.value).isAvailable() ? 1 : -1;
      }
      
      if (value instanceof OptionDescription && o.value instanceof BooleanOptionDescription) return 1;
      if (o.value instanceof OptionDescription && value instanceof BooleanOptionDescription) return -1;

      if (value instanceof OptionDescription && !(o.value instanceof OptionDescription)) return 1;
      if (o.value instanceof OptionDescription && !(value instanceof OptionDescription)) return -1;

      diff = StringUtil.notNullize(getValueText()).length() - StringUtil.notNullize(o.getValueText()).length();
      if (diff != 0) return diff;
      
      //noinspection unchecked
      diff = value.compareTo(o.value);
      if (diff != 0) return diff;
      
      return o.hashCode() - hashCode(); 
    }
  }

  @Override
  public ListCellRenderer getListCellRenderer() {
    return new GotoActionListCellRenderer(new Function<OptionDescription, String>() {
      @NotNull
      @Override
      public String fun(@NotNull OptionDescription description) {
        return getGroupName(description);
      }
    });
  }

  protected String getActionId(@NotNull AnAction anAction) {
    return myActionManager.getId(anAction);
  }

  @NotNull
  private static JLabel createIconLabel(@Nullable Icon icon) {
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    if (icon != null && icon.getIconWidth() <= EMPTY_ICON.getIconWidth() && icon.getIconHeight() <= EMPTY_ICON.getIconHeight()) {
      layeredIcon
        .setIcon(icon, 1, (-icon.getIconWidth() + EMPTY_ICON.getIconWidth()) / 2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight()) / 2);
    }

    return new JLabel(layeredIcon);
  }


  @NotNull
  protected JLabel createActionLabel(@NotNull AnAction anAction, String anActionName,
                                     Color fg, Color bg,
                                     @Nullable Icon icon) {
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    if (icon != null && icon.getIconWidth() <= EMPTY_ICON.getIconWidth() && icon.getIconHeight() <= EMPTY_ICON.getIconHeight()) {
      layeredIcon
        .setIcon(icon, 1, (-icon.getIconWidth() + EMPTY_ICON.getIconWidth()) / 2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight()) / 2);
    }

    Shortcut shortcut = preferKeyboardShortcut(KeymapManager.getInstance().getActiveKeymap().getShortcuts(getActionId(anAction)));
    String actionName = anActionName + (shortcut != null ? " (" + KeymapUtil.getShortcutText(shortcut) + ")" : "");
    JLabel actionLabel = new JLabel(actionName, layeredIcon, SwingConstants.LEFT);
    actionLabel.setBackground(bg);
    actionLabel.setForeground(fg);
    return actionLabel;
  }

  @Nullable
  private static Shortcut preferKeyboardShortcut(@Nullable Shortcut[] shortcuts) {
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
    return ((MatchedValue)o1).compareTo((MatchedValue)o2);
  }

  @NotNull
  public static AnActionEvent updateActionBeforeShow(@NotNull AnAction anAction, @NotNull DataContext dataContext) {
    AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.ACTION_SEARCH, null, dataContext);
    ActionUtil.performDumbAwareUpdate(anAction, event, false);
    ActionUtil.performDumbAwareUpdate(anAction, event, true);
    return event;
  }

  protected static Color defaultActionForeground(boolean isSelected, @Nullable Presentation presentation) {
    if (isSelected) return UIUtil.getListSelectionForeground();
    if (presentation != null && (!presentation.isEnabled() || !presentation.isVisible())) return UIUtil.getInactiveTextColor();
    return UIUtil.getListForeground();
  }

  @Override
  @NotNull
  public String[] getNames(boolean checkBoxState) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  @NotNull
  public Object[] getElementsByName(String id, boolean checkBoxState, String pattern) {
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

  private void collectActions(@NotNull Map<AnAction, String> result, @NotNull ActionGroup group, @Nullable String containingGroupName) {
    AnAction[] actions = group.getChildren(null);
    includeGroup(result, group, actions, containingGroupName);
    for (AnAction action : actions) {
      if (action == null || action instanceof Separator) continue;
      if (action instanceof ActionGroup) {
        ActionGroup actionGroup = (ActionGroup)action;
        String groupName = actionGroup.getTemplatePresentation().getText();
        collectActions(result, actionGroup, getGroupName(StringUtil.isEmpty(groupName) || !actionGroup.isPopup() ? containingGroupName : groupName));
      }
      else {
        String groupName = group.getTemplatePresentation().getText();
        if (result.containsKey(action)) {
          result.put(action, null);
        }
        else {
          result.put(action, getGroupName(StringUtil.isEmpty(groupName) ? containingGroupName : groupName));
        }
      }
    }
  }

  @Nullable
  private static String getGroupName(@Nullable String groupName) {
    if (groupName != null) {
      Matcher matcher = INNER_GROUP_WITH_IDS.matcher(groupName);
      if (matcher.matches()) return matcher.group(1);
    }
    return groupName;  
  }

  private void includeGroup(@NotNull Map<AnAction, String> result,
                            @NotNull ActionGroup group,
                            @NotNull AnAction[] actions,
                            @Nullable String containingGroupName) {
    boolean showGroup = true;
    for (AnAction action : actions) {
      if (myActionManager.getId(action) != null) {
        showGroup = false;
        break;
      }
    }
    if (showGroup) {
      result.put(group, getGroupName(containingGroupName));
    }
  }

  @Override
  @Nullable
  public String getFullName(@NotNull Object element) {
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

  @Nullable
  @Override
  public String getElementName(@NotNull Object mv) {
    return ((MatchedValue) mv).getValueText();
  }

  @Override
  public boolean matches(@NotNull String name, @NotNull String pattern) {
    AnAction anAction = myActionManager.getAction(name);
    if (anAction == null) return true;
    return actionMatches(pattern, anAction) != MatchMode.NONE;
  }

  protected MatchMode actionMatches(@NotNull String pattern, @NotNull AnAction anAction) {
    Pattern compiledPattern = getPattern(pattern);
    Presentation presentation = anAction.getTemplatePresentation();
    String text = presentation.getText();
    String description = presentation.getDescription();
    String groupName = myActionGroups.get(anAction);
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
    if (groupName == null) {
      return matches(pattern, compiledPattern, matcher, text) ? MatchMode.NON_MENU : MatchMode.NONE;
    }
    if (matches(pattern, compiledPattern, matcher, groupName + " " + text)) {
      return anAction instanceof ToggleAction ? MatchMode.NAME : MatchMode.GROUP;
    }
    return matches(pattern, compiledPattern, matcher, text + " " + groupName) ? MatchMode.GROUP : MatchMode.NONE;
  }

  private static boolean matches(@NotNull String pattern, Pattern compiledPattern, @NotNull PatternMatcher matcher, @NotNull String str) {
    return StringUtil.containsIgnoreCase(str, pattern) || matcher.matches(str, compiledPattern);
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
    TreeSet<Object> objects = ContainerUtilRt.newTreeSet(this);
    objects.addAll(elements);
    return objects;
  }

  @VisibleForTesting
  public enum MatchMode {
    NONE, INTENTION, NAME, DESCRIPTION, GROUP, NON_MENU
  }

  static String convertPattern(@NotNull String pattern) {
    int eol = pattern.indexOf('\n');
    if (eol != -1) {
      pattern = pattern.substring(0, eol);
    }
    if (pattern.length() >= 80) {
      pattern = pattern.substring(0, 80);
    }

    @NonNls StringBuilder buffer = new StringBuilder();

    boolean allowToLower = true;
    if (containsOnlyUppercaseLetters(pattern)) {
      allowToLower = false;
    }

    if (allowToLower) {
      buffer.append(".*");
    }

    boolean firstIdentifierLetter = true;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
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
        buffer.append(".*\\ ");
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

  private static boolean containsOnlyUppercaseLetters(@NotNull String s) {
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
    @NotNull
    @Override
    protected PatternMatcher initialValue() {
      return new Perl5Matcher();
    }
  };
  PatternMatcher getMatcher() {
    return myMatcher.get();
  }

  public static class ActionWrapper implements Comparable<ActionWrapper> {
    @NotNull private final AnAction myAction;
    @NotNull private final MatchMode myMode;
    @Nullable  private final String myGroupName;
    private final DataContext myDataContext;
    private Presentation myPresentation;

    public ActionWrapper(@NotNull AnAction action, @Nullable String groupName, @NotNull MatchMode mode, DataContext dataContext) {
      myAction = action;
      myMode = mode;
      myGroupName = groupName;
      myDataContext = dataContext;
    }

    @NotNull
    public AnAction getAction() {
      return myAction;
    }

    @NotNull
    public MatchMode getMode() {
      return myMode;
    }

    @Override
    public int compareTo(@NotNull ActionWrapper o) {
      int compared = myMode.compareTo(o.getMode());
      if (compared != 0) return compared;
      Presentation myPresentation = myAction.getTemplatePresentation();
      Presentation oPresentation = o.getAction().getTemplatePresentation();
      String myText = myPresentation.getText();
      String oText = oPresentation.getText();
      int byText = StringUtil.compare(StringUtil.trimEnd(myText, "..."), StringUtil.trimEnd(oText, "..."), true);
      if (byText != 0) return byText;
      int byTextLength = StringUtil.notNullize(myText).length() - StringUtil.notNullize(oText).length();
      if (byTextLength != 0) return byTextLength;
      int byGroup = Comparing.compare(myGroupName, o.getGroupName());
      if (byGroup != 0) return byGroup;
      int byDesc = StringUtil.compare(myPresentation.getDescription(), oPresentation.getDescription(), true);
      if (byDesc != 0) return byDesc;
      return 0;
    }

    public boolean isAvailable() {
      return getPresentation().isEnabledAndVisible();
    }

    public Presentation getPresentation() {
      if (myPresentation != null) return myPresentation;
      return myPresentation = updateActionBeforeShow(myAction, myDataContext).getPresentation();
    }

    @Nullable
    public String getGroupName() {
      if (myAction instanceof ActionGroup && Comparing.equal(myAction.getTemplatePresentation().getText(), myGroupName)) return null;
      return myGroupName;
    }
    
    public boolean isGroupAction() {
      return myAction instanceof ActionGroup;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ActionWrapper && compareTo((ActionWrapper)obj) == 0;
    }

    @Override
    public int hashCode() {
      String text = myAction.getTemplatePresentation().getText();
      return text != null ? text.hashCode() : 0;
    }

    @Override
    public String toString() {
      return myAction.toString();
    }
  }

  public static class GotoActionListCellRenderer extends DefaultListCellRenderer {
    private final Function<OptionDescription, String> myGroupNamer;

    public GotoActionListCellRenderer(Function<OptionDescription, String> groupNamer) {
      myGroupNamer = groupNamer;
    }

    @NotNull
    @Override
    public Component getListCellRendererComponent(@NotNull JList list,
                                                  Object matchedValue,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
      boolean showIcon = UISettings.getInstance().SHOW_ICONS_IN_MENUS;
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(IdeBorderFactory.createEmptyBorder(2));
      panel.setOpaque(true);
      Color bg = UIUtil.getListBackground(isSelected);
      panel.setBackground(bg);

      SimpleColoredComponent nameComponent = new SimpleColoredComponent();
      nameComponent.setBackground(bg);
      panel.add(nameComponent, BorderLayout.CENTER);
      
      if (matchedValue instanceof String) { //...
        nameComponent.append((String)matchedValue, new SimpleTextAttributes(STYLE_PLAIN, defaultActionForeground(isSelected, null)));
        if (showIcon) {
          panel.add(new JBLabel(EMPTY_ICON), BorderLayout.WEST);
        }
        return panel;
      }

      Color groupFg = isSelected ? UIUtil.getListSelectionForeground() : UIUtil.getLabelDisabledForeground();

      Object value = ((MatchedValue) matchedValue).value;
      String pattern = ((MatchedValue)matchedValue).pattern;

      Border eastBorder = IdeBorderFactory.createEmptyBorder(0, 0, 0, 2);
      if (value instanceof ActionWrapper) {
        ActionWrapper actionWithParentGroup = (ActionWrapper)value;
        AnAction anAction = actionWithParentGroup.getAction();
        Presentation presentation = anAction.getTemplatePresentation();
        boolean toggle = anAction instanceof ToggleAction;
        String groupName = actionWithParentGroup.getAction() instanceof ApplyIntentionAction ? null : actionWithParentGroup.getGroupName();
        Color fg = defaultActionForeground(isSelected, actionWithParentGroup.getPresentation());
        if (showIcon) {
          panel.add(createIconLabel(presentation.getIcon()), BorderLayout.WEST);
        }
        appendWithColoredMatches(nameComponent, getName(presentation.getText(), groupName, toggle), pattern, fg, isSelected);

        Shortcut shortcut = preferKeyboardShortcut(KeymapManager.getInstance().getActiveKeymap().getShortcuts(ActionManager.getInstance().getId(anAction)));
        if (shortcut != null) {
          nameComponent.append(" " + KeymapUtil.getShortcutText(shortcut),
                               new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER | SimpleTextAttributes.STYLE_BOLD,
                                                        UIUtil.isUnderDarcula() ? groupFg : ColorUtil.shift(groupFg, 1.3)));
        }

        if (toggle) {
          AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, ((ActionWrapper)value).myDataContext);
          boolean selected = ((ToggleAction)anAction).isSelected(event);
          addOnOffButton(panel, selected);
        }
        else {
          if (groupName != null) {
            JLabel groupLabel = new JLabel(groupName);
            groupLabel.setBackground(bg);
            groupLabel.setBorder(eastBorder);
            groupLabel.setForeground(groupFg);
            panel.add(groupLabel, BorderLayout.EAST);
          }
        }
      }
      else if (value instanceof OptionDescription) {
        if (!isSelected && !(value instanceof BooleanOptionDescription)) {
          Color descriptorBg = UIUtil.isUnderDarcula() ? ColorUtil.brighter(UIUtil.getListBackground(), 1) : LightColors.SLIGHTLY_GRAY;
          panel.setBackground(descriptorBg);
          nameComponent.setBackground(descriptorBg);
        }
        String hit = ((OptionDescription)value).getHit();
        if (hit == null) {
          hit = ((OptionDescription)value).getOption();
        }
        hit = StringUtil.unescapeXml(hit);
        hit = hit.replace("  ", " "); // avoid extra spaces from mnemonics and xml conversion
        String fullHit = hit;
        hit = StringUtil.first(hit, 45, true);

        Color fg = UIUtil.getListForeground(isSelected);

        appendWithColoredMatches(nameComponent, hit.trim(), pattern, fg, isSelected);

        if (showIcon) {
          panel.add(new JLabel(EMPTY_ICON), BorderLayout.WEST);
        }
        panel.setToolTipText(fullHit);

        if (value instanceof BooleanOptionDescription) {
          boolean selected = ((BooleanOptionDescription)value).isOptionEnabled();
          addOnOffButton(panel, selected);
        }
        else {
          JLabel settingsLabel = new JLabel(myGroupNamer.fun((OptionDescription)value));
          settingsLabel.setForeground(groupFg);
          settingsLabel.setBackground(bg);
          settingsLabel.setBorder(eastBorder);
          panel.add(settingsLabel, BorderLayout.EAST);
        }
      }
      return panel;
    }

    private static void addOnOffButton(@NotNull JPanel panel, boolean selected) {
      OnOffButton button = new OnOffButton();
      button.setSelected(selected);
      panel.add(button, BorderLayout.EAST);
      panel.setBorder(IdeBorderFactory.createEmptyBorder(0, 2, 0, 2));
    }

    @NotNull
    private static String getName(@Nullable String text, @Nullable String groupName, boolean toggle) {
      return toggle && StringUtil.isNotEmpty(groupName)
             ? StringUtil.isNotEmpty(text) ? groupName + ": " + text 
                                           : groupName : StringUtil.notNullize(text);
    }

    private static void appendWithColoredMatches(SimpleColoredComponent nameComponent,
                                                 @NotNull String name,
                                                 @NotNull String pattern,
                                                 Color fg,
                                                 boolean selected) {
      SimpleTextAttributes plain = new SimpleTextAttributes(STYLE_PLAIN, fg);
      SimpleTextAttributes highlighted = new SimpleTextAttributes(null, fg, null, STYLE_SEARCH_MATCH);
      List<TextRange> fragments = ContainerUtil.newArrayList();
      if (selected) {
        int matchStart = StringUtil.indexOfIgnoreCase(name, pattern, 0);
        if (matchStart >= 0) {
          fragments.add(TextRange.from(matchStart, pattern.length()));
        }
      }
      SpeedSearchUtil.appendColoredFragments(nameComponent, name, fragments, plain, highlighted);
    }
  }
}
