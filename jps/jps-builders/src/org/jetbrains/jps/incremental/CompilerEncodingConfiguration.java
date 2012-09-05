package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.JpsEncodingProjectConfiguration;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class CompilerEncodingConfiguration {
  private final Map<String, String> myUrlToCharset;
  private final String myProjectCharset;
  private final ModuleRootsIndex myRootsIndex;
  private Map<String, Set<String>> myModuleCharsetMap;

  public CompilerEncodingConfiguration(JpsModel jpsModel, ModuleRootsIndex index) {
    JpsEncodingProjectConfiguration configuration = JpsEncodingConfigurationService.getInstance().getEncodingConfiguration(jpsModel.getProject());
    myUrlToCharset = configuration != null ? configuration.getUrlToEncoding() : Collections.<String, String>emptyMap();
    myProjectCharset = JpsEncodingConfigurationService.getInstance().getProjectEncoding(jpsModel);
    myRootsIndex = index;
  }

  public Map<String, Set<String>> getModuleCharsetMap() {
    if (myModuleCharsetMap == null) {
      myModuleCharsetMap = computeModuleCharsetMap();
    }
    return myModuleCharsetMap;
  }

  private Map<String, Set<String>> computeModuleCharsetMap() {
    final Map<String, Set<String>> map = new THashMap<String, Set<String>>();
    final List<ModuleLevelBuilder> builders = BuilderRegistry.getInstance().getModuleLevelBuilders();
    for (Map.Entry<String, String> entry : myUrlToCharset.entrySet()) {
      final String fileUrl = entry.getKey();
      final String charset = entry.getValue();
      File file = JpsPathUtil.urlToFile(fileUrl);
      if (charset == null || (!file.isDirectory() && !shouldHonorEncodingForCompilation(builders, file))) continue;

      final RootDescriptor rootDescriptor = myRootsIndex.getModuleAndRoot(null, file);
      if (rootDescriptor == null) continue;

      final String module = rootDescriptor.module;
      Set<String> set = map.get(module);
      if (set == null) {
        set = new LinkedHashSet<String>();
        map.put(module, set);

        final File sourceRoot = rootDescriptor.root;
        File current = FileUtil.getParentFile(file);
        String parentCharset = null;
        while (current != null) {
          final String currentCharset = myUrlToCharset.get(FileUtil.toSystemIndependentName(current.getAbsolutePath()));
          if (currentCharset != null) {
            parentCharset = currentCharset;
          }
          if (FileUtil.filesEqual(current, sourceRoot)) {
            break;
          }
          current = FileUtil.getParentFile(current);
        }
        if (parentCharset != null) {
          set.add(parentCharset);
        }
      }
      set.add(charset);
    }

    return map;
  }

  private static boolean shouldHonorEncodingForCompilation(List<ModuleLevelBuilder> builders, File file) {
    for (ModuleLevelBuilder builder : builders) {
      if (builder.shouldHonorFileEncodingForCompilation(file)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public String getPreferredModuleChunkEncoding(@NotNull ModuleChunk moduleChunk) {
    for (JpsModule module : moduleChunk.getModules()) {
      final Set<String> encodings = getModuleCharsetMap().get(module.getName());
      final String encoding = ContainerUtil.getFirstItem(encodings, null);
      if (encoding != null) {
        return encoding;
      }
    }
    return myProjectCharset;
  }

  @NotNull
  public Set<String> getAllModuleChunkEncodings(@NotNull ModuleChunk moduleChunk) {
    final Map<String, Set<String>> map = getModuleCharsetMap();
    Set<String> encodings = new HashSet<String>();
    for (JpsModule module : moduleChunk.getModules()) {
      final Set<String> moduleEncodings = map.get(module.getName());
      if (moduleEncodings != null) {
        encodings.addAll(moduleEncodings);
      }
    }
    return encodings;
  }
}
