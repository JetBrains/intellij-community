/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.BaseLibrariesConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibraryConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
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
    final String libraryName = library.getName();
    if (libraryName == null || !libraryName.startsWith("Maven: ")) {
      reportInvalidRoots(problemsHolder, library, OrderRootType.SOURCES, "sources",
                         ProjectStructureProblemType.warning("library-invalid-source-javadoc-path"));
      reportInvalidRoots(problemsHolder, library, JavadocOrderRootType.getInstance(), "javadoc",
                         ProjectStructureProblemType.warning("library-invalid-source-javadoc-path"));
    }
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
    final String name = StringUtil.escapeXml(libraryName);
    buffer.append("Library ");
    buffer.append("<a href='http://library/").append(name).append("'>").append(name).append("</a>");
    buffer.append(" has broken " + rootName + " " + StringUtil.pluralize("path", invalidClasses.size()) + ":");
    for (String url : invalidClasses) {
      buffer.append("<br>&nbsp;&nbsp;");
      buffer.append(PathUtil.toPresentableUrl(url));
    }
    return XmlStringUtil.wrapInHtml(buffer);
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
    return LibraryTablesRegistrar.PROJECT_LEVEL.equals(libraryTable.getTableLevel());
  }

  @Override
  public ProjectStructureProblemDescription createUnusedElementWarning() {
    final List<ConfigurationErrorQuickFix> fixes = Arrays.asList(new AddLibraryToDependenciesFix(), new RemoveLibraryFix(), new RemoveAllUnusedLibrariesFix());
    final String name = StringUtil.escapeXml(myLibrary.getName());
    String libraryName = "<a href='http://library/" + name + "'>" + name + "</a>";
    return new ProjectStructureProblemDescription(XmlStringUtil.wrapInHtml("Library " + libraryName + " is not used"), null, createPlace(),
                                                  ProjectStructureProblemType.unused("unused-library"), ProjectStructureProblemDescription.ProblemLevel.PROJECT,
                                                  fixes, false);
  }

  @Override
  public String getPresentableName() {
    return myLibrary.getName();
  }

  @Override
  public String getTypeName() {
    return "Library";
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
        navigate().doWhenDone(() -> {
          final NamedConfigurable configurable = structureConfigurable.getConfigurableFor(myLibrary).getSelectedConfigurable();
          if (configurable instanceof LibraryConfigurable) {
            ((LibraryConfigurable)configurable).updateComponent();
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
      LibraryEditingUtil.showDialogAndAddLibraryToDependencies(myLibrary, myContext.getProject(), false);
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

  private class RemoveAllUnusedLibrariesFix extends ConfigurationErrorQuickFix {
    private RemoveAllUnusedLibrariesFix() {
      super("Remove All Unused Libraries");
    }

    @Override
    public void performFix() {
      BaseLibrariesConfigurable configurable = BaseLibrariesConfigurable.getInstance(myContext.getProject(), LibraryTablesRegistrar.PROJECT_LEVEL);
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
