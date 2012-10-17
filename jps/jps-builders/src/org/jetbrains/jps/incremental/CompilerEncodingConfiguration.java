package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.JpsEncodingProjectConfiguration;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class CompilerEncodingConfiguration {
  private final Map<String, String> myUrlToCharset;
  private final String myProjectCharset;
  private final BuildRootIndex myRootsIndex;
  private Map<JpsModule, Set<String>> myModuleCharsetMap;

  public CompilerEncodingConfiguration(JpsModel jpsModel, BuildRootIndex index) {
    JpsEncodingProjectConfiguration configuration = JpsEncodingConfigurationService.getInstance().getEncodingConfiguration(jpsModel.getProject());
    myUrlToCharset = configuration != null ? configuration.getUrlToEncoding() : Collections.<String, String>emptyMap();
    myProjectCharset = JpsEncodingConfigurationService.getInstance().getProjectEncoding(jpsModel);
    myRootsIndex = index;
  }

  public Map<JpsModule, Set<String>> getModuleCharsetMap() {
    if (myModuleCharsetMap == null) {
      myModuleCharsetMap = computeModuleCharsetMap();
    }
    return myModuleCharsetMap;
  }

  private Map<JpsModule, Set<String>> computeModuleCharsetMap() {
    final Map<JpsModule, Set<String>> map = new THashMap<JpsModule, Set<String>>();
    final List<ModuleLevelBuilder> builders = BuilderRegistry.getInstance().getModuleLevelBuilders();
    for (Map.Entry<String, String> entry : myUrlToCharset.entrySet()) {
      final String fileUrl = entry.getKey();
      final String charset = entry.getValue();
      File file = JpsPathUtil.urlToFile(fileUrl);
      if (charset == null || (!file.isDirectory() && !shouldHonorEncodingForCompilation(builders, file))) continue;

      final JavaSourceRootDescriptor rootDescriptor = myRootsIndex.findJavaRootDescriptor(null, file);
      if (rootDescriptor == null) continue;

      final JpsModule module = rootDescriptor.target.getModule();
      Set<String> set = map.get(module);
      if (set == null) {
        set = new LinkedHashSet<String>();
        map.put(module, set);

        final File sourceRoot = rootDescriptor.root;
        File current = FileUtilRt.getParentFile(file);
        String parentCharset = null;
        while (current != null) {
          final String currentCharset = myUrlToCharset.get(FileUtil.toSystemIndependentName(current.getAbsolutePath()));
          if (currentCharset != null) {
            parentCharset = currentCharset;
          }
          if (FileUtil.filesEqual(current, sourceRoot)) {
            break;
          }
          current = FileUtilRt.getParentFile(current);
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
      final Set<String> encodings = getModuleCharsetMap().get(module);
      final String encoding = ContainerUtil.getFirstItem(encodings, null);
      if (encoding != null) {
        return encoding;
      }
    }
    return myProjectCharset;
  }

  @NotNull
  public Set<String> getAllModuleChunkEncodings(@NotNull ModuleChunk moduleChunk) {
    final Map<JpsModule, Set<String>> map = getModuleCharsetMap();
    Set<String> encodings = new HashSet<String>();
    for (JpsModule module : moduleChunk.getModules()) {
      final Set<String> moduleEncodings = map.get(module);
      if (moduleEncodings != null) {
        encodings.addAll(moduleEncodings);
      }
    }
    return encodings;
  }
}
