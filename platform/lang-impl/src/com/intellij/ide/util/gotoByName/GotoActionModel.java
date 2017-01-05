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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.OnOffButton;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;
import static com.intellij.ui.SimpleTextAttributes.STYLE_SEARCH_MATCH;

public class GotoActionModel implements ChooseByNameModel, Comparator<Object>, DumbAware {
  private static final Pattern INNER_GROUP_WITH_IDS = Pattern.compile("(.*) \\(\\d+\\)");

  @Nullable private final Project myProject;
  private final Component myContextComponent;
  @Nullable private final Editor myEditor;
  @Nullable private final PsiFile myFile;

  protected final ActionManager myActionManager = ActionManager.getInstance();

  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;

  protected final Map<AnAction, String> myActionGroups = ContainerUtil.newHashMap();

  private final NotNullLazyValue<Map<String, String>> myConfigurablesNames = VolatileNotNullLazyValue.createValue(() -> {
    Map<String, String> map = ContainerUtil.newTroveMap();
    for (Configurable configurable : ShowSettingsUtilImpl.getConfigurables(getProject(), true)) {
      if (configurable instanceof SearchableConfigurable) {
        map.put(((SearchableConfigurable)configurable).getId(), configurable.getDisplayName());
      }
    }
    return map;
  });

  private final ModalityState myModality = ModalityState.defaultModalityState();

  public GotoActionModel(@Nullable Project project, Component component, @Nullable Editor editor, @Nullable PsiFile file) {
    myProject = project;
    myContextComponent = component;
    myEditor = editor;
    myFile = file;
    ActionGroup mainMenu = (ActionGroup)myActionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU);
    collectActions(myActionGroups, mainMenu, mainMenu.getTemplatePresentation().getText());
  }

  @NotNull
  Map<String, ApplyIntentionAction> getAvailableIntentions() {
    Map<String, ApplyIntentionAction> map = new TreeMap<>();
    if (myProject != null && myEditor != null && myFile != null) {
      ApplyIntentionAction[] children = ApplyIntentionAction.getAvailableIntentions(myEditor, myFile);
      if (children != null) {
        for (ApplyIntentionAction action : children) {
          map.put(action.getName(), action);
        }
      }
    }
    return map;
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
    return new GotoActionListCellRenderer(this::getGroupName);
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
    return event;
  }

  public static Color defaultActionForeground(boolean isSelected, @Nullable Presentation presentation) {
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
    String name = myConfigurablesNames.getValue().get(id);
    String settings = SystemInfo.isMac ? "Preferences" : "Settings";
    if (name == null) return settings;
    return settings + " > " + name;
  }

  void initConfigurables() {
    myConfigurablesNames.getValue();
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

  protected MatchMode actionMatches(@NotNull String pattern, MinusculeMatcher matcher, @NotNull AnAction anAction) {
    Presentation presentation = anAction.getTemplatePresentation();
    String text = presentation.getText();
    String description = presentation.getDescription();
    String groupName = myActionGroups.get(anAction);
    if (text != null && matcher.matches(text)) {
      return MatchMode.NAME;
    }
    else if (description != null && !description.equals(text) && matcher.matches(description)) {
      return MatchMode.DESCRIPTION;
    }
    if (text == null) {
      return MatchMode.NONE;
    }
    if (matcher.matches(groupName + " " + text)) {
      return anAction instanceof ToggleAction ? MatchMode.NAME : MatchMode.GROUP;
    }
    return matcher.matches(text + " " + groupName) ? MatchMode.GROUP : MatchMode.NONE;
  }

  @Nullable
  protected Project getProject() {
    return myProject;
  }

  protected Component getContextComponent() {
    return myContextComponent;
  }

  @NotNull
  public SortedSet<Object> sortItems(@NotNull Set<Object> elements) {
    List<ActionWrapper> toUpdate = getActionsToUpdate(elements);
    if (!toUpdate.isEmpty()) {
      updateActions(toUpdate);
    }

    TreeSet<Object> objects = ContainerUtilRt.newTreeSet(this);
    objects.addAll(elements);
    return objects;
  }

  @NotNull
  private static List<ActionWrapper> getActionsToUpdate(@NotNull Set<Object> elements) {
    List<ActionWrapper> toUpdate = new ArrayList<>();
    for (Object element : elements) {
      if (element instanceof MatchedValue) {
        Comparable value = ((MatchedValue)element).value;
        if (value instanceof ActionWrapper && !((ActionWrapper)value).hasPresentation()) {
          toUpdate.add((ActionWrapper)value);
        }
      }
    }
    return toUpdate;
  }

  private void updateActions(List<ActionWrapper> toUpdate) {
    TransferToEDTQueue<ActionWrapper> queue = new TransferToEDTQueue<ActionWrapper>("goto action", aw -> {
      aw.getPresentation();
      return true;
    }, Conditions.FALSE, 50) {
      @Override
      protected void schedule(@NotNull Runnable updateRunnable) {
        ApplicationManager.getApplication().invokeLater(updateRunnable, myModality);
      }
    };
    for (ActionWrapper wrapper : toUpdate) {
      queue.offer(wrapper);
    }
    while (queue.size() > 0) {
      ProgressManager.checkCanceled();
      TimeoutUtil.sleep(50);
    }
  }

  public enum MatchMode {
    NONE, INTENTION, NAME, DESCRIPTION, GROUP, NON_MENU
  }

  @Override
  public boolean willOpenEditor() {
    return false;
  }

  @Override
  public boolean useMiddleMatching() {
    return true;
  }

  public static class ActionWrapper implements Comparable<ActionWrapper> {
    @NotNull private final AnAction myAction;
    @NotNull private final MatchMode myMode;
    @Nullable  private final String myGroupName;
    private final DataContext myDataContext;
    private volatile Presentation myPresentation;

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

    private boolean hasPresentation() {
      return myPresentation != null;
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
        panel.setToolTipText(presentation.getDescription());

        Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(ActionManager.getInstance().getId(anAction));
        String shortcutText = KeymapUtil.getPreferredShortcutText(
          shortcuts);
        if (StringUtil.isNotEmpty(shortcutText)) {
          nameComponent.append(" " + shortcutText,
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
