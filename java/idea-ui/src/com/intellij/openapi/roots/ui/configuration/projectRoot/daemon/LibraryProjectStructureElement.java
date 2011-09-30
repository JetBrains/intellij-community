package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibraryConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
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

    return getSourceOrThis().equals(((LibraryProjectStructureElement)o).getSourceOrThis());
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
    return getSourceOrThis().hashCode();
  }

  @Override
  public boolean highlightIfUnused() {
    final LibraryTable libraryTable = myLibrary.getTable();
    return libraryTable != null && LibraryTablesRegistrar.PROJECT_LEVEL.equals(libraryTable.getTableLevel());
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
        createPlace().navigate().doWhenDone(new Runnable() {
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
}
