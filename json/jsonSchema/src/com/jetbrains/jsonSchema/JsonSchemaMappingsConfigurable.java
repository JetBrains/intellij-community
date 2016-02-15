package com.jetbrains.jsonSchema;

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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaReader;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Irina.Chernushina on 2/2/2016.
 */
public class JsonSchemaMappingsConfigurable extends MasterDetailsComponent implements SearchableConfigurable, Disposable {
  @NonNls public static final String SETTINGS_JSON_SCHEMA = "settings.json.schema";
  public static final String JSON_SCHEMA_MAPPINGS = "JSON Schema";

  private final static Comparator<JsonSchemaMappingsConfigurationBase.SchemaInfo> COMPARATOR = new Comparator<JsonSchemaMappingsConfigurationBase.SchemaInfo>() {
    @Override
    public int compare(JsonSchemaMappingsConfigurationBase.SchemaInfo o1, JsonSchemaMappingsConfigurationBase.SchemaInfo o2) {
      if (o1.isApplicationLevel() != o2.isApplicationLevel()) {
        return o1.isApplicationLevel() ? -1 : 1;
      }
      return o1.getName().compareToIgnoreCase(o2.getName());
    }
  };
  public static final String READ_JSON_SCHEMA = "Read JSON Schema";
  public static final String ADD_PROJECT_SCHEMA = "Add Project Schema";

  @Nullable
  private Project myProject;
  private Runnable myTreeUpdater = new Runnable() {
    @Override
    public void run() {
      TREE_UPDATER.run();
    }
  };
  /*
* variants:
* 1) $schema
* 2) drop down intention with already known schemas
* 3) external - (+by uri?) - settings for editing
*
* */

  public JsonSchemaMappingsConfigurable(@Nullable final Project project) {
    myProject = project;
    initTree();
    //final JsonSchemaMappingsApplicationConfiguration applicationConfiguration = JsonSchemaMappingsApplicationConfiguration.getInstance();
    //applicationConfiguration.recheck();
    fillTree();
  }

  @Nullable
  @Override
  protected ArrayList<AnAction> createActions(boolean fromPopup) {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(new DumbAwareAction("Add", "Add", IconUtil.getAddIcon()) {
      {
        registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
      }
      @Override
      public void actionPerformed(AnActionEvent e) {
        /*if (myProject == null) {
          importSchema();
          return;
        }

        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new DumbAwareAction("Import Schema", "Add schema to be stored internally and available for all projects", null) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            importSchema();
          }
        });
        group.add(new DumbAwareAction(ADD_PROJECT_SCHEMA, ADD_PROJECT_SCHEMA, null) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            addProjectSchema();
          }
        });
        final ListPopup popup = PopupFactoryImpl.getInstance().
          createActionGroupPopup(null, group, e.getDataContext(), false, true, true, null, -1, null);
        if (e.getInputEvent() instanceof MouseEvent) {
          final MouseEvent mouseEvent = (MouseEvent)e.getInputEvent();
          if (mouseEvent.getXOnScreen() == 0 && mouseEvent.getYOnScreen() == 0) {
            popup.showInBestPositionFor(e.getDataContext());
          } else {
            popup.show(new RelativePoint(mouseEvent));
          }
        } else {
          popup.showInBestPositionFor(e.getDataContext());
        }*/
        addProjectSchema();
      }
    });
    result.add(new MyDeleteAction());
    return result;
  }

