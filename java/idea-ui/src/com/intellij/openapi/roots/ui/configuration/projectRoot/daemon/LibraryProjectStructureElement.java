package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.*;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class LibraryProjectStructureElement extends ProjectStructureElement {
  private final Library myLibrary;

  public LibraryProjectStructureElement(@NotNull StructureConfigurableContext context, @NotNull Library library) {
    super(context);
    myLibrary = library;
  }

  public Library getLibrary() {
    return myLibrary;
  }

  @Override
  public void check(ProjectStructureProblemsHolder problemsHolder) {
    if (((LibraryEx)myLibrary).isDisposed()) return;
    final LibraryEx library = (LibraryEx)myContext.getLibraryModel(myLibrary);
    if (library == null || library.isDisposed()) return;

    reportInvalidRoots(problemsHolder, library, OrderRootType.CLASSES, "classes", ProjectStructureProblemType.error("library-invalid-classes-path"));
    reportInvalidRoots(problemsHolder, library, OrderRootType.SOURCES, "sources", ProjectStructureProblemType.warning("library-invalid-source-javadoc-path"));
    reportInvalidRoots(problemsHolder, library, JavadocOrderRootType.getInstance(), "javadoc", ProjectStructureProblemType.warning("library-invalid-source-javadoc-path"));
  }

  private void reportInvalidRoots(ProjectStructureProblemsHolder problemsHolder, LibraryEx library,
                                  final OrderRootType type, String rootName, final ProjectStructureProblemType problemType) {
    final List<String> invalidUrls = library.getInvalidRootUrls(type);
    if (!invalidUrls.isEmpty()) {
      final String description = createInvalidRootsDescription(invalidUrls, rootName, library.getName());
      final PlaceInProjectStructure place = createPlace();
      final String message = ProjectBundle.message("project.roots.error.message.invalid.roots", rootName, invalidUrls.size());
      ProjectStructureProblemDescription.ProblemLevel level = library.getTable().getTableLevel().equals(LibraryTablesRegistrar.PROJECT_LEVEL)
                                                              ? ProjectStructureProblemDescription.ProblemLevel.PROJECT : ProjectStructureProblemDescription.ProblemLevel.GLOBAL;
      problemsHolder.registerProblem(new ProjectStructureProblemDescription(message, description, place,
                                                                            problemType, level,
                                                                            Collections.singletonList(new RemoveInvalidRootsQuickFix(library, type, invalidUrls)),
                                                                            true));
    }
  }

  private static String createInvalidRootsDescription(List<String> invalidClasses, String rootName, String libraryName) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<html>");
    buffer.append("Library '").append(StringUtil.escapeXml(libraryName)).append("' has broken " + rootName + " " + StringUtil.pluralize("path", invalidClasses.size()) + ":");
    for (String url : invalidClasses) {
      buffer.append("<br>&nbsp;&nbsp;");
      buffer.append(PathUtil.toPresentableUrl(url));
    }
    buffer.append("</html>");
    return buffer.toString();
  }

  @NotNull
  private PlaceInProjectStructure createPlace() {
    final Project project = myContext.getProject();
    return new PlaceInProjectStructureBase(project, ProjectStructureConfigurable.getInstance(project).createProjectOrGlobalLibraryPlace(myLibrary), this);
  }

  @Override
  public List<ProjectStructureElementUsage> getUsagesInElement() {
    return Collections.emptyList();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LibraryProjectStructureElement)) return false;

    return getSourceOrThis() == (((LibraryProjectStructureElement)o).getSourceOrThis());
  }

  public ActionCallback navigate() {
    return createPlace().navigate();
  }

  @NotNull
  private Library getSourceOrThis() {
    final InvocationHandler invocationHandler = Proxy.isProxyClass(myLibrary.getClass()) ? Proxy.getInvocationHandler(myLibrary) : null;
    final Library realLibrary = invocationHandler instanceof ModuleEditor.ProxyDelegateAccessor ?
                                (Library)((ModuleEditor.ProxyDelegateAccessor)invocationHandler).getDelegate() : myLibrary;
    final Library source = realLibrary instanceof LibraryImpl? ((LibraryImpl)realLibrary).getSource() : null;
    return source != null ? source : myLibrary;
  }
  
  @Override
  public int hashCode() {
    return System.identityHashCode(getSourceOrThis());
  }

  @Override
  public boolean shouldShowWarningIfUnused() {
    final LibraryTable libraryTable = myLibrary.getTable();
    if (libraryTable == null) return false;
    return LibraryTablesRegistrar.PROJECT_LEVEL.equals(libraryTable.getTableLevel())
        || LibraryTablesRegistrar.APPLICATION_LEVEL.equals(libraryTable.getTableLevel()) && !ApplicationLibraryTable.getApplicationTable().isUsedInOtherProjects(myLibrary, myContext.getProject());
  }

  @Override
  public ProjectStructureProblemDescription createUnusedElementWarning() {
    final List<ConfigurationErrorQuickFix> fixes = Arrays.asList(new AddLibraryToDependenciesFix(), new RemoveLibraryFix());
    return new ProjectStructureProblemDescription("Library '" + StringUtil.escapeXml(myLibrary.getName()) + "'" + " is not used", null, createPlace(),
                                                  ProjectStructureProblemType.unused("unused-library"), ProjectStructureProblemDescription.ProblemLevel.PROJECT,
                                                  fixes, false);
  }

  @Override
  public String getPresentableName() {
    return "Library '" + myLibrary.getName() + "'";
  }

  @Override
  public String getId() {
    return "library:" + myLibrary.getTable().getTableLevel() + ":" + myLibrary.getName();
  }

  private class RemoveInvalidRootsQuickFix extends ConfigurationErrorQuickFix {
    private final Library myLibrary;
    private final OrderRootType myType;
    private final List<String> myInvalidUrls;

    public RemoveInvalidRootsQuickFix(Library library, OrderRootType type, List<String> invalidUrls) {
      super("Remove invalid " + StringUtil.pluralize("root", invalidUrls.size()));
      myLibrary = library;
      myType = type;
      myInvalidUrls = invalidUrls;
    }

    @Override
    public void performFix() {
      final LibraryTable.ModifiableModel libraryTable = myContext.getModifiableLibraryTable(myLibrary.getTable());
      if (libraryTable instanceof LibrariesModifiableModel) {
        for (String invalidRoot : myInvalidUrls) {
          final ExistingLibraryEditor libraryEditor = ((LibrariesModifiableModel)libraryTable).getLibraryEditor(myLibrary);
          libraryEditor.removeRoot(invalidRoot, myType);
        }
        myContext.getDaemonAnalyzer().queueUpdate(LibraryProjectStructureElement.this);
        final ProjectStructureConfigurable structureConfigurable = ProjectStructureConfigurable.getInstance(myContext.getProject());
        navigate().doWhenDone(new Runnable() {
          @Override
          public void run() {
            final NamedConfigurable configurable = structureConfigurable.getConfigurableFor(myLibrary).getSelectedConfugurable();
            if (configurable instanceof LibraryConfigurable) {
              ((LibraryConfigurable)configurable).updateComponent();
            }
          }
        });
      }
    }
  }

  private class AddLibraryToDependenciesFix extends ConfigurationErrorQuickFix {
    private AddLibraryToDependenciesFix() {
      super("Add to Dependencies...");
    }

    @Override
    public void performFix() {
      LibraryEditingUtil.showDialogAndAddLibraryToDependencies(myLibrary, myContext.getProject());
    }
  }

  private class RemoveLibraryFix extends ConfigurationErrorQuickFix {
    private RemoveLibraryFix() {
      super("Remove Library");
    }

    @Override
    public void performFix() {
      BaseLibrariesConfigurable.getInstance(myContext.getProject(), myLibrary.getTable().getTableLevel()).removeLibrary(LibraryProjectStructureElement.this);
    }
  }
}
