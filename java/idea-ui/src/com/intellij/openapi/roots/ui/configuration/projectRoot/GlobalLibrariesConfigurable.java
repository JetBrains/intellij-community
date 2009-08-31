package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@State(
  name = "GlobalLibrariesConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class GlobalLibrariesConfigurable extends BaseLibrariesConfigurable {

  public GlobalLibrariesConfigurable(final Project project) {
    super(project);
    myLevel = LibraryTablesRegistrar.APPLICATION_LEVEL;
  }

  @Nls
  public String getDisplayName() {
    return "Global Libraries";
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @NonNls
  public String getId() {
    return "global.libraries";
  }


  public static GlobalLibrariesConfigurable getInstance(final Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, GlobalLibrariesConfigurable.class);
  }

  public LibraryTableModifiableModelProvider getModelProvider(final boolean editable) {
    return myContext.getGlobalLibrariesProvider(editable);
  }

  public BaseLibrariesConfigurable getOppositeGroup() {
    return ProjectLibrariesConfigurable.getInstance(myProject);
  }

  protected String getAddText() {
    return ProjectBundle.message("add.new.global.library.text");
  }
}