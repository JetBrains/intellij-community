package com.intellij.facet.ui.libraries;

import com.intellij.javaee.LibrariesManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarVersionDetectionUtil {
  @NonNls private static final String IMPLEMENTATION_VERSION = "Implementation-Version";

  private JarVersionDetectionUtil() {
  }

  @Nullable
  public static String detectJarVersion(@NotNull final String detectionClass, @NotNull Module module) {
    try {
      final ZipFile zipFile = getDetectionJar(detectionClass, module);
      if (zipFile == null) {
        return null;
      }
      final ZipEntry zipEntry = zipFile.getEntry(JarFile.MANIFEST_NAME);
      if (zipEntry == null) {
        return null;
      }
      final InputStream inputStream = zipFile.getInputStream(zipEntry);
      final Manifest manifest = new Manifest(inputStream);
      final Attributes attributes = manifest.getMainAttributes();
      return attributes.getValue(IMPLEMENTATION_VERSION);
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nullable
  private static ZipFile getDetectionJar(final String detectionClass, Module module) throws IOException {
      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      for (OrderEntry library : model.getOrderEntries()) {
        if (library instanceof LibraryOrderEntry) {
          VirtualFile file = LibrariesManager.getInstance().findJarByClass(((LibraryOrderEntry)library).getLibrary(), detectionClass);
          if (file != null) {
            return JarFileSystem.getInstance().getJarFile(file);
          }
        }
      }
    return null;
  }
}

