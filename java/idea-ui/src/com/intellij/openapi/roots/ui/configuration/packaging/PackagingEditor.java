package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.deployment.PackagingConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.OrderEntry;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public interface PackagingEditor {

  void saveData();

  void moduleStateChanged();

  ContainerElement[] getModifiedElements();

  boolean isModified();

  void reset();

  JComponent createMainComponent();

  JPanel getMainPanel();

  void addModules(final List<Module> modules);

  void addLibraries(final List<Library> libraries);

  PackagingConfiguration getModifiedConfiguration();

  void rebuildTree();

  void addElement(ContainerElement element);

  void selectElement(@NotNull ContainerElement toSelect, final boolean requestFocus);

  void processNewOrderEntries(final Set<OrderEntry> newEntries);

  void addListener(@NotNull PackagingEditorListener listener);

  void removeListener(@NotNull PackagingEditorListener listener);
}
