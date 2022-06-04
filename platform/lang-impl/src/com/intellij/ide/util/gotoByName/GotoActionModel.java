// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.gotoByName;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.BundleBase;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.ui.RegistryTextOptionDescriptor;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.WordPrefixMatcher;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.OnOffButton;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GotoActionModel implements ChooseByNameModel, Comparator<Object>, DumbAware {
  private static final Logger LOG = Logger.getInstance(GotoActionModel.class);
  private static final Pattern INNER_GROUP_WITH_IDS = Pattern.compile("(.*) \\(\\d+\\)");
  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;

  private final @Nullable Project myProject;
  private final @Nullable WeakReference<Editor> myEditor;
  private final DataContext myDataContext;
  private volatile UpdateSession myUpdateSession;

  private final ActionManager myActionManager = ActionManager.getInstance();

  private final Map<AnAction, GroupMapping> myActionGroups = new ConcurrentHashMap<>();

  private final NotNullLazyValue<Map<@NonNls String, @NlsContexts.ConfigurableName String>> myConfigurablesNames =
    NotNullLazyValue.volatileLazy(() -> {
      if (SwingUtilities.isEventDispatchThread() && !ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error("Configurable names must not be loaded on EDT");
      }

      Map<@NonNls String, @NlsContexts.ConfigurableName String> map = new HashMap<>();
      for (Configurable configurable : ShowSettingsUtilImpl.getConfigurables(getProject(), true)) {
        if (configurable instanceof SearchableConfigurable) {
          map.put(((SearchableConfigurable)configurable).getId(), configurable.getDisplayName());
        }
      }
      return map;
    });

  public GotoActionModel(@Nullable Project project, @Nullable Component component, @Nullable Editor editor) {
    myProject = project;
    myEditor = new WeakReference<>(editor);
    myDataContext = Utils.wrapDataContext(DataManager.getInstance().getDataContext(component));
    myUpdateSession = newUpdateSession();
  }

  @NotNull
  private UpdateSession newUpdateSession() {
    AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.ACTION_SEARCH, null, myDataContext);
    return Utils.getOrCreateUpdateSession(event);
  }

  void buildGroupMappings() {
    if (!myActionGroups.isEmpty()) return;
    ActionGroup mainMenu = Objects.requireNonNull((ActionGroup)myActionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU));
    ActionGroup keymapOthers = Objects.requireNonNull((ActionGroup)myActionManager.getActionOrStub("Other.KeymapGroup"));

    Map<AnAction, GroupMapping> mainGroups = new HashMap<>();
    collectActions(mainGroups, mainMenu, Collections.emptyList(), false);

    Map<AnAction, GroupMapping> otherGroups = new HashMap<>();
    collectActions(otherGroups, keymapOthers, Collections.emptyList(), true);

    myActionGroups.putAll(mainGroups);
    otherGroups.forEach(myActionGroups::putIfAbsent);
  }

  @NotNull
  Map<String, ApplyIntentionAction> getAvailableIntentions() {
    Map<String, ApplyIntentionAction> map = new TreeMap<>();
    Editor editor = myEditor != null ? myEditor.get() : null;
    if (myProject != null && !myProject.isDisposed() && !DumbService.isDumb(myProject) &&
        editor != null && !editor.isDisposed()) {
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      ApplyIntentionAction[] children = file == null ? null : ApplyIntentionAction.getAvailableIntentions(editor, file);
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

  @NotNull
  @Override
  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.disabled.included");
  }


  @NotNull
  @Override
  public String getNotInMessage() {
    return IdeBundle.message("label.no.enabled.actions.found");
  }

  @NotNull
  @Override
  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.actions.found");
  }

  @Override
  public boolean loadInitialCheckBoxState() {
    return false;
  }

  @Override
  public void saveInitialCheckBoxState(boolean state) {
  }

  @ApiStatus.Internal
  public void clearCaches() {
    myActionGroups.clear();
    myUpdateSession = newUpdateSession();
  }

  public enum MatchedValueType { ABBREVIATION, INTENTION, TOP_HIT, OPTION, ACTION }

  public static class MatchedValue {
    @NotNull public final Object value;
    @NotNull final MatchedValueType type;
    @NotNull final String pattern;
    final int matchingDegree;

    MatchedValue(@NotNull Object value, @NotNull String pattern, @NotNull MatchedValueType type) {
      assert value instanceof OptionDescription || value instanceof ActionWrapper;
      this.value = value;
      this.pattern = pattern;
      matchingDegree = calcMatchingDegree();
      this.type = type;
    }

    MatchedValue(@NotNull Object value, @NotNull String pattern, int degree, @NotNull MatchedValueType type) {
      assert value instanceof OptionDescription || value instanceof ActionWrapper;
      this.value = value;
      this.pattern = pattern;
      matchingDegree = degree;
      this.type = type;
    }

    @Nullable
    @VisibleForTesting
    public String getValueText() {
      return GotoActionItemProvider.getActionText(value);
    }

    @Nullable
    @Override
    public String toString() {
      return getMatchingDegree() + " " + getValueText();
    }

    public int getMatchingDegree() {
      return matchingDegree;
    }

    @NotNull
    public MatchedValueType getType() {
      return type;
    }

    public int getValueTypeWeight() {
      return getTypeWeight(value);
    }

    private int calcMatchingDegree() {
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

    public int compareWeights(@NotNull MatchedValue o) {
      if (o == this) return 0;
      int diff = o.getMatchingDegree() - getMatchingDegree();
      if (diff != 0) return diff;

      diff = getTypeWeight(o.value) - getTypeWeight(value);
      if (diff != 0) return diff;

      if (value instanceof ActionWrapper && o.value instanceof ActionWrapper) {
        ActionWrapper value1 = (ActionWrapper)value;
        ActionWrapper value2 = (ActionWrapper)o.value;
        int compared = value1.compareWeights(value2);
        if (compared != 0) return compared;
      }

      diff = StringUtil.notNullize(getValueText()).length() - StringUtil.notNullize(o.getValueText()).length();
      if (diff != 0) return diff;

      if (value instanceof OptionDescription && o.value instanceof OptionDescription) {
        OptionDescription value1 = (OptionDescription)value;
        OptionDescription value2 = (OptionDescription)o.value;
        diff = value1.compareTo(value2);
        if (diff != 0) return diff;
      }

      return o.hashCode() - hashCode();
    }

    private static int getTypeWeight(@NotNull Object value) {
      if (value instanceof ActionWrapper) {
        ActionWrapper actionWrapper = (ActionWrapper)value;
        if ((ApplicationManager.getApplication().isDispatchThread() || actionWrapper.hasPresentation()) &&
            actionWrapper.isAvailable()) {
          return 0;
        }
        return 2;
      }
      if (value instanceof OptionDescription) {
        if (value instanceof BooleanOptionDescription) return 1;
        return 3;
      }
      throw new IllegalArgumentException(value.getClass() + " - " + value);
    }

    @Override
    public final boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof MatchedValue)) return false;
      MatchedValue value1 = (MatchedValue)o;
      return Objects.equals(value, value1.value) &&
             Objects.equals(pattern, value1.pattern);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, pattern);
    }
  }

  @NotNull
  @Override
  public ListCellRenderer<?> getListCellRenderer() {
    return new GotoActionListCellRenderer(this::getGroupName);
  }

  @NotNull
  private static JLabel createIconLabel(@Nullable Icon icon, boolean disabled) {
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    if (icon == null) return new JLabel(layeredIcon);

    int width = icon.getIconWidth();
    int height = icon.getIconHeight();
    int emptyIconWidth = EMPTY_ICON.getIconWidth();
    int emptyIconHeight = EMPTY_ICON.getIconHeight();
    if (width <= emptyIconWidth && height <= emptyIconHeight) {
      layeredIcon.setIcon(disabled && IconLoader.isGoodSize(icon) ? IconLoader.getDisabledIcon(icon) : icon, 1,
                          (emptyIconWidth - width) / 2,
                          (emptyIconHeight - height) / 2);
    }

    return new JLabel(layeredIcon);
  }

  @Override
  public int compare(@NotNull Object o1, @NotNull Object o2) {
    if (ChooseByNameBase.EXTRA_ELEM.equals(o1)) return 1;
    if (ChooseByNameBase.EXTRA_ELEM.equals(o2)) return -1;
    return ((MatchedValue)o1).compareWeights((MatchedValue)o2);
  }

  public static Color defaultActionForeground(boolean isSelected, boolean hasFocus, @Nullable Presentation presentation) {
    if (isSelected) return UIUtil.getListSelectionForeground(hasFocus);
    if (presentation != null && !presentation.isEnabledAndVisible()) return UIUtil.getInactiveTextColor();
    return UIUtil.getListForeground();
  }

  @Override
  public String @NotNull [] getNames(boolean checkBoxState) {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Override
  public Object @NotNull [] getElementsByName(@NotNull String id, boolean checkBoxState, @NotNull String pattern) {
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Nls
  @NotNull
  public String getGroupName(@NotNull OptionDescription description) {
    if (description instanceof RegistryTextOptionDescriptor) return LangBundle.message("group.registry");
    String groupName = description.getGroupName();
    String settings = SystemInfo.isMac ? LangBundle.message("group.preferences") : LangBundle.message("group.settings");
    if (groupName == null || groupName.equals(description.getHit())) return settings;
    return settings + " > " + groupName;
  }

  @NotNull
  Map<@NonNls String, @NlsContexts.ConfigurableName String> getConfigurablesNames() {
    return myConfigurablesNames.getValue();
  }

  private void collectActions(@NotNull Map<? super AnAction, GroupMapping> actionGroups,
                              @NotNull ActionGroup group,
                              @NotNull List<ActionGroup> path,
                              boolean showNonPopupGroups) {
    if (actionGroups.containsKey(group)) return;

    List<? extends AnAction> actions = ReadAction.nonBlocking(() -> {
      try {
        return myUpdateSession.children(group);
      }
      catch (PluginException e) {
        LOG.error(e);
        return Collections.<AnAction>emptyList();
      }
    })
      .executeSynchronously();

    boolean hasRegisteredChild = ContainerUtil.exists(actions, action -> myActionManager.getId(action) != null);
    if (!hasRegisteredChild) {
      GroupMapping mapping = actionGroups.computeIfAbsent(group, __ -> new GroupMapping(showNonPopupGroups));
      mapping.addPath(path);
    }

    List<ActionGroup> newPath = ContainerUtil.append(path, group);
    for (AnAction action : actions) {
      if (action == null || action instanceof Separator) continue;
      if (action instanceof ActionGroup) {
        collectActions(actionGroups, (ActionGroup)action, newPath, showNonPopupGroups);
      }
      else {
        GroupMapping mapping = actionGroups.computeIfAbsent(action, __ -> new GroupMapping(showNonPopupGroups));
        mapping.addPath(newPath);
      }
    }
  }

  @Nullable
  GroupMapping getGroupMapping(@NotNull AnAction action) {
    return myActionGroups.get(action);
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
  public String @NotNull [] getSeparators() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Nullable
  @Override
  public String getElementName(@NotNull Object mv) {
    return ((MatchedValue)mv).getValueText();
  }

  @NotNull
  MatchMode actionMatches(@NotNull String pattern, com.intellij.util.text.Matcher matcher, @NotNull AnAction anAction) {
    Presentation presentation = anAction.getTemplatePresentation().clone();
    anAction.applyTextOverride(ActionPlaces.ACTION_SEARCH, presentation);
    String text = presentation.getText();
    String description = presentation.getDescription();
    if (text != null && matcher.matches(text)) {
      return MatchMode.NAME;
    }
    for (Supplier<String> synonym : anAction.getSynonyms()) {
      if (matcher.matches(synonym.get())) {
        return MatchMode.SYNONYM;
      }
    }
    if (description != null && !description.equals(text) && new WordPrefixMatcher(pattern).matches(description)) {
      return MatchMode.DESCRIPTION;
    }
    if (text == null) {
      return MatchMode.NONE;
    }
    GroupMapping groupMapping = myActionGroups.get(anAction);
    if (groupMapping != null) {
      for (String groupName : groupMapping.getAllGroupNames()) {
        if (matcher.matches(groupName + " " + text)) {
          return anAction instanceof ToggleAction ? MatchMode.NAME : MatchMode.GROUP;
        }
        if (matcher.matches(text + " " + groupName)) {
          return MatchMode.GROUP;
        }
      }
    }

    for (GotoActionAliasMatcher m : GotoActionAliasMatcher.EP_NAME.getExtensions()) {
      MatchMode match = m.matchAction(anAction, pattern);
      if (match != MatchMode.NONE) {
        return match;
      }
    }
    return MatchMode.NONE;
  }

  @Nullable
  Project getProject() {
    return myProject;
  }

  @NotNull
  DataContext getDataContext() {
    // This data context can be reused because
    // 1. it was reused before
    // 2. context component shall not change much while SE popup is open
    // 2. EDT event count check is not applied
    return myDataContext;
  }

  @NotNull
  private UpdateSession getUpdateSession() {
    return myUpdateSession;
  }

  @NotNull
  public SortedSet<Object> sortItems(@NotNull Set<Object> elements) {
    TreeSet<Object> objects = new TreeSet<>(this);
    objects.addAll(elements);
    return objects;
  }

  @Override
  public boolean willOpenEditor() {
    return false;
  }

  @Override
  public boolean useMiddleMatching() {
    return true;
  }

  public static class GroupMapping implements Comparable<GroupMapping> {
    private final boolean myShowNonPopupGroups;
    private final List<List<ActionGroup>> myPaths = new ArrayList<>();

    @Nullable @ActionText private String myBestGroupName;
    private boolean myBestNameComputed;

    public GroupMapping() {
      this(false);
    }

    public GroupMapping(boolean showNonPopupGroups) {
      myShowNonPopupGroups = showNonPopupGroups;
    }

    @NotNull
    public static GroupMapping createFromText(@ActionText String text, boolean showGroupText) {
      GroupMapping mapping = new GroupMapping(showGroupText);
      mapping.addPath(Collections.singletonList(new DefaultActionGroup(text, false)));
      return mapping;
    }

    private void addPath(@NotNull List<ActionGroup> path) {
      myPaths.add(path);
    }


    @Override
    public int compareTo(@NotNull GroupMapping o) {
      return Comparing.compare(getFirstGroupName(), o.getFirstGroupName());
    }

    @ActionText
    @Nullable
    public String getBestGroupName() {
      if (myBestNameComputed) return myBestGroupName;
      return getFirstGroupName();
    }

    @Nls
    @Nullable
    private String getFirstGroupName() {
      List<ActionGroup> path = ContainerUtil.getFirstItem(myPaths);
      return path != null ? getPathName(path) : null;
    }

    private void updateBeforeShow(@NotNull UpdateSession session) {
      if (myBestNameComputed) return;
      myBestNameComputed = true;

      for (List<ActionGroup> path : myPaths) {
        String name = getActualPathName(path, session);
        if (name != null) {
          myBestGroupName = name;
          return;
        }
      }
    }

    @NotNull
    public List<String> getAllGroupNames() {
      return ContainerUtil.map(myPaths, path -> getPathName(path));
    }

    @Nls
    @Nullable
    private String getPathName(@NotNull List<? extends ActionGroup> path) {
      String name = "";
      for (ActionGroup group : path) {
        name = appendGroupName(name, group, group.getTemplatePresentation());
      }
      return StringUtil.nullize(name);
    }

    @Nls
    @Nullable
    private String getActualPathName(@NotNull List<? extends ActionGroup> path, @NotNull UpdateSession session) {
      String name = "";
      for (ActionGroup group : path) {
        Presentation presentation = session.presentation(group);
        if (!presentation.isVisible()) return null;
        name = appendGroupName(name, group, presentation);
      }
      return StringUtil.nullize(name);
    }

    @Nls
    @NotNull
    private String appendGroupName(@NotNull @Nls String prefix, @NotNull ActionGroup group, @NotNull Presentation presentation) {
      if (group.isPopup() || myShowNonPopupGroups) {
        String groupName = getActionGroupName(presentation);
        if (!StringUtil.isEmptyOrSpaces(groupName)) {
          return prefix.isEmpty()
                 ? groupName
                 : prefix + " | " + groupName;
        }
      }
      return prefix;
    }

    @ActionText
    @Nullable
    private static String getActionGroupName(@NotNull Presentation presentation) {
      String text = presentation.getText();
      if (text == null) return null;

      Matcher matcher = INNER_GROUP_WITH_IDS.matcher(text);
      if (matcher.matches()) return matcher.group(1);

      return text;
    }
  }

  public static class ActionWrapper {
    @NotNull private final AnAction myAction;
    @NotNull private final MatchMode myMode;
    @Nullable private final GroupMapping myGroupMapping;
    private final GotoActionModel myModel;
    private final Presentation myPresentation;
    private final String myActionText;

    public ActionWrapper(@NotNull AnAction action,
                         @Nullable GroupMapping groupMapping,
                         @NotNull MatchMode mode,
                         @NotNull GotoActionModel model) {
      myAction = action;
      myMode = mode;
      myGroupMapping = groupMapping;
      myModel = model;
      myPresentation = ReadAction.nonBlocking(() -> {
          if (myGroupMapping != null) {
            myGroupMapping.updateBeforeShow(myModel.getUpdateSession());
          }
          return myModel.getUpdateSession().presentation(myAction);
        })
        .executeSynchronously();
      myActionText = GotoActionItemProvider.getActionText(action);
    }

    public String getActionText() {
      return myActionText;
    }

    @NotNull
    public AnAction getAction() {
      return myAction;
    }

    @NotNull
    public MatchMode getMode() {
      return myMode;
    }

    public int compareWeights(@NotNull ActionWrapper o) {
      int compared = myMode.compareTo(o.getMode());
      if (compared != 0) return compared;

      Presentation myPresentation = myAction.getTemplatePresentation();
      Presentation oPresentation = o.getAction().getTemplatePresentation();
      String myText = StringUtil.notNullize(myActionText);
      String oText = StringUtil.notNullize(o.getActionText());
      int byText = StringUtil.compare(StringUtil.trimEnd(myText, "..."), StringUtil.trimEnd(oText, "..."), true);
      if (byText != 0) return byText;
      int byTextLength = StringUtil.notNullize(myText).length() - StringUtil.notNullize(oText).length();
      if (byTextLength != 0) return byTextLength;
      int byGroup = Comparing.compare(myGroupMapping, o.myGroupMapping);
      if (byGroup != 0) return byGroup;
      int byDesc = StringUtil.compare(myPresentation.getDescription(), oPresentation.getDescription(), true);
      if (byDesc != 0) return byDesc;
      int byClassHashCode = Comparing.compare(myAction.getClass().hashCode(), o.myAction.getClass().hashCode());
      if (byClassHashCode != 0) return byClassHashCode;
      int byInstanceHashCode = Comparing.compare(myAction.hashCode(), o.myAction.hashCode());
      if (byInstanceHashCode != 0) return byInstanceHashCode;
      return 0;
    }

    public boolean isAvailable() {
      return getPresentation().isEnabledAndVisible();
    }

    @NotNull
    public Presentation getPresentation() {
      return myPresentation;
    }

    public boolean hasPresentation() {
      return myPresentation != null;
    }

    @ActionText
    @Nullable
    public String getGroupName() {
      if (myGroupMapping == null) return null;
      String groupName = myGroupMapping.getBestGroupName();
      if (myAction instanceof ActionGroup && Objects.equals(myAction.getTemplatePresentation().getText(), groupName)) return null;
      return groupName;
    }

    public boolean isGroupAction() {
      return myAction instanceof ActionGroup;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ActionWrapper && myAction.equals(((ActionWrapper)obj).myAction);
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

  @DirtyUI
  public static class GotoActionListCellRenderer extends DefaultListCellRenderer {
    public static final Border TOGGLE_BUTTON_BORDER = JBUI.Borders.empty(0, 2);
    private final Function<? super OptionDescription, @ActionText String> myGroupNamer;
    private final boolean myUseListFont;

    public GotoActionListCellRenderer(Function<? super OptionDescription, String> groupNamer) {
      this(groupNamer, false);
    }

    public GotoActionListCellRenderer(Function<? super OptionDescription, String> groupNamer, boolean useListFont) {
      myGroupNamer = groupNamer;
      myUseListFont = useListFont;
    }

    @NotNull
    @Override
    public Component getListCellRendererComponent(@NotNull JList list,
                                                  Object matchedValue,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
      SimpleColoredComponent nameComponent = new SimpleColoredComponent();

      boolean showIcon = UISettings.getInstance().getShowIconsInMenus();
      JPanel panel = new JPanel(new BorderLayout()) {
        @Override
        public AccessibleContext getAccessibleContext() {
          return nameComponent.getAccessibleContext();
        }
      };

      panel.setBorder(JBUI.Borders.empty(2));
      panel.setOpaque(true);
      Color bg = UIUtil.getListBackground(isSelected, cellHasFocus);
      panel.setBackground(bg);

      if (myUseListFont) {
        nameComponent.setFont(list.getFont());
      }
      nameComponent.setOpaque(false);
      panel.add(nameComponent, BorderLayout.CENTER);

      if (matchedValue instanceof String) { //...
        if (showIcon) {
          panel.add(new JBLabel(EMPTY_ICON), BorderLayout.WEST);
        }
        String str = cutName((String)matchedValue, null, list, panel, nameComponent);
        nameComponent.append(str, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, defaultActionForeground(isSelected, cellHasFocus, null)));
        return panel;
      }

      Color groupFg = isSelected ? UIUtil.getListSelectionForeground(true) : UIUtil.getInactiveTextColor();

      Object value = ((MatchedValue)matchedValue).value;
      String pattern = ((MatchedValue)matchedValue).pattern;

      Border eastBorder = JBUI.Borders.emptyRight(2);
      if (value instanceof ActionWrapper) {
        ActionWrapper actionWithParentGroup = (ActionWrapper)value;
        AnAction anAction = actionWithParentGroup.getAction();
        boolean toggle = anAction instanceof ToggleAction;
        String groupName = actionWithParentGroup.getAction() instanceof ApplyIntentionAction ? null : actionWithParentGroup.getGroupName();
        Presentation presentation = actionWithParentGroup.getPresentation();
        Color fg = defaultActionForeground(isSelected, cellHasFocus, presentation);
        boolean disabled = !isSelected && !presentation.isEnabledAndVisible();

        if (disabled) {
          groupFg = UIUtil.getLabelDisabledForeground();
        }

        if (showIcon) {
          Icon icon = presentation.getIcon();
          panel.add(createIconLabel(icon, disabled), BorderLayout.WEST);
        }

        if (toggle) {
          DataContext dataContext = actionWithParentGroup.myModel.getDataContext();
          AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext);
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

        panel.setToolTipText(presentation.getDescription());
        @NlsSafe String actionId = ActionManager.getInstance().getId(anAction);
        Shortcut[] shortcuts = KeymapUtil.getActiveKeymapShortcuts(actionId).getShortcuts();
        String shortcutText = KeymapUtil.getPreferredShortcutText(shortcuts);
        String name = getName(presentation.getText(), groupName, toggle);
        name = cutName(name, shortcutText, list, panel, nameComponent);

        appendWithColoredMatches(nameComponent, name, pattern, fg, isSelected);
        if (UISettings.getInstance().getShowInplaceCommentsInternal() && actionId != null) {
          nameComponent.append(" " + actionId + " ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        if (StringUtil.isNotEmpty(shortcutText)) {
          nameComponent.append(" " + shortcutText,
                               new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER | SimpleTextAttributes.STYLE_BOLD, groupFg));
        }
      }
      else if (value instanceof OptionDescription) {
        if (!isSelected && !(value instanceof BooleanOptionDescription)) {
          Color descriptorBg;
          if (StartupUiUtil.isUnderDarcula()) {
            descriptorBg = ColorUtil.brighter(UIUtil.getListBackground(), 1);
          }
          else {
            if (ExperimentalUI.isNewUI()) {
              Color color = JBUI.CurrentTheme.Popup.BACKGROUND;
              descriptorBg = ColorUtil.isDark(color) ? ColorUtil.brighter(color, 1) : ColorUtil.darker(color, 1);
            } else {
              descriptorBg = LightColors.SLIGHTLY_GRAY;
            }
          }
          panel.setBackground(descriptorBg);
          nameComponent.setBackground(descriptorBg);
        }
        String hit = calcHit((OptionDescription)value);
        Color fg = UIUtil.getListForeground(isSelected, cellHasFocus);

        if (showIcon) {
          panel.add(new JLabel(EMPTY_ICON), BorderLayout.WEST);
        }
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

        String name = cutName(hit, null, list, panel, nameComponent);
        appendWithColoredMatches(nameComponent, name, pattern, fg, isSelected);
      }
      return panel;
    }

    @ActionText
    @NotNull
    private static String calcHit(@NotNull OptionDescription value) {
      if (value instanceof RegistryTextOptionDescriptor) {
        return value.getHit() + " = " + value.getValue();
      }
      String hit = StringUtil.defaultIfEmpty(value.getHit(), value.getOption());
      return StringUtil.unescapeXmlEntities(StringUtil.notNullize(hit))
        .replace(BundleBase.MNEMONIC_STRING, "")
        .replace("  ", " "); // avoid extra spaces from mnemonics and xml conversion
    }

    @ActionText
    private static String cutName(@ActionText String name,
                                  @NlsSafe String shortcutText,
                                  JList<?> list,
                                  JPanel panel,
                                  SimpleColoredComponent nameComponent) {
      if (!list.isShowing() || list.getWidth() <= 0) {
        return StringUtil.first(name, 60, true); // fallback to previous behaviour
      }

      // we have a min size for SE, which is ~40 symbols, don't spend time for trimming, let's use a shortcut
      if (name.length() < 40) return name;

      int freeSpace = calcFreeSpace(list, panel, nameComponent, shortcutText);

      if (freeSpace <= 0) {
        return name;
      }

      FontMetrics fm = nameComponent.getFontMetrics(nameComponent.getFont());
      int strWidth = fm.stringWidth(name);
      if (strWidth <= freeSpace) {
        return name;
      }

      int cutSymbolIndex = (int)((((double)freeSpace - fm.stringWidth("...")) / strWidth) * name.length());
      cutSymbolIndex = Integer.max(1, cutSymbolIndex);
      name = name.substring(0, cutSymbolIndex);
      while (fm.stringWidth(name + "...") > freeSpace && name.length() > 1) {
        name = name.substring(0, name.length() - 1);
      }

      return name.trim() + "...";
    }

    private static int calcFreeSpace(JList<?> list, JPanel panel, SimpleColoredComponent nameComponent, String shortcutText) {
      BorderLayout layout = (BorderLayout)panel.getLayout();
      Component eastComponent = layout.getLayoutComponent(BorderLayout.EAST);
      Component westComponent = layout.getLayoutComponent(BorderLayout.WEST);
      int freeSpace = list.getWidth()
                      - (list.getInsets().right + list.getInsets().left)
                      - (panel.getInsets().right + panel.getInsets().left)
                      - (eastComponent == null ? 0 : eastComponent.getPreferredSize().width)
                      - (westComponent == null ? 0 : westComponent.getPreferredSize().width)
                      - (nameComponent.getInsets().right + nameComponent.getInsets().left)
                      - (nameComponent.getIpad().right + nameComponent.getIpad().left)
                      - nameComponent.getIconTextGap();

      if (StringUtil.isNotEmpty(shortcutText)) {
        FontMetrics fm = nameComponent.getFontMetrics(nameComponent.getFont().deriveFont(Font.BOLD));
        freeSpace -= fm.stringWidth(" " + shortcutText);
      }

      return freeSpace;
    }

    private static void addOnOffButton(@NotNull JPanel panel, boolean selected) {
      OnOffButton button = new OnOffButton();
      button.setSelected(selected);
      panel.add(button, BorderLayout.EAST);
      panel.setBorder(TOGGLE_BUTTON_BORDER);
    }

    @ActionText
    @NotNull
    private static String getName(@Nullable @ActionText String text, @Nullable @ActionText String groupName, boolean toggle) {
      return toggle && StringUtil.isNotEmpty(groupName)
             ? StringUtil.isNotEmpty(text) ? groupName + ": " + text
                                           : groupName : StringUtil.notNullize(text);
    }

    private static void appendWithColoredMatches(SimpleColoredComponent nameComponent,
                                                 @NotNull @ActionText String name,
                                                 @NotNull @NlsSafe String pattern,
                                                 Color fg,
                                                 boolean selected) {
      SimpleTextAttributes plain = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fg);

      if (name.startsWith("<html>")) {
        new HtmlToSimpleColoredComponentConverter(HtmlToSimpleColoredComponentConverter.DEFAULT_TAG_HANDLER).appendHtml(nameComponent, name, plain);
        name = nameComponent.getCharSequence(false).toString();
      }
      else {
        nameComponent.append(name, plain);
      }

      nameComponent.setDynamicSearchMatchHighlighting(false);
      if (selected) {
        int matchStart = StringUtil.indexOfIgnoreCase(name, pattern, 0);
        if (matchStart >= 0) {
          nameComponent.setDynamicSearchMatchHighlighting(true);
          List<TextRange> fragments = Collections.singletonList(TextRange.from(matchStart, pattern.length()));
          SpeedSearchUtil.applySpeedSearchHighlighting(nameComponent, fragments, true);
        }
      }
    }
  }
}
