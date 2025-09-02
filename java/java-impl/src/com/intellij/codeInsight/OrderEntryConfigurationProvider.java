// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@link OptionControllerProvider} that provides a root OrderEntryConfiguration
 * with options to configure the project. Currently, only the "externalAnnotations" option is provided,
 * which is the list of all external annotation roots applicable for the context order entry.
 */
final class OrderEntryConfigurationProvider implements OptionControllerProvider {
  @Override
  public @NotNull OptionController forContext(@NotNull PsiElement context) {
    PsiFile file = context.getContainingFile();
    List<OrderEntry> orderEntries = ProjectRootManager.getInstance(file.getProject())
      .getFileIndex().getOrderEntriesForFile(file.getVirtualFile());
    OrderEntry entry = ContainerUtil.find(orderEntries, oe -> !(oe instanceof ModuleOrderEntry));
    if (entry == null) return OptionController.empty();
    return OptionController.empty()
      .onValue("externalAnnotations", () -> getExternalAnnotations(entry),
               list -> WriteAction.run(() -> setExternalAnnotations(entry, list)));
  }

  private static void setExternalAnnotations(@NotNull OrderEntry entry, @NotNull List<String> list) {
    OrderRootType type = AnnotationOrderRootType.getInstance();
    if (entry instanceof LibraryOrderEntry orderEntry) {
      Library library = orderEntry.getLibrary();
      if (library == null) {
        throw new IllegalStateException("No library found for "+orderEntry.getPresentableName());
      }
      final Library.ModifiableModel model = library.getModifiableModel();
      for (String url : model.getUrls(type)) {
        model.removeRoot(url, type);
      }
      list.forEach(url -> model.addRoot(url, type));
      model.commit();
    }
    else if (entry instanceof ModuleSourceOrderEntry) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(entry.getOwnerModule()).getModifiableModel();
      final JavaModuleExternalPaths extension = model.getModuleExtension(JavaModuleExternalPaths.class);
      extension.setExternalAnnotationUrls(ArrayUtil.toStringArray(list));
      model.commit();
    }
    else if (entry instanceof JdkOrderEntry jdkOrderEntry) {
      Sdk jdk = jdkOrderEntry.getJdk();
      if (jdk == null) {
        throw new IllegalStateException("JDK is not configured correctly: "+jdkOrderEntry.getPresentableName());
      }
      final SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.removeRoots(type);
      list.forEach(url -> sdkModificator.addRoot(url, type));
      sdkModificator.commitChanges();
    }
  }

  private static @NotNull List<String> getExternalAnnotations(@NotNull OrderEntry entry) {
    if (entry instanceof LibraryOrderEntry libraryOrderEntry) {
      Library library = libraryOrderEntry.getLibrary();
      if (library == null) return List.of();
      return List.of(library.getUrls(AnnotationOrderRootType.getInstance()));
    }
    else if (entry instanceof ModuleSourceOrderEntry) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(entry.getOwnerModule()).getModifiableModel();
      final JavaModuleExternalPaths extension = model.getModuleExtension(JavaModuleExternalPaths.class);
      return List.of(extension.getExternalAnnotationsUrls());
    }
    else if (entry instanceof JdkOrderEntry jdkOrderEntry) {
      Sdk jdk = jdkOrderEntry.getJdk();
      if (jdk == null) return List.of();
      return List.of(jdk.getRootProvider().getUrls(AnnotationOrderRootType.getInstance()));
    }
    return List.of();
  }

  @Override
  public @NotNull String name() {
    return "OrderEntryConfiguration";
  }
}
