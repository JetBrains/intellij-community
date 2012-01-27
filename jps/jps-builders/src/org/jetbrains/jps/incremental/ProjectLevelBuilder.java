package org.jetbrains.jps.incremental;

/**
 * @author nik
 */
public abstract class ProjectLevelBuilder extends Builder {
  private final ProjectLevelBuilderCategory myCategory;

  protected ProjectLevelBuilder(ProjectLevelBuilderCategory category) {
    myCategory = category;
  }

  public abstract void build(CompileContext context);

  public ProjectLevelBuilderCategory getCategory() {
    return myCategory;
  }

  public static enum ProjectLevelBuilderCategory { TRANSLATOR, PACKAGER }
}
