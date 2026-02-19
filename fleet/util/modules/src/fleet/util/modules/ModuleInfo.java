package fleet.util.modules;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public sealed interface ModuleInfo {
  record WithDescriptor(ModuleDescriptor descriptor, String path) implements ModuleInfo {
  }

  record Path(String path) implements ModuleInfo {
  }

  static List<ModuleInfo> readModulePathFromFile(java.nio.file.Path file, java.nio.file.Path bundledCodeCacheDirectory) {
    try {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      int size = lines.size();
      int resultCapacity = size / 2 + size % 2 == 0 ? 0 : 1;
      List<ModuleInfo> result = new ArrayList<>(resultCapacity);
      for (int i = 0; i < size; i += 2) {
        if (i < size - 1) {
          java.nio.file.Path moduleFile = java.nio.file.Path.of(lines.get(i));
          moduleFile = moduleFile.isAbsolute() ? moduleFile : bundledCodeCacheDirectory.resolve(moduleFile);

          String moduleDescriptor = lines.get(i + 1);
          if ("null".equals(moduleDescriptor)) {
            result.add(new ModuleInfo.Path(moduleFile.toString()));
          }
          else {
            result.add(new ModuleInfo.WithDescriptor(ModuleLayers.deserializeModuleDescriptor(moduleDescriptor), moduleFile.toString()));
          }
        }
      }
      return result;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}