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
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.json.JsonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.CollectConsumer;
import com.intellij.util.IconUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaReader;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.util.*;

/**
 * @author Irina.Chernushina on 2/2/2016.
 */
public class JsonSchemaMappingsConfigurable extends MasterDetailsComponent implements SearchableConfigurable, Disposable {
  @NonNls public static final String SETTINGS_JSON_SCHEMA = "settings.json.schema";
  public static final String JSON_SCHEMA_MAPPINGS = "JSON Schema";

  private final static Comparator<JsonSchemaMappingsConfigurationBase.SchemaInfo> COMPARATOR = (o1, o2) -> {
    if (o1.isApplicationLevel() != o2.isApplicationLevel()) {
      return o1.isApplicationLevel() ? -1 : 1;
    }
    return o1.getName().compareToIgnoreCase(o2.getName());
  };
  public static final String READ_JSON_SCHEMA = "Read JSON Schema";
  public static final String ADD_PROJECT_SCHEMA = "Add Project Schema";
  private String myError;

  @Nullable
  private Project myProject;
  private Runnable myTreeUpdater = () -> {
    TREE_UPDATER.run();
    updateWarningText();
  };

  public JsonSchemaMappingsConfigurable(@Nullable final Project project) {
    myProject = project;
    initTree();
    fillTree();
    updateWarningText();
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

  private void addProjectSchema() {
    final VirtualFile file =
      FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle(
        JsonBundle.message("json.schema.add.schema.chooser.title")), myProject, null);
    if (file != null) {
      final String relativePath = VfsUtil.getRelativePath(file, myProject.getBaseDir());
      if (relativePath == null) {
        Messages.showErrorDialog(myProject, "Please select file under project root.", ADD_PROJECT_SCHEMA);
        return;
      }
      final JsonSchemaChecker importer = new JsonSchemaChecker(myProject, file);
      if (!importer.checkSchemaFile()) {
        if (!StringUtil.isEmptyOrSpaces(importer.getError())) {
          JsonSchemaReader.ERRORS_NOTIFICATION.createNotification(importer.getError(), MessageType.ERROR).notify(myProject);
          Messages.showErrorDialog(myProject, importer.getError(), READ_JSON_SCHEMA);
        }
        return;
      }

      addCreatedMappings(file,
                         new JsonSchemaMappingsConfigurationBase.SchemaInfo(file.getNameWithoutExtension(), relativePath, false, null));
    }
  }

  private void addCreatedMappings(@NotNull VirtualFile schemaFile, @NotNull final JsonSchemaMappingsConfigurationBase.SchemaInfo info) {
    final JsonSchemaConfigurable configurable = new JsonSchemaConfigurable(myProject, FileUtil.toSystemDependentName(schemaFile.getPath()),
                                                                     info, myTreeUpdater);
    configurable.setError(myError);
    final MyNode node = new MyNode(configurable, info.isApplicationLevel());
    addNode(node, myRoot);
    selectNodeInTree(node, true);
  }

  private void fillTree() {
    myRoot.removeAllChildren();

    if (myProject.isDefault()) return;

    final List<JsonSchemaMappingsConfigurationBase.SchemaInfo> list = getStoredList();
    for (JsonSchemaMappingsConfigurationBase.SchemaInfo info : list) {
      final JsonSchemaConfigurable configurable =
        new JsonSchemaConfigurable(myProject, new File(myProject.getBasePath(), info.getRelativePathToSchema()).getPath(),
                                   info, myTreeUpdater);
      configurable.setError(myError);
      myRoot.add(new MyNode(configurable, info.isApplicationLevel()));
    }
    ((DefaultTreeModel) myTree.getModel()).reload(myRoot);
    if (myRoot.children().hasMoreElements()) {
      myTree.addSelectionRow(0);
    }
  }

  @NotNull
  private List<JsonSchemaMappingsConfigurationBase.SchemaInfo> getStoredList() {
    final List<JsonSchemaMappingsConfigurationBase.SchemaInfo> list = new ArrayList<>();
    if (myProject != null) {
      final Map<String, JsonSchemaMappingsConfigurationBase.SchemaInfo> projectState = JsonSchemaMappingsProjectConfiguration
        .getInstance(myProject).getStateMap();
      if (projectState != null) {
        list.addAll(projectState.values());
      }
    }

    Collections.sort(list, COMPARATOR);
    return list;
  }

