// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.classpath;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChooseLibrariesFromTablesDialog extends ChooseLibrariesDialogBase {
  private final @Nullable Project myProject;
  private final boolean myShowCustomLibraryTables;

  protected ChooseLibrariesFromTablesDialog(@NotNull @NlsContexts.DialogTitle String title, @NotNull Project project, final boolean showCustomLibraryTables) {
    super(project, title);
    myShowCustomLibraryTables = showCustomLibraryTables;
    myProject = project;
  }

  protected ChooseLibrariesFromTablesDialog(@NotNull JComponent parentComponent,
                                            @NotNull @NlsContexts.DialogTitle String title,
                                            @Nullable Project project,
                                            final boolean showCustomLibraryTables) {
    super(parentComponent, title);
    myShowCustomLibraryTables = showCustomLibraryTables;
    myProject = project;
  }

  public static ChooseLibrariesFromTablesDialog createDialog(@NotNull @NlsContexts.DialogTitle String title,
                                                             @NotNull Project project,
                                                             final boolean showCustomLibraryTables) {
    final ChooseLibrariesFromTablesDialog dialog = new ChooseLibrariesFromTablesDialog(title, project, showCustomLibraryTables);
    dialog.init();
    return dialog;
  }

  @Override
  protected @NotNull Project getProject() {
    if (myProject != null) {
      return myProject;
    }
    return super.getProject();
  }

  @Override
  protected JComponent createNorthPanel() {
    return null;
  }

  @Override
  protected void collectChildren(Object element, List<Object> result) {
    if (element instanceof Application) {
      for (LibraryTable table : getLibraryTables(myProject, myShowCustomLibraryTables)) {
        if (hasLibraries(table)) {
          result.add(table);
        }
      }
    }
    else if (element instanceof LibraryTable) {
      Collections.addAll(result, getLibraries((LibraryTable)element));
    }
  }

  public static List<LibraryTable> getLibraryTables(final Project project, final boolean showCustomLibraryTables) {
    final List<LibraryTable> tables = new ArrayList<>();
    final LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
    if (project != null) {
      tables.add(registrar.getLibraryTable(project));
    }
    tables.add(registrar.getLibraryTable());
    if (showCustomLibraryTables) {
      tables.addAll(registrar.getCustomLibraryTables());
    }
    return tables;
  }

  private boolean hasLibraries(LibraryTable table) {
    final Library[] libraries = getLibraries(table);
    for (Library library : libraries) {
      if (acceptsElement(library)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected int getLibraryTableWeight(@NotNull LibraryTable libraryTable) {
    if (libraryTable.getTableLevel().equals(LibraryTableImplUtil.MODULE_LEVEL)) return 0;
    if (isProjectLibraryTable(libraryTable)) return 1;
    if (isApplicationLibraryTable(libraryTable)) return 2;
    return 3;
  }

  private static boolean isApplicationLibraryTable(LibraryTable libraryTable) {
    return libraryTable.equals(LibraryTablesRegistrar.getInstance().getLibraryTable());
  }

  private boolean isProjectLibraryTable(LibraryTable libraryTable) {
    final LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
    return myProject != null && libraryTable.equals(registrar.getLibraryTable(myProject));
  }

  @Override
  protected boolean isAutoExpandLibraryTable(@NotNull LibraryTable libraryTable) {
    return isApplicationLibraryTable(libraryTable) || isProjectLibraryTable(libraryTable);
  }

  protected Library @NotNull [] getLibraries(@NotNull LibraryTable table) {
    return table.getLibraries();
  }
}
