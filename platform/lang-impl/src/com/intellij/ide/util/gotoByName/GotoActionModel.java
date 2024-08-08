// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.gotoByName;

import com.intellij.BundleBase;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.actions.searcheverywhere.MergeableElement;
import com.intellij.ide.actions.searcheverywhere.PromoAction;
import com.intellij.ide.ui.RegistryTextOptionDescriptor;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorActionUtil;
import com.intellij.internal.inspector.UiInspectorContextProvider;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionPresentationDecorator;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FeaturePromoBundle;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.WordPrefixMatcher;
import com.intellij.ui.*;
import com.intellij.ui.components.OnOffButton;
import com.intellij.ui.render.IconCompOptionalCompPanel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.components.BorderLayoutPanel;
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
  private static final Icon EMPTY_ICON = EmptyIcon.ICON_16;

  private final @Nullable Project myProject;
  private final @Nullable WeakReference<Editor> myEditor;
  private final DataContext myDataContext;
  private volatile UpdateSession myUpdateSession;

  private final ActionManager myActionManager = ActionManager.getInstance();

  private final Map<AnAction, GroupMapping> myActionGroups = new ConcurrentHashMap<>();

  private final Supplier<Map<@NonNls String, @NlsContexts.ConfigurableName String>> myConfigurablesNames =
    new SynchronizedClearableLazy<>(() -> {
      if (SwingUtilities.isEventDispatchThread() && !ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error("Configurable names must not be loaded on EDT");
      }

      Map<@NonNls String, @NlsContexts.ConfigurableName String> map = new HashMap<>();
      for (Configurable configurable : ShowSettingsUtilImpl.getConfigurables(getProject(), true, true)) {
        if (configurable instanceof SearchableConfigurable) {
          map.put(((SearchableConfigurable)configurable).getId(), configurable.getDisplayName());
        }
      }
      return map;
    });

  public GotoActionModel(@Nullable Project project, @Nullable Component component, @Nullable Editor editor) {
    myProject = project;
    myEditor = new WeakReference<>(editor);
    myDataContext = Utils.createAsyncDataContext(DataManager.getInstance().getDataContext(component));
    myUpdateSession = newUpdateSession();
  }

  private @NotNull UpdateSession newUpdateSession() {
    AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.ACTION_SEARCH, null, myDataContext);
    Utils.initUpdateSession(event);
    return event.getUpdateSession();
  }

  public void buildGroupMappings() {
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

  @Override
  public @NotNull String getCheckBoxName() {
    return IdeBundle.message("checkbox.disabled.included");
  }


  @Override
  public @NotNull String getNotInMessage() {
    return IdeBundle.message("label.no.enabled.actions.found");
  }

  @Override
  public @NotNull String getNotFoundMessage() {
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

  public enum MatchedValueType {ABBREVIATION, INTENTION, TOP_HIT, OPTION, ACTION, SEMANTIC}

  public static class MatchedValue implements MergeableElement, UiInspectorContextProvider {
    public final @NotNull Object value;
    final @NotNull MatchedValueType type;
    final @NotNull String pattern;
    final int matchingDegree;

    public @Nullable Double similarityScore = null;

    public MatchedValue(@NotNull Object value, @NotNull String pattern, @NotNull MatchedValueType type) {
      LOG.assertTrue(value instanceof OptionDescription || value instanceof ActionWrapper, "Not expected: " + value.getClass());
      this.value = value;
      this.pattern = pattern;
      matchingDegree = calcMatchingDegree();
      this.type = type;
    }

    public MatchedValue(@NotNull Object value, @NotNull String pattern, @NotNull MatchedValueType type, double similarityScore) {
      this(value, pattern, type);
      this.similarityScore = similarityScore;
    }

    MatchedValue(@NotNull Object value, @NotNull String pattern, int degree, @NotNull MatchedValueType type) {
      assert value instanceof OptionDescription || value instanceof ActionWrapper;
      this.value = value;
      this.pattern = pattern;
      matchingDegree = degree;
      this.type = type;
    }

    @Override
    public MergeableElement mergeWith(MergeableElement other) {
      MatchedValue mergedValue = new MatchedValue(value, pattern, matchingDegree, type);
      if (other instanceof MatchedValue otherMatchedValue) {
        if (otherMatchedValue.type == MatchedValueType.SEMANTIC) {
          mergedValue.similarityScore = otherMatchedValue.similarityScore;
        }
      }
      return mergedValue;
    }

    @Override
    public boolean shouldBeMergedIntoAnother() {
      return similarityScore != null;
    }

    @VisibleForTesting
    public @Nullable String getValueText() {
      return ActionSearchUtilKt.getActionText(value);
    }

    @Override
    public @Nullable String toString() {
      String presentation;
      if (value instanceof OptionDescription) {
        presentation = String.format("%s, %s, %s",
                                     ((OptionDescription)value).getHit(),
                                     ((OptionDescription)value).getOption(),
                                     ((OptionDescription)value).getConfigurableId());
      }
      else {
        presentation = value.toString();
      }

      return String.format("%d %s; %s; %s", getMatchingDegree(), getValueText(), presentation, value.getClass());
    }

    public int getMatchingDegree() {
      return matchingDegree;
    }

    public @NotNull MatchedValueType getType() {
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

      if (value instanceof ActionWrapper value1 && o.value instanceof ActionWrapper value2) {
        int compared = value1.compareWeights(value2);
        if (compared != 0) return compared;
      }

      diff = StringUtil.notNullize(getValueText()).length() - StringUtil.notNullize(o.getValueText()).length();
      if (diff != 0) return diff;

      if (value instanceof OptionDescription value1 && o.value instanceof OptionDescription value2) {
        diff = value1.compareTo(value2);
        if (diff != 0) return diff;
      }

      return o.hashCode() - hashCode();
    }

    private static int getTypeWeight(@NotNull Object value) {
      if (value instanceof ActionWrapper actionWrapper) {
        if (actionWrapper.isAvailable()) {
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
    public @NotNull List<PropertyBean> getUiInspectorContext() {
      // Implement here, as GotoActionListCellRenderer is behind 9000 wrappers
      List<PropertyBean> result = new ArrayList<>();
      if (value instanceof ActionWrapper actionWrapper) {
        result.add(new PropertyBean("Action ID", UiInspectorActionUtil.getActionId(actionWrapper.myAction), true));
        result.add(new PropertyBean("Action Class", UiInspectorUtil.getClassPresentation(actionWrapper.myAction), true));
      }
      return result;
    }

    @Override
    public final boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof MatchedValue value1)) return false;
      return Objects.equals(value, value1.value) &&
             Objects.equals(pattern, value1.pattern);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, pattern);
    }
  }

  @Override
  public @NotNull ListCellRenderer<?> getListCellRenderer() {
    return new GotoActionListCellRenderer(this::getGroupName);
  }

  private static @NotNull LayeredIcon createLayeredIcon(@Nullable Icon icon, boolean disabled) {
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    if (icon == null) {
      return layeredIcon;
    }

    int width = icon.getIconWidth();
    int height = icon.getIconHeight();
    int emptyIconWidth = EMPTY_ICON.getIconWidth();
    int emptyIconHeight = EMPTY_ICON.getIconHeight();
    if (width <= emptyIconWidth && height <= emptyIconHeight) {
      layeredIcon.setIcon(disabled && IconLoader.isGoodSize(icon) ? IconLoader.getDisabledIcon(icon) : icon, 1,
                          (emptyIconWidth - width) / 2,
                          (emptyIconHeight - height) / 2);
    }

    return layeredIcon;
  }

  @Override
  public int compare(@NotNull Object o1, @NotNull Object o2) {
    if (ChooseByNameBase.EXTRA_ELEM.equals(o1)) return 1;
    if (ChooseByNameBase.EXTRA_ELEM.equals(o2)) return -1;
    return ((MatchedValue)o1).compareWeights((MatchedValue)o2);
  }

  public static Color defaultActionForeground(boolean isSelected, boolean hasFocus, @Nullable Presentation presentation) {
    if (isSelected) return NamedColorUtil.getListSelectionForeground(hasFocus);
    if (presentation != null && !presentation.isEnabledAndVisible()) return NamedColorUtil.getInactiveTextColor();
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

  public @Nls @NotNull String getGroupName(@NotNull OptionDescription description) {
    if (description instanceof RegistryTextOptionDescriptor) return LangBundle.message("group.registry");
    String groupName = description.getGroupName();
    String settings = LangBundle.message("group.settings");
    if (groupName == null || groupName.equals(description.getHit())) return settings;
    return settings + " > " + groupName;
  }

  @NotNull
  Map<@NonNls String, @NlsContexts.ConfigurableName String> getConfigurablesNames() {
    return myConfigurablesNames.get();
  }

  private void collectActions(@NotNull Map<? super AnAction, GroupMapping> actionGroups,
                              @NotNull ActionGroup group,
                              @NotNull List<ActionGroup> path,
                              boolean showNonPopupGroups) {
    if (actionGroups.containsKey(group)) return;
    AnAction[] actions = group instanceof DefaultActionGroup g ?
                         g.getChildren(myActionManager) : AnAction.EMPTY_ARRAY;

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

  public @Nullable GroupMapping getGroupMapping(@NotNull AnAction action) {
    return myActionGroups.get(action);
  }

  @Override
  public @Nullable String getFullName(@NotNull Object element) {
    return getElementName(element);
  }

  @Override
  public @NonNls String getHelpId() {
    return "procedures.navigating.goto.action";
  }

  @Override
  public String @NotNull [] getSeparators() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Override
  public @Nullable String getElementName(@NotNull Object mv) {
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

  @ApiStatus.Internal
  public @NotNull UpdateSession getUpdateSession() {
    return myUpdateSession;
  }

  public @NotNull SortedSet<Object> sortItems(@NotNull Set<Object> elements) {
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

  public static final class GroupMapping implements Comparable<GroupMapping> {
    private final boolean myShowNonPopupGroups;
    private final List<List<ActionGroup>> myPaths = new ArrayList<>();

    private @Nullable @ActionText String myBestGroupName;
    private boolean myBestNameComputed;

    public GroupMapping() {
      this(false);
    }

    public GroupMapping(boolean showNonPopupGroups) {
      myShowNonPopupGroups = showNonPopupGroups;
    }

    public static @NotNull GroupMapping createFromText(@ActionText String text, boolean showGroupText) {
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

    public @ActionText @Nullable String getBestGroupName() {
      if (myBestNameComputed) return myBestGroupName;
      return getFirstGroupName();
    }

    public @Nullable List<ActionGroup> getFirstGroup() {
      return ContainerUtil.getFirstItem(myPaths);
    }

    private @Nls @Nullable String getFirstGroupName() {
      List<ActionGroup> path = getFirstGroup();
      return path != null ? getPathName(path) : null;
    }

    public void updateBeforeShow(@NotNull UpdateSession session) {
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

    public @NotNull List<String> getAllGroupNames() {
      return ContainerUtil.map(myPaths, path -> getPathName(path));
    }

    private @Nls @Nullable String getPathName(@NotNull List<? extends ActionGroup> path) {
      String name = "";
      for (ActionGroup group : path) {
        name = appendGroupName(name, group, group.getTemplatePresentation());
      }
      return StringUtil.nullize(name);
    }

    private @Nls @Nullable String getActualPathName(@NotNull List<? extends ActionGroup> path, @NotNull UpdateSession session) {
      String name = "";
      for (ActionGroup group : path) {
        Presentation presentation = session.presentation(group);
        if (!presentation.isVisible()) return null;
        name = appendGroupName(name, group, presentation);
      }
      return StringUtil.nullize(name);
    }

    private @Nls @NotNull String appendGroupName(@NotNull @Nls String prefix, @NotNull ActionGroup group, @NotNull Presentation presentation) {
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

    private static @ActionText @Nullable String getActionGroupName(@NotNull Presentation presentation) {
      String text = presentation.getText();
      if (text == null) return null;

      Matcher matcher = INNER_GROUP_WITH_IDS.matcher(text);
      if (matcher.matches()) return matcher.group(1);

      return text;
    }
  }

  public static class ActionWrapper {
    private final @NotNull AnAction myAction;
    private final @NotNull MatchMode myMode;
    private final @Nullable GroupMapping myGroupMapping;
    private final Presentation myPresentation;
    private final String myActionText;

    public ActionWrapper(@NotNull AnAction action,
                         @Nullable GroupMapping groupMapping,
                         @NotNull MatchMode mode,
                         @NotNull Presentation presentation) {
      myAction = action;
      myMode = mode;
      myGroupMapping = groupMapping;
      myPresentation = presentation;
      myActionText = ActionSearchUtilKt.getActionText(action);
    }

    public String getActionText() {
      return myActionText;
    }

    public @NotNull AnAction getAction() {
      return myAction;
    }

    public @NotNull MatchMode getMode() {
      return myMode;
    }

    public @Nullable GroupMapping getGroupMapping() {
      return myGroupMapping;
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
      int byClassHashCode = Integer.compare(myAction.getClass().hashCode(), o.myAction.getClass().hashCode());
      if (byClassHashCode != 0) return byClassHashCode;
      int byInstanceHashCode = Integer.compare(myAction.hashCode(), o.myAction.hashCode());
      if (byInstanceHashCode != 0) return byInstanceHashCode;
      return 0;
    }

    public boolean isAvailable() {
      return myPresentation.isEnabledAndVisible();
    }

    public @NotNull Presentation getPresentation() {
      return myPresentation;
    }

    public @ActionText @Nullable String getGroupName() {
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
  public static final class GotoActionListCellRenderer extends DefaultListCellRenderer {
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

    @Override
    public @NotNull Component getListCellRendererComponent(@NotNull JList list,
                                                           Object matchedValue,
                                                           int index, boolean isSelected, boolean cellHasFocus) {
      SimpleColoredComponent nameComponent = new SimpleColoredComponent();

      boolean showIcon = UISettings.getInstance().getShowIconsInMenus();
      IconCompOptionalCompPanel<SimpleColoredComponent>
        panel = new IconCompOptionalCompPanel<>(nameComponent) {
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

      if (matchedValue instanceof String) { //...
        if (showIcon) {
          panel.setIcon(EMPTY_ICON);
        }
        String str = cutName((String)matchedValue, null, list, panel);
        nameComponent.append(str, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                                           defaultActionForeground(isSelected, cellHasFocus, null)));
        return panel;
      }

      Color groupFg;
      groupFg = isSelected ? NamedColorUtil.getListSelectionForeground(true) : NamedColorUtil.getInactiveTextColor();

      Object value = ((MatchedValue)matchedValue).value;
      String pattern = ((MatchedValue)matchedValue).pattern;

      Border eastBorder = JBUI.Borders.emptyRight(2);
      if (value instanceof ActionWrapper actionWithParentGroup) {
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
          if (isSelected && presentation.getSelectedIcon() != null) {
            icon = presentation.getSelectedIcon();
          }
          panel.setIcon(createLayeredIcon(icon, disabled));
        }

        if (toggle) {
          addOnOffButton(panel, Toggleable.isSelected(presentation));
        }
        else if (groupName != null) {
          JLabel groupLabel = new JLabel(groupName);
          groupLabel.setBackground(bg);
          groupLabel.setBorder(eastBorder);
          groupLabel.setForeground(groupFg);
          panel.setRight(groupLabel);
        }

        if (anAction instanceof PromoAction promoAction) {
          customizePromoAction(promoAction, bg, eastBorder, groupFg, panel);
        }

        panel.setToolTipText(presentation.getDescription());
        @NlsSafe String actionId = ActionManager.getInstance().getId(anAction);
        Shortcut[] shortcuts = KeymapUtil.getActiveKeymapShortcuts(actionId).getShortcuts();
        String shortcutText = KeymapUtil.getPreferredShortcutText(shortcuts);
        String text = ActionPresentationDecorator.decorateTextIfNeeded(anAction, presentation.getText());
        String name = getName(text, groupName, toggle);
        name = cutName(name, shortcutText, list, panel);

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
            descriptorBg = JBUI.CurrentTheme.BigPopup.LIST_SETTINGS_BACKGROUND;
          }
          panel.setBackground(descriptorBg);
          nameComponent.setBackground(descriptorBg);
        }
        String hit = calcHit((OptionDescription)value);
        Color fg = UIUtil.getListForeground(isSelected, cellHasFocus);

        if (showIcon) {
          panel.setIcon(EMPTY_ICON);
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
          panel.setRight(settingsLabel);
        }

        String name = cutName(hit, null, list, panel);
        appendWithColoredMatches(nameComponent, name, pattern, fg, isSelected);
      }
      return panel;
    }

    private static void customizePromoAction(PromoAction promoAction,
                                             Color panelBackground,
                                             Border eastBorder,
                                             Color groupFg,
                                             IconCompOptionalCompPanel<SimpleColoredComponent> panel) {
      SimpleColoredComponent promo = new SimpleColoredComponent();
      promo.setBackground(panelBackground);
      promo.setForeground(groupFg);
      promo.setIcon(AllIcons.Ide.External_link_arrow);
      promo.setIconOnTheRight(true);
      promo.setTransparentIconBackground(true);
      promo.append(promoAction.getCallToAction());

      SimpleColoredComponent upgradeTo = new SimpleColoredComponent();
      upgradeTo.setIcon(promoAction.getPromotedProductIcon());
      upgradeTo.setBackground(panelBackground);
      upgradeTo.setForeground(groupFg);
      upgradeTo.setIconOnTheRight(true);
      upgradeTo.append(FeaturePromoBundle.message("get.prefix") + " ");
      upgradeTo.setTransparentIconBackground(true);

      BorderLayoutPanel compositeUpgradeHint = JBUI.Panels.simplePanel(promo);
      if (promoAction.getPromotedProductIcon() != null) {
        compositeUpgradeHint.addToLeft(upgradeTo);
      }

      compositeUpgradeHint.andTransparent();

      compositeUpgradeHint.setBorder(eastBorder);

      panel.setRight(compositeUpgradeHint);
    }

    public static @ActionText @NotNull String calcHit(@NotNull OptionDescription value) {
      if (value instanceof RegistryTextOptionDescriptor) {
        return value.getHit() + " = " + value.getValue();
      }
      String hit = StringUtil.defaultIfEmpty(value.getHit(), value.getOption());
      return StringUtil.unescapeXmlEntities(StringUtil.notNullize(hit))
        .replace(BundleBase.MNEMONIC_STRING, "")
        .replace("  ", " "); // avoid extra spaces from mnemonics and xml conversion
    }

    private static @ActionText String cutName(@ActionText String name,
                                              @NlsSafe String shortcutText,
                                              JList<?> list,
                                              IconCompOptionalCompPanel<SimpleColoredComponent> panel) {
      if (!list.isShowing() || list.getWidth() <= 0) {
        return StringUtil.first(name, 60, true); // fallback to previous behaviour
      }

      //we cannot cut HTML formatted strings
      if (name.startsWith("<html>")) return name;

      // we have a min size for SE, which is ~40 symbols, don't spend time for trimming, let's use a shortcut
      if (name.length() < 40) return name;

      int freeSpace = calcFreeSpace(list, panel, shortcutText);

      if (freeSpace <= 0) {
        return name;
      }

      SimpleColoredComponent nameComponent = panel.getCenter();
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

    private static int calcFreeSpace(JList<?> list, IconCompOptionalCompPanel<SimpleColoredComponent> panel, String shortcutText) {
      SimpleColoredComponent nameComponent = panel.getCenter();
      Insets insets = nameComponent.getInsets();
      Insets ipad = nameComponent.getIpad();
      int freeSpace = list.getWidth()
                      - (list.getInsets().right + list.getInsets().left)
                      - panel.calculateNonResizeableWidth()
                      - (insets.right + insets.left)
                      - (ipad.right + ipad.left);

      if (StringUtil.isNotEmpty(shortcutText)) {
        FontMetrics fm = nameComponent.getFontMetrics(nameComponent.getFont().deriveFont(Font.BOLD));
        freeSpace -= fm.stringWidth(" " + shortcutText);
      }

      return freeSpace;
    }

    private static void addOnOffButton(@NotNull IconCompOptionalCompPanel<SimpleColoredComponent> panel, boolean selected) {
      OnOffButton button = new OnOffButton();
      button.setSelected(selected);
      panel.setRight(button);
      panel.setBorder(TOGGLE_BUTTON_BORDER);
    }

    private static @ActionText @NotNull String getName(@Nullable @ActionText String text, @Nullable @ActionText String groupName, boolean toggle) {
      if (text != null && text.startsWith("<html>") && text.endsWith("</html>")) {
        String rawText = text.substring(6, text.length() - 7);
        return "<html>" + getName(rawText, groupName, toggle) + "</html>";
      }
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
        new HtmlToSimpleColoredComponentConverter(HtmlToSimpleColoredComponentConverter.DEFAULT_TAG_HANDLER).appendHtml(nameComponent, name,
                                                                                                                        plain);
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
