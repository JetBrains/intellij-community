package com.intellij.openapi.externalSystem.model.project;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 2/7/12 11:18 AM
 */
public class ProjectEntityType {

  @NotNull public static final ProjectEntityType PROJECT            = new ProjectEntityType("PROJECT", getProjectIcon());
  @NotNull public static final ProjectEntityType MODULE             = new ProjectEntityType("MODULE", AllIcons.Nodes.Module);
  @NotNull public static final ProjectEntityType MODULE_DEPENDENCY  = new ProjectEntityType("MODULE_DEPENDENCY", MODULE.getIcon());
  @NotNull public static final ProjectEntityType LIBRARY            = new ProjectEntityType("LIBRARY", AllIcons.Nodes.PpLib);
  @NotNull public static final ProjectEntityType LIBRARY_DEPENDENCY = new ProjectEntityType("LIBRARY_DEPENDENCY", LIBRARY.getIcon());
  @NotNull public static final ProjectEntityType CONTENT_ROOT       = new ProjectEntityType("CONTENT_ROOT", null);
  @NotNull public static final ProjectEntityType SYNTHETIC          = new ProjectEntityType("SYNTHETIC", null);
  @NotNull public static final ProjectEntityType JAR                = new ProjectEntityType("JAR", AllIcons.FileTypes.Archive);

  @NotNull public static final ProjectEntityType DEPENDENCY_TO_OUTDATED_LIBRARY = new ProjectEntityType(
    "DEPENDENCY_TO_OUTDATED_LIBRARY", LIBRARY.getIcon()
  );

  @NotNull private final  String myId;
  @Nullable private final Icon   myIcon;

  ProjectEntityType(@NotNull String id, @Nullable Icon icon) {
    myId = id;
    myIcon = icon;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProjectEntityType type = (ProjectEntityType)o;

    if (!myId.equals(type.myId)) return false;

    return true;
  }

  @Override
  public String toString() {
    return myId;
  }

  @NotNull
  private static Icon getProjectIcon() {
    try {
      return IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getSmallIconUrl());
    }
    catch (Exception e) {
      // Control flow may reach this place if we run tests and platform IoC has not been initialised.
      return IconLoader.getIcon("/nodes/ideProject.png");
    }
  }
}