  private void addProjectSchema() {
    final VirtualFile file =
      FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(), myProject, null);
    if (file != null) {
      final String relativePath = VfsUtil.getRelativePath(file, myProject.getBaseDir());
      if (relativePath == null) {
        Messages.showErrorDialog(myProject, "Please select file under project root or import schema.", ADD_PROJECT_SCHEMA);
        return;
      }
      final JsonSchemaChecker importer = new JsonSchemaChecker(file, true);
      if (!importer.checkSchemaFile()) {
        if (!StringUtil.isEmptyOrSpaces(importer.getError())) {
          Messages.showErrorDialog(myProject, importer.getError(), READ_JSON_SCHEMA);
        }
        return;
      }

      addCreatedMappings(file,
                         new JsonSchemaMappingsConfigurationBase.SchemaInfo(file.getNameWithoutExtension(), relativePath, false, null));
    }
  }

 /* private void importSchema() {
    final VirtualFile file =
      FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(), myProject, null);
    if (file != null) {
      final Pair<Boolean, String> pair = importFromFile(myProject, file);
      if (Boolean.TRUE.equals(pair.getFirst())) {
        addCreatedMappings(new JsonSchemaMappingsConfigurationBase.SchemaInfo(file.getNameWithoutExtension(), pair.getSecond(), true, null));
      }
    }
  }

  private static Pair<Boolean, String> importFromFile(@Nullable final Project project, @NotNull final VirtualFile file) {
    final JsonSchemaChecker importer = new JsonSchemaChecker(file, true);
    if (!importer.checkSchemaFile()) {
      if (!StringUtil.isEmptyOrSpaces(importer.getError())) {
        Messages.showErrorDialog(project, importer.getError(), READ_JSON_SCHEMA);
      } else Messages.showErrorDialog(project, "Can not import JSON Schema from " + file.getPath(), READ_JSON_SCHEMA);
      return Pair.create(false, null);
    }

    try {
      String newName = file.getName();
      final File ioSchemaFolder = JsonSchemaMappingsApplicationConfiguration.getInstance().getSchemaFolder();
      final VirtualFile schemaFolder = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioSchemaFolder);
      if (schemaFolder == null) {
        Messages.showErrorDialog(project, "Can not create folder to import JSON Schema into in: " + ioSchemaFolder.getPath(), "Import JSON Schema");
        return Pair.create(false, null);
      }
      for (int i = 0; i < 1000; i++) {
        if (schemaFolder.findChild(newName) == null) break;
        newName = file.getNameWithoutExtension() + i + ".json";
      }
      final File ioCopy = new File(ioSchemaFolder, newName);
      FileUtil.copy(new File(file.getPath()), ioCopy);

      return Pair.create(true, newName);
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, e.getMessage(), "Import JSON Schema");
      return Pair.create(false, null);
    }
  }*/

  private void addCreatedMappings(@NotNull VirtualFile schemaFile, @NotNull final JsonSchemaMappingsConfigurationBase.SchemaInfo info) {
    final MyNode node = new MyNode(new JsonSchemaConfigurable(myProject, schemaFile, info, myTreeUpdater), info.isApplicationLevel());
    addNode(node, myRoot);
    selectNodeInTree(node, true);
  }

  private void fillTree() {
    myRoot.removeAllChildren();

    if (myProject.isDefault()) return;

    final List<JsonSchemaMappingsConfigurationBase.SchemaInfo> list = getStoredList();
    for (JsonSchemaMappingsConfigurationBase.SchemaInfo info : list) {
      final String[] parts = info.getRelativePathToSchema().replace('\\', '/').split("/");
      final VirtualFile schemaFile = VfsUtil.findRelativeFile(myProject.getBaseDir(), parts);
      if (schemaFile != null) {
        myRoot.add(new MyNode(new JsonSchemaConfigurable(myProject, schemaFile, info, myTreeUpdater), info.isApplicationLevel()));
      }
    }
    ((DefaultTreeModel) myTree.getModel()).reload(myRoot);
    if (myRoot.children().hasMoreElements()) {
      myTree.addSelectionRow(0);
    }
  }

  @NotNull
  private List<JsonSchemaMappingsConfigurationBase.SchemaInfo> getStoredList() {
    final List<JsonSchemaMappingsConfigurationBase.SchemaInfo> list = new ArrayList<JsonSchemaMappingsConfigurationBase.SchemaInfo>();
    //final JsonSchemaMappingsApplicationConfiguration applicationConfiguration = JsonSchemaMappingsApplicationConfiguration.getInstance();
    //final Map<String, JsonSchemaMappingsConfigurationBase.SchemaInfo> state = applicationConfiguration.getStateMap();
    //if (state != null) {
    //  for (JsonSchemaMappingsConfigurationBase.SchemaInfo info : state.values()) {
    //    list.add(info);
    //  }
    //}
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
    final Map<String, JsonSchemaMappingsConfigurationBase.SchemaInfo> appMap = new HashMap<String, JsonSchemaMappingsConfigurationBase.SchemaInfo>();
    final Map<String, JsonSchemaMappingsConfigurationBase.SchemaInfo> projectMap = new HashMap<String, JsonSchemaMappingsConfigurationBase.SchemaInfo>();
    for (JsonSchemaMappingsConfigurationBase.SchemaInfo info : uiList) {
      if (info.isApplicationLevel()) {
        appMap.put(info.getName(), info);
      } else {
        projectMap.put(info.getName(), info);
      }
    }

    //JsonSchemaMappingsApplicationConfiguration.getInstance().setState(appMap);
    if (myProject != null) {
      JsonSchemaMappingsProjectConfiguration.getInstance(myProject).setState(projectMap);
    }
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      final JsonSchemaService service = JsonSchemaService.Impl.get(project);
      if (service != null) service.reset();
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

  @NotNull
  private List<JsonSchemaMappingsConfigurationBase.SchemaInfo> getUiList(boolean applyChildren) throws ConfigurationException {
    final List<JsonSchemaMappingsConfigurationBase.SchemaInfo> uiList = new ArrayList<JsonSchemaMappingsConfigurationBase.SchemaInfo>();
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
  }

  @Override
  protected Comparator<MyNode> getNodeComparator() {
    return new Comparator<MyNode>() {
      @Override
      public int compare(MyNode o1, MyNode o2) {
        if (o1.getConfigurable() instanceof JsonSchemaConfigurable && o2.getConfigurable() instanceof JsonSchemaConfigurable) {
          return COMPARATOR.compare(getSchemaInfo(o1), getSchemaInfo(o2));
        }
        return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
      }
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

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  public static class JsonSchemaChecker {
    private static final int MAX_SCHEMA_LENGTH = FileUtil.MEGABYTE;
    private final boolean myLoadText;
    private final VirtualFile myFile;

    public JsonSchemaChecker(VirtualFile file, boolean loadText) {
      myLoadText = loadText;
      myFile = file;
    }

    @Nullable
    private String myText;
    @Nullable
    private String myError;

    public boolean checkSchemaFile() {
      String text = null;
      try {
        final File ioFile = new File(myFile.getPath());
        final long length = ioFile.length();
        if (length > MAX_SCHEMA_LENGTH) {
          myError = "JSON schema was not loaded from '" + myFile.getName() + "' because it's too large (file size is " + length + " bytes).";
          return false;
        }
        if (length == 0) {
          myError = "JSON schema was not loaded from '" + myFile.getName() + "'. File is empty.";
          return false;
        }
        myText = FileUtil.loadFile(ioFile);
      }
      catch (IOException e1) {
        myError = "Problem during reading JSON schema from '" + myFile.getName() + "': " + e1.getMessage();
        return false;
      }
      if (!JsonSchemaReader.isJsonSchema(myText)) {
        myError = "JSON Schema not found in '" + myFile.getName() + "'";
        return false;
      }
      if (!myLoadText) myText = null;
      return true;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    @Nullable
    public String getText() {
      return myText;
    }

    @Nullable
    public String getError() {
      return myError;
    }
  }
}
