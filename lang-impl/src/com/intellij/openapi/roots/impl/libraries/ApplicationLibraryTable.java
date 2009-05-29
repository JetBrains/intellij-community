package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 *  @author dsl
 */
@State(
  name = "libraryTable",
  roamingType = RoamingType.DISABLED,
  storages = {
    @Storage(id = "default", file = "$OPTIONS$/applicationLibraries.xml")
    }
)
public class ApplicationLibraryTable extends LibraryTableBase implements ExportableComponent {
  private static final LibraryTablePresentation GLOBAL_LIBRARY_TABLE_PRESENTATION = new LibraryTablePresentation() {
    public String getDisplayName(boolean plural) {
      return ProjectBundle.message("global.library.display.name", plural ? 2 : 1);
    }

    public String getDescription() {
      return ProjectBundle.message("libraries.node.text.ide");
    }

    public String getLibraryTableEditorTitle() {
      return ProjectBundle.message("library.configure.global.title");
    }
  };

  public static ApplicationLibraryTable getApplicationTable() {
    return ServiceManager.getService(ApplicationLibraryTable.class);
  }

  public String getTableLevel() {
    return LibraryTablesRegistrar.APPLICATION_LEVEL;
  }

  public LibraryTablePresentation getPresentation() {
    return GLOBAL_LIBRARY_TABLE_PRESENTATION;
  }

  public boolean isEditable() {
    return true;
  }

  private static String getExternalFileName() {
    return "applicationLibraries";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(getExternalFileName())};
  }

  @NotNull
  public String getPresentableName() {
    return ProjectBundle.message("library.global.settings");
  }
}
