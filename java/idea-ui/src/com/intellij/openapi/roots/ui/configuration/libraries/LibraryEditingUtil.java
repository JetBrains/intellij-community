/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.libraries;

import com.google.common.base.Predicate;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.ModuleLibraryTable;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.classpath.ClasspathPanel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ParameterizedRunnable;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public class LibraryEditingUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil");

  private LibraryEditingUtil() {
  }

  public static boolean libraryAlreadyExists(LibraryTable.ModifiableModel table, String libraryName) {
    for (Iterator<Library> it = table.getLibraryIterator(); it.hasNext(); ) {
      final Library library = it.next();
      final String libName;
      if (table instanceof LibrariesModifiableModel){
        libName = ((LibrariesModifiableModel)table).getLibraryEditor(library).getName();
      }
      else {
        libName = library.getName();
      }
      if (libraryName.equals(libName)) {
        return true;
      }
    }
    return false;
  }

  public static String suggestNewLibraryName(LibraryTable.ModifiableModel table, List<OrderRoot> roots) {
    return suggestNewLibraryName(table, suggestLibraryName(roots));
  }

  public static String suggestNewLibraryName(LibraryTable.ModifiableModel table,
                                             final String baseName) {
    String candidateName = baseName;
    int idx = 1;
    while (libraryAlreadyExists(table, candidateName)) {
      candidateName = baseName + (idx++);
    }
    return candidateName;
  }

  public static String suggestLibraryName(@NotNull List<OrderRoot> roots) {
    if (roots.size() >= 1) {
      return FileUtil.getNameWithoutExtension(PathUtil.getFileName(roots.get(0).getFile().getPath()));
    }
    return "Unnamed";
  }

  public static Predicate<Library> getNotAddedLibrariesCondition(final ModuleRootModel rootModel) {
    final OrderEntry[] orderEntries = rootModel.getOrderEntries();
    final Set<Library> result = new HashSet<Library>(orderEntries.length);
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry && orderEntry.isValid()) {
        final LibraryImpl library = (LibraryImpl)((LibraryOrderEntry)orderEntry).getLibrary();
        if (library != null) {
          final Library source = library.getSource();
          result.add(source != null ? source : library);
        }
      }
    }
    return new Predicate<Library>() {
      @Override
      public boolean apply(Library library) {
        if (result.contains(library)) return false;
        if (library instanceof LibraryImpl) {
          final Library source = ((LibraryImpl)library).getSource();
          if (source != null && result.contains(source)) return false;
        }
        return true;
      }
    };
  }

  public static void copyLibrary(LibraryEx from, Map<String, String> rootMapping, LibraryEx.ModifiableModelEx target) {
    target.setProperties(from.getProperties());
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      final String[] urls = from.getUrls(type);
      for (String url : urls) {
        final String protocol = VirtualFileManager.extractProtocol(url);
        if (protocol == null) continue;
        final String fullPath = VirtualFileManager.extractPath(url);
        final int sep = fullPath.indexOf(JarFileSystem.JAR_SEPARATOR);
        String localPath;
        String pathInJar;
        if (sep != -1) {
          localPath = fullPath.substring(0, sep);
          pathInJar = fullPath.substring(sep);
        }
        else {
          localPath = fullPath;
          pathInJar = "";
        }
        final String targetPath = rootMapping.get(localPath);
        String targetUrl = targetPath != null ? VirtualFileManager.constructUrl(protocol, targetPath + pathInJar) : url;

        if (from.isJarDirectory(url, type)) {
          target.addJarDirectory(targetUrl, false, type);
        }
        else {
          target.addRoot(targetUrl, type);
        }
      }
    }
  }

  public static LibraryTablePresentation getLibraryTablePresentation(@NotNull Project project, @NotNull String level) {
    if (level.equals(LibraryTableImplUtil.MODULE_LEVEL)) {
      return ModuleLibraryTable.MODULE_LIBRARY_TABLE_PRESENTATION;
    }
    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, project);
    LOG.assertTrue(table != null, level);
    return table.getPresentation();
  }

  public static List<LibraryType> getSuitableTypes(ClasspathPanel classpathPanel) {
    List<LibraryType> suitableTypes = new ArrayList<LibraryType>();
    suitableTypes.add(null);
    final Module module = classpathPanel.getRootModel().getModule();
    for (LibraryType libraryType : LibraryType.EP_NAME.getExtensions()) {
      if (libraryType.getCreateActionName() != null && libraryType.isSuitableModule(module, classpathPanel.getModuleConfigurationState().getFacetsProvider())) {
        suitableTypes.add(libraryType);
      }
    }
    return suitableTypes;
  }

  public static boolean hasSuitableTypes(ClasspathPanel panel) {
    return getSuitableTypes(panel).size() > 1;
  }

  public static BaseListPopupStep<LibraryType> createChooseTypeStep(final ClasspathPanel classpathPanel,
                                                                    final ParameterizedRunnable<LibraryType> action) {
    return new BaseListPopupStep<LibraryType>(IdeBundle.message("popup.title.select.library.type"), getSuitableTypes(classpathPanel)) {
          @NotNull
          @Override
          public String getTextFor(LibraryType value) {
            return value != null ? value.getCreateActionName() : IdeBundle.message("create.default.library.type.action.name");
          }

          @Override
          public Icon getIconFor(LibraryType aValue) {
            return aValue != null ? aValue.getIcon() : PlatformIcons.LIBRARY_ICON;
          }

          @Override
          public PopupStep onChosen(final LibraryType selectedValue, boolean finalChoice) {
            return doFinalStep(new Runnable() {
              @Override
              public void run() {
                action.run(selectedValue);
              }
            });
          }
        };
  }

  public static List<Module> getSuitableModules(@NotNull ModuleStructureConfigurable rootConfigurable, final @Nullable LibraryType type) {
    final List<Module> modules = new ArrayList<Module>();
    for (Module module : rootConfigurable.getModules()) {
      if (type == null || type.isSuitableModule(module, rootConfigurable.getFacetConfigurator())) {
        modules.add(module);
      }
    }
    return modules;
  }

  public static void showDialogAndAddLibraryToDependencies(@NotNull Library library, @NotNull Project project) {
    final ModuleStructureConfigurable moduleStructureConfigurable = ModuleStructureConfigurable.getInstance(project);
    final List<Module> modules = getSuitableModules(moduleStructureConfigurable, ((LibraryEx)library).getType());
    if (modules.isEmpty()) return;
    final ChooseModulesDialog
      dlg = new ChooseModulesDialog(moduleStructureConfigurable.getProject(), modules, ProjectBundle.message("choose.modules.dialog.title"),
                                                            ProjectBundle
                                                              .message("choose.modules.dialog.description", library.getName()));
    dlg.show();
    if (dlg.isOK()) {
      final List<Module> chosenModules = dlg.getChosenElements();
      for (Module module : chosenModules) {
        moduleStructureConfigurable.addLibraryOrderEntry(module, library);
      }
    }
  }
}
