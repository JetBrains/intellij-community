package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
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

    final String libraryName = library.getName();
    if (!library.allPathsValid(OrderRootType.CLASSES)) {
      problemsHolder.registerError(ProjectBundle.message("project.roots.tooltip.library.misconfigured", libraryName));
    }
    else if (!library.allPathsValid(JavadocOrderRootType.getInstance()) || !library.allPathsValid(OrderRootType.SOURCES)) {
      problemsHolder.registerWarning(ProjectBundle.message("project.roots.tooltip.library.misconfigured", libraryName));
    }
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
  public String toString() {
    return "library:" + myLibrary.getName();
  }

  @Override
  public boolean highlightIfUnused() {
    final LibraryTable libraryTable = myLibrary.getTable();
    return libraryTable != null && LibraryTablesRegistrar.PROJECT_LEVEL.equals(libraryTable.getTableLevel());
  }
}
