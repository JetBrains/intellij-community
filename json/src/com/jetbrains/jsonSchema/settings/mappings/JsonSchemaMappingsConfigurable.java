// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.settings.mappings;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.util.*;

import static com.jetbrains.jsonSchema.remote.JsonFileResolver.isHttpPath;

/**
 * @author Irina.Chernushina on 2/2/2016.
 */
public class JsonSchemaMappingsConfigurable extends MasterDetailsComponent implements SearchableConfigurable, Disposable {
  @NonNls public static final String SETTINGS_JSON_SCHEMA = "settings.json.schema";
  public static final String JSON_SCHEMA_MAPPINGS = "JSON Schema Mappings";

  private final static Comparator<UserDefinedJsonSchemaConfiguration> COMPARATOR = (o1, o2) -> {
    if (o1.isApplicationDefined() != o2.isApplicationDefined()) {
      return o1.isApplicationDefined() ? 1 : -1;
    }
    return o1.getName().compareToIgnoreCase(o2.getName());
  };
  static final String STUB_SCHEMA_NAME = "New Schema";
  private String myError;

  @NotNull
  private final Project myProject;
  private final TreeUpdater myTreeUpdater = showWarning -> {
    TREE_UPDATER.run();
    updateWarningText(showWarning);
  };

  private final Function<String, String> myNameCreator = s -> createUniqueName(s);

  public JsonSchemaMappingsConfigurable(@NotNull final Project project) {
    myProject = project;
    initTree();
  }

  @Nullable
  @Override
  protected String getEmptySelectionString() {
    return myRoot.children().hasMoreElements() ? "Select JSON Schema to view" :
           "Please add a JSON Schema file and configure its usage";
  }

  @Nullable
  @Override
  protected ArrayList<AnAction> createActions(boolean fromPopup) {
    final ArrayList<AnAction> result = new ArrayList<>();
    result.add(new DumbAwareAction("Add", "Add", IconUtil.getAddIcon()) {
      {
        registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
      }
      @Override
      public void actionPerformed(AnActionEvent e) {
        addProjectSchema();
      }
    });
    result.add(new MyDeleteAction());
    return result;
  }