  @Override
  public void apply() throws ConfigurationException {
    final List<JsonSchemaMappingsConfigurationBase.SchemaInfo> uiList = getUiList(true);
    validate(uiList);
    final Map<String, JsonSchemaMappingsConfigurationBase.SchemaInfo> projectMap = new HashMap<>();
    for (JsonSchemaMappingsConfigurationBase.SchemaInfo info : uiList) {
      if (!info.isApplicationLevel()) {
        projectMap.put(info.getName(), info);
      }
    }

    if (myProject != null) {
      JsonSchemaMappingsProjectConfiguration.getInstance(myProject).setState(projectMap);
    }
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      final JsonSchemaService service = JsonSchemaService.Impl.get(project);
      if (service != null) service.reset();
    }
    if (myProject != null) {
      DaemonCodeAnalyzer.getInstance(myProject).restart();
      EditorNotifications.getInstance(myProject).updateAllNotifications();
    }
  }

  private static void validate(@NotNull List<JsonSchemaMappingsConfigurationBase.SchemaInfo> list) throws ConfigurationException {
    final Set<String> set = new HashSet<>();
    for (JsonSchemaMappingsConfigurationBase.SchemaInfo info : list) {
      if (set.contains(info.getName())) {
        throw new ConfigurationException("Duplicate schema name: '" + info.getName() + "'");
      }
      set.add(info.getName());
    }
  }

  @Override
  public boolean isModified() {
    final List<JsonSchemaMappingsConfigurationBase.SchemaInfo> storedList = getStoredList();
    final List<JsonSchemaMappingsConfigurationBase.SchemaInfo> uiList;
    try {
      uiList = getUiList(false);
    }
    catch (ConfigurationException e) {
      //will not happen
      return false;
    }
    return !storedList.equals(uiList);
  }

  private void updateWarningText() {
    final MultiMap<String, JsonSchemaMappingsConfigurationBase.Item> patternsMap = new MultiMap<>();
    final StringBuilder sb = new StringBuilder();
    final List<JsonSchemaMappingsConfigurationBase.SchemaInfo> list;
    try {
      list = getUiList(false);
    }
    catch (ConfigurationException e) {
      // will not happen
      return;
    }
    for (JsonSchemaMappingsConfigurationBase.SchemaInfo info : list) {
      final JsonSchemaPatternComparator comparator = new JsonSchemaPatternComparator(myProject);
      final List<JsonSchemaMappingsConfigurationBase.Item> patterns = info.getPatterns();
      for (JsonSchemaMappingsConfigurationBase.Item pattern : patterns) {
        for (Map.Entry<String, Collection<JsonSchemaMappingsConfigurationBase.Item>> entry : patternsMap.entrySet()) {
          for (JsonSchemaMappingsConfigurationBase.Item item : entry.getValue()) {
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
        ((JsonSchemaConfigurable) ((MyNode)o).getConfigurable()).setError(myError);
      }
    }
  }

  @NotNull
  private List<JsonSchemaMappingsConfigurationBase.SchemaInfo> getUiList(boolean applyChildren) throws ConfigurationException {
    final List<JsonSchemaMappingsConfigurationBase.SchemaInfo> uiList = new ArrayList<>();
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
    updateWarningText();
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

  private static JsonSchemaMappingsConfigurationBase.SchemaInfo getSchemaInfo(@NotNull final MyNode node) {
    return ((JsonSchemaConfigurable) node.getConfigurable()).getSchema();
  }

  @Override
  protected void processRemovedItems() {

  }

  @Override
  protected boolean wasObjectStored(Object editableObject) {
    return false;
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

  public static class JsonSchemaChecker {
    private static final int MAX_SCHEMA_LENGTH = FileUtil.MEGABYTE;
    private final Project myProject;
    private final VirtualFile myFile;

    public JsonSchemaChecker(Project project, VirtualFile file) {
      myProject = project;
      myFile = file;
    }

    @Nullable
    private String myError;

    public boolean checkSchemaFile() {
      final long length = myFile.getLength();
      if (length > MAX_SCHEMA_LENGTH) {
        myError = "JSON schema was not loaded from '" + myFile.getName() + "' because it's too large (file size is " + length + " bytes).";
        return false;
      }
      if (length == 0) {
        myError = "JSON schema was not loaded from '" + myFile.getName() + "'. File is empty.";
        return false;
      }
      final CollectConsumer<String> collectConsumer = new CollectConsumer<>();
      final JsonSchemaService service = JsonSchemaService.Impl.get(myProject);
      if (service != null && !service.isSchemaFile(myFile, collectConsumer)) {
        myError = "JSON Schema not found or contain error in '" + myFile.getName() + "'";
        if (!collectConsumer.getResult().isEmpty()) {
          myError += ": " + StringUtil.join(collectConsumer.getResult(), "; ");
        }
        return false;
      }
      return true;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    @Nullable
    public String getError() {
      return myError;
    }
  }
}
