package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.module.Module;
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
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.*;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    //final String libraryName = library.getName();
    reportInvalidRoots(problemsHolder, library, OrderRootType.CLASSES, ProjectStructureProblemType.error("library-invalid-classes-path"));
    reportInvalidRoots(problemsHolder, library, OrderRootType.SOURCES, ProjectStructureProblemType.warning("library-invalid-source-javadoc-path"));
    reportInvalidRoots(problemsHolder, library, JavadocOrderRootType.getInstance(), ProjectStructureProblemType.warning("library-invalid-source-javadoc-path"));
    //if (!invalidClasses.isEmpty()) {
    //  final String description = createInvalidRootsDescription(invalidClasses, libraryName);
    //  problemsHolder.registerProblem(ProjectBundle.message("project.roots.error.message.invalid.classes.roots", invalidClasses.size()), description, ProjectStructureProblemType.error("library-invalid-classes-path"),
    //                                 createPlace(),
    //                                 new RemoveInvalidRootsQuickFix(Collections.singletonMap(OrderRootType.CLASSES, invalidClasses),
    //                                                                library));
    //}
    //final List<String> invalidJavadocs = library.getInvalidRootUrls(JavadocOrderRootType.getInstance());
    //final List<String> invalidSources = library.getInvalidRootUrls(OrderRootType.SOURCES);
    //if (!invalidJavadocs.isEmpty() || !invalidSources.isEmpty()) {
    //  final Map<OrderRootType, List<String>> invalidRoots = new HashMap<OrderRootType, List<String>>();
    //  invalidRoots.put(OrderRootType.SOURCES, invalidSources);
    //  invalidRoots.put(JavadocOrderRootType.getInstance(), invalidJavadocs);
    //  final String description = createInvalidRootsDescription(ContainerUtil.concat(invalidJavadocs, invalidSources), libraryName);
    //  problemsHolder.registerProblem(ProjectBundle.message("project.roots.error.message.invalid.source.javadoc.roots", invalidJavadocs.size()+invalidSources.size()), description,
    //                                 ProjectStructureProblemType.warning("library-invalid-source-javadoc-path"),
    //                                 createPlace(),
    //                                 new RemoveInvalidRootsQuickFix(invalidRoots, library));
    //}
  }

  private void reportInvalidRoots(ProjectStructureProblemsHolder problemsHolder,
                                  LibraryEx library,
                                  final OrderRootType type, final ProjectStructureProblemType problemType) {
    final List<String> invalidClasses = library.getInvalidRootUrls(type);
    for (String url : invalidClasses) {
      problemsHolder.registerProblem("invalid path '" + url + "'", null, problemType,
                                     createPlace(), new RemoveInvalidRootsQuickFix(Collections.singletonMap(type, Collections.singletonList(url)), library));
    }
  }

  private static String createInvalidRootsDescription(List<String> invalidClasses, String libraryName) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<html>");
    buffer.append("Library '").append(libraryName).append("' has broken paths:");
    for (String url : invalidClasses) {
      buffer.append("<br>&nbsp;&nbsp;");
      buffer.append(VfsUtil.urlToPath(url));
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
    return new ProjectStructureProblemDescription(getPresentableName() + " is not used", null, createPlace(), fixes,
                                                  ProjectStructureProblemType.unused("unused-library"));
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
    private final Map<OrderRootType, List<String>> myInvalidRoots;
    private final Library myLibrary;

    public RemoveInvalidRootsQuickFix(Map<OrderRootType, List<String>> invalidRoots, Library library) {
      super("Remove invalid roots");
      myInvalidRoots = invalidRoots;
      myLibrary = library;
    }

    @Override
    public void performFix() {
      final LibraryTable.ModifiableModel libraryTable = myContext.getModifiableLibraryTable(myLibrary.getTable());
      if (libraryTable instanceof LibrariesModifiableModel) {
        for (OrderRootType rootType : myInvalidRoots.keySet()) {
          for (String invalidRoot : myInvalidRoots.get(rootType)) {
            final ExistingLibraryEditor libraryEditor = ((LibrariesModifiableModel)libraryTable).getLibraryEditor(myLibrary);
            libraryEditor.removeRoot(invalidRoot, rootType);
          }
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
      final Project project = myContext.getProject();
      final ModuleStructureConfigurable moduleStructureConfigurable = ModuleStructureConfigurable.getInstance(project);
      final List<Module> modules = LibraryEditingUtil.getSuitableModules(moduleStructureConfigurable, ((LibraryEx)myLibrary).getType());
      if (modules.isEmpty()) return;
      final ChooseModulesDialog dlg = new ChooseModulesDialog(project, modules, ProjectBundle.message("choose.modules.dialog.title"),
                                                              ProjectBundle
                                                                .message("choose.modules.dialog.description", myLibrary.getName()));
      dlg.show();
      if (dlg.isOK()) {
        final List<Module> chosenModules = dlg.getChosenElements();
        for (Module module : chosenModules) {
          moduleStructureConfigurable.addLibraryOrderEntry(module, myLibrary);
        }
      }
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