  public UserDefinedJsonSchemaConfiguration addProjectSchema() {
    UserDefinedJsonSchemaConfiguration configuration = new UserDefinedJsonSchemaConfiguration(createUniqueName(STUB_SCHEMA_NAME),
                                                                                     JsonSchemaVersion.SCHEMA_4, "", false, null);
    addCreatedMappings(configuration);
    return configuration;
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private String createUniqueName(@NotNull String s) {
    int max = -1;
    Enumeration children = myRoot.children();
    while (children.hasMoreElements()) {
      Object element = children.nextElement();
      if (!(element instanceof MyNode)) continue;
      String displayName = ((MyNode)element).getDisplayName();
      if (displayName.startsWith(s)) {
        String lastPart = displayName.substring(s.length()).trim();
        if (lastPart.length() == 0 && max == -1) {
          max = 1;
          continue;
        }
        int i = tryParseInt(lastPart);
        if (i == -1) continue;
        max = i > max ? i : max;
      }
    }
    return max == -1 ? s : (s + " " + (max + 1));
  }

  private static int tryParseInt(@NotNull String s) {
    try {
      return Integer.parseInt(s);
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  private void addCreatedMappings(@NotNull final UserDefinedJsonSchemaConfiguration info) {
    final JsonSchemaConfigurable configurable = new JsonSchemaConfigurable(myProject, "", info, myTreeUpdater, myNameCreator);
    configurable.setError(myError, true);
    final MyNode node = new MyNode(configurable);
    addNode(node, myRoot);
    selectNodeInTree(node, true);
  }

  private void fillTree() {
    myRoot.removeAllChildren();

    if (myProject.isDefault()) return;

    final List<UserDefinedJsonSchemaConfiguration> list = getStoredList();
    for (UserDefinedJsonSchemaConfiguration info : list) {
      String pathToSchema = info.getRelativePathToSchema();
      final JsonSchemaConfigurable configurable =
        new JsonSchemaConfigurable(myProject, isHttpPath(pathToSchema) || new File(pathToSchema).isAbsolute() ? pathToSchema : new File(myProject.getBasePath(), pathToSchema).getPath(),
                                   info, myTreeUpdater, myNameCreator);
      configurable.setError(myError, true);
      myRoot.add(new MyNode(configurable));
    }
    ((DefaultTreeModel) myTree.getModel()).reload(myRoot);
    if (myRoot.children().hasMoreElements()) {
      myTree.addSelectionRow(0);
    }
  }

  @NotNull
  private List<UserDefinedJsonSchemaConfiguration> getStoredList() {
    final List<UserDefinedJsonSchemaConfiguration> list = new ArrayList<>();
    final Map<String, UserDefinedJsonSchemaConfiguration> projectState = JsonSchemaMappingsProjectConfiguration
      .getInstance(myProject).getStateMap();
    if (projectState != null) {
      list.addAll(projectState.values());
    }

    Collections.sort(list, COMPARATOR);
    return list;
  }

  @Override
  public void apply() throws ConfigurationException {
    final List<UserDefinedJsonSchemaConfiguration> uiList = getUiList(true);
    validate(uiList);
    final Map<String, UserDefinedJsonSchemaConfiguration> projectMap = new HashMap<>();
    for (UserDefinedJsonSchemaConfiguration info : uiList) {
      projectMap.put(info.getName(), info);
    }

    JsonSchemaMappingsProjectConfiguration.getInstance(myProject).setState(projectMap);
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      final JsonSchemaService service = JsonSchemaService.Impl.get(project);
      if (service != null) service.reset();
    }
    DaemonCodeAnalyzer.getInstance(myProject).restart();
    EditorNotifications.getInstance(myProject).updateAllNotifications();
  }

  private static void validate(@NotNull List<UserDefinedJsonSchemaConfiguration> list) throws ConfigurationException {
    final Set<String> set = new HashSet<>();
    for (UserDefinedJsonSchemaConfiguration info : list) {
      if (set.contains(info.getName())) {
        throw new ConfigurationException("Duplicate schema name: '" + info.getName() + "'");
      }
      set.add(info.getName());
    }
  }

  @Override
  public boolean isModified() {
    final List<UserDefinedJsonSchemaConfiguration> storedList = getStoredList();
    final List<UserDefinedJsonSchemaConfiguration> uiList;
    try {
      uiList = getUiList(false);
    }
    catch (ConfigurationException e) {
      //will not happen
      return false;
    }
    return !storedList.equals(uiList);
  }

  private void updateWarningText(boolean showWarning) {
    final MultiMap<String, UserDefinedJsonSchemaConfiguration.Item> patternsMap = new MultiMap<>();
    final StringBuilder sb = new StringBuilder();
    final List<UserDefinedJsonSchemaConfiguration> list;
    try {
      list = getUiList(false);
    }
    catch (ConfigurationException e) {
      // will not happen
      return;
    }
    for (UserDefinedJsonSchemaConfiguration info : list) {
      info.refreshPatterns();
      final JsonSchemaPatternComparator comparator = new JsonSchemaPatternComparator(myProject);
      final List<UserDefinedJsonSchemaConfiguration.Item> patterns = info.getPatterns();
      for (UserDefinedJsonSchemaConfiguration.Item pattern : patterns) {
        for (Map.Entry<String, Collection<UserDefinedJsonSchemaConfiguration.Item>> entry : patternsMap.entrySet()) {
          for (UserDefinedJsonSchemaConfiguration.Item item : entry.getValue()) {
            final ThreeState similar = comparator.isSimilar(pattern, item);
            if (ThreeState.NO.equals(similar)) continue;

            if (sb.length() > 0) sb.append('\n');
            sb.append("'").append(pattern.getPresentation()).append("' for schema '")
              .append(info.getName()).append("' and '").append(item.getPresentation()).append("' for schema '").append(entry.getKey())
              .append("'");
          }
        }
      }
      patternsMap.put(info.getName(), patterns);
    }
    if (sb.length() > 0) {
      myError = "Conflicting mappings:\n" + sb.toString();
    } else {
      myError = null;
    }
    final Enumeration children = myRoot.children();
    while (children.hasMoreElements()) {
      Object o = children.nextElement();
      if (o instanceof MyNode && ((MyNode)o).getConfigurable() instanceof JsonSchemaConfigurable) {
        ((JsonSchemaConfigurable) ((MyNode)o).getConfigurable()).setError(myError, showWarning);
      }
    }
  }

  public void selectInTree(UserDefinedJsonSchemaConfiguration configuration) {
    final Enumeration children = myRoot.children();
    while (children.hasMoreElements()) {
      final MyNode node = (MyNode)children.nextElement();
      JsonSchemaConfigurable configurable = (JsonSchemaConfigurable)node.getConfigurable();
      if (Objects.equals(configurable.getUiSchema(), configuration)) {
        selectNodeInTree(node);
      }
    }
  }

  @NotNull
  private List<UserDefinedJsonSchemaConfiguration> getUiList(boolean applyChildren) throws ConfigurationException {
    final List<UserDefinedJsonSchemaConfiguration> uiList = new ArrayList<>();
    final Enumeration children = myRoot.children();
    while (children.hasMoreElements()) {
      final MyNode node = (MyNode)children.nextElement();
      if (applyChildren) {
        node.getConfigurable().apply();
        uiList.add(getSchemaInfo(node));
      }
      else {
        uiList.add(((JsonSchemaConfigurable) node.getConfigurable()).getUiSchema());
      }
    }
    Collections.sort(uiList, COMPARATOR);
    return uiList;
  }

  @Override
  public void reset() {
    fillTree();
    updateWarningText(true);
  }

  @Override
  protected Comparator<MyNode> getNodeComparator() {
    return (o1, o2) -> {
      if (o1.getConfigurable() instanceof JsonSchemaConfigurable && o2.getConfigurable() instanceof JsonSchemaConfigurable) {
        return COMPARATOR.compare(getSchemaInfo(o1), getSchemaInfo(o2));
      }
      return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
    };
  }

  private static UserDefinedJsonSchemaConfiguration getSchemaInfo(@NotNull final MyNode node) {
    return ((JsonSchemaConfigurable) node.getConfigurable()).getSchema();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return JSON_SCHEMA_MAPPINGS;
  }


  @Override
  public void dispose() {
    final Enumeration children = myRoot.children();
    while (children.hasMoreElements()) {
      Object o = children.nextElement();
      if (o instanceof MyNode) {
        ((MyNode)o).getConfigurable().disposeUIResources();
      }
    }
  }

  @NotNull
  @Override
  public String getId() {
    return SETTINGS_JSON_SCHEMA;
  }

  @Override
  public String getHelpTopic() {
    return SETTINGS_JSON_SCHEMA;
  }
}
