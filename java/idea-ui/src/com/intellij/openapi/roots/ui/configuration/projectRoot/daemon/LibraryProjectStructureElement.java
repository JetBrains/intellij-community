// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.BaseLibrariesConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibraryConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.navigation.Place;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;

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
    final String libraryName = library.getName();
    if (libraryName == null || !libraryName.startsWith("Maven: ")) {
      reportInvalidRoots(problemsHolder, library, OrderRootType.SOURCES, "sources",
                         ProjectStructureProblemType.warning("library-invalid-source-javadoc-path"));
      reportInvalidRoots(problemsHolder, library, JavadocOrderRootType.getInstance(), "javadoc",
                         ProjectStructureProblemType.warning("library-invalid-source-javadoc-path"));
    }
  }

  private void reportInvalidRoots(ProjectStructureProblemsHolder problemsHolder, LibraryEx library,
                                  @NotNull OrderRootType type, String rootName, final ProjectStructureProblemType problemType) {
    final List<String> invalidUrls = library.getInvalidRootUrls(type);
    if (!invalidUrls.isEmpty()) {
      final HtmlChunk description = createInvalidRootsDescription(invalidUrls, rootName, library.getName());
      final PlaceInProjectStructure place = createPlace();
      final String message = JavaUiBundle.message("project.roots.error.message.invalid.roots", rootName, invalidUrls.size());
      ProjectStructureProblemDescription.ProblemLevel level = library.getTable().getTableLevel().equals(LibraryTablesRegistrar.PROJECT_LEVEL)
                                                              ? ProjectStructureProblemDescription.ProblemLevel.PROJECT : ProjectStructureProblemDescription.ProblemLevel.GLOBAL;
      problemsHolder.registerProblem(new ProjectStructureProblemDescription(message, description, place,
                                                                            problemType, level,
                                                                            Collections.singletonList(new RemoveInvalidRootsQuickFix(library, type, invalidUrls)),
                                                                            true));
    }
  }

  private static HtmlChunk createInvalidRootsDescription(List<String> invalidClasses, String rootName, @NlsSafe String libraryName) {
    HtmlBuilder buffer = new HtmlBuilder();
    final String name = StringUtil.escapeXmlEntities(libraryName);
    final HtmlChunk.Element link = HtmlChunk.link("http://library/" + name, name);
    buffer.appendRaw(
      JavaUiBundle.message("library.project.structure.invalid.roots.description",
                           link,
                           rootName,
                           invalidClasses.size()
      )
    );
    for (String url : invalidClasses) {
      buffer.br().nbsp(2);
      buffer.append(PathUtil.toPresentableUrl(url));
    }
    return buffer.toFragment();
  }

  @NotNull
  private PlaceInProjectStructure createPlace() {
    final Project project = myContext.getProject();
    Place place = myContext.getModulesConfigurator().getProjectStructureConfigurable().createProjectOrGlobalLibraryPlace(myLibrary);
    return new PlaceInProjectStructureBase(project, place, this);
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
    final Library source = realLibrary instanceof LibraryEx? ((LibraryEx)realLibrary).getSource() : null;
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
    return LibraryTablesRegistrar.PROJECT_LEVEL.equals(libraryTable.getTableLevel());
  }

  @Override
  public ProjectStructureProblemDescription createUnusedElementWarning() {
    final List<ConfigurationErrorQuickFix> fixes = Arrays.asList(new AddLibraryToDependenciesFix(), new RemoveLibraryFix(), new RemoveAllUnusedLibrariesFix());
    final String name = Objects.toString(myLibrary.getName());
    final String libraryName = HtmlChunk.link("http://library/" + name, name).toString();

    final String result = JavaUiBundle.message("library.0.is.not.used", libraryName);
    return new ProjectStructureProblemDescription(result,
                                                  HtmlChunk.empty(),
                                                  createPlace(),
                                                  ProjectStructureProblemType.unused("unused-library"),
                                                  ProjectStructureProblemDescription.ProblemLevel.PROJECT,
                                                  fixes,
                                                  false);
  }

  @Override
  public String getPresentableName() {
    return myLibrary.getName();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) String getTypeName() {
    return JavaUiBundle.message("configurable.library.prefix");
  }

  @Override
  public String getId() {
    return "library:" + myLibrary.getTable().getTableLevel() + ":" + myLibrary.getName();
  }

  private class RemoveInvalidRootsQuickFix extends ConfigurationErrorQuickFix {
    private final Library myLibrary;
    private final OrderRootType myType;
    private final List<String> myInvalidUrls;

    RemoveInvalidRootsQuickFix(Library library, OrderRootType type, List<String> invalidUrls) {
      super(JavaUiBundle.message("label.remove.invalid.roots", invalidUrls.size()));
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
        final ProjectStructureConfigurable structureConfigurable = myContext.getModulesConfigurator().getProjectStructureConfigurable();
        navigate().doWhenDone(() -> {
          final NamedConfigurable configurable = structureConfigurable.getConfigurableFor(myLibrary).getSelectedConfigurable();
          if (configurable instanceof LibraryConfigurable) {
            ((LibraryConfigurable)configurable).updateComponent();
          }
        });
      }
    }
  }

  private final class AddLibraryToDependenciesFix extends ConfigurationErrorQuickFix {
    private AddLibraryToDependenciesFix() {
      super(JavaUiBundle.message("label.add.to.dependencies"));
    }

    @Override
    public void performFix() {
      ProjectStructureValidator.showDialogAndAddLibraryToDependencies(myLibrary,
                                                                      myContext.getModulesConfigurator().getProjectStructureConfigurable(),
                                                                      false);
    }
  }

  private final class RemoveLibraryFix extends ConfigurationErrorQuickFix {
    private RemoveLibraryFix() {
      super(JavaUiBundle.message("label.remove.library"));
    }

    @Override
    public void performFix() {
      BaseLibrariesConfigurable.getInstance(myContext.getModulesConfigurator().getProjectStructureConfigurable(),
                                            myLibrary.getTable().getTableLevel()).removeLibrary(LibraryProjectStructureElement.this);
    }
  }

  private final class RemoveAllUnusedLibrariesFix extends ConfigurationErrorQuickFix {
    private RemoveAllUnusedLibrariesFix() {
      super(JavaUiBundle.message("label.remove.all.unused.libraries"));
    }

    @Override
    public void performFix() {
      ProjectStructureConfigurable projectStructureConfigurable = myContext.getModulesConfigurator().getProjectStructureConfigurable();
      BaseLibrariesConfigurable configurable = BaseLibrariesConfigurable.getInstance(projectStructureConfigurable, LibraryTablesRegistrar.PROJECT_LEVEL);
      Library[] libraries = configurable.getModelProvider().getModifiableModel().getLibraries();
      List<LibraryProjectStructureElement> toRemove = new ArrayList<>();
      for (Library library : libraries) {
        LibraryProjectStructureElement libraryElement = new LibraryProjectStructureElement(myContext, library);
        if (myContext.getDaemonAnalyzer().getUsages(libraryElement).isEmpty()) {
          toRemove.add(libraryElement);
        }
      }
      configurable.removeLibraries(toRemove);
    }
  }
}
