package org.jetbrains.jps.incremental;

import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/11/12
 */
public class ModuleRootsIndex {
  private final Set<File> myExcludedRoots = new HashSet<File>();
  private final Map<JpsModule, List<File>> myModuleToExcludesMap = new HashMap<JpsModule, List<File>>();
  private final IgnoredFilePatterns myIgnoredFilePatterns;

  public ModuleRootsIndex(JpsModel model) {
    myIgnoredFilePatterns = new IgnoredFilePatterns(model.getGlobal().getFileTypesConfiguration().getIgnoredPatternString());
    final Collection<JpsModule> allModules = model.getProject().getModules();
    for (final JpsModule module : allModules) {
      final List<File> moduleExcludes = new ArrayList<File>();
      myModuleToExcludesMap.put(module, moduleExcludes);
      for (String url : module.getExcludeRootsList().getUrls()) {
        final File root = JpsPathUtil.urlToFile(url);
        myExcludedRoots.add(root);
        moduleExcludes.add(root);
      }
    }

    Map<File, JpsModule> contentToModule = new HashMap<File, JpsModule>();
    for (JpsModule module : allModules) {
      for (String contentUrl : module.getContentRootsList().getUrls()) {
        File contentRoot = JpsPathUtil.urlToFile(contentUrl);
        contentToModule.put(contentRoot, module);
      }
    }
    List<File> parents = new ArrayList<File>();
    for (JpsModule module : allModules) {
      for (String contentUrl : module.getContentRootsList().getUrls()) {
        File contentRoot = JpsPathUtil.urlToFile(contentUrl);
        File parent = contentRoot.getParentFile();
        JpsModule parentModule = null;
        parents.clear();
        while (parent != null) {
          parents.add(parent);
          if (contentToModule.containsKey(parent)) {
            parentModule = contentToModule.get(parent);
            break;
          }
          parent = parent.getParentFile();
        }
        if (parentModule != null) {
          myModuleToExcludesMap.get(parentModule).add(contentRoot);
        }
        for (File file : parents) {
          contentToModule.put(file, parentModule);
        }
      }
    }
  }

  public IgnoredFilePatterns getIgnoredFilePatterns() {
    return myIgnoredFilePatterns;
  }

  public boolean isExcluded(File file) {
    return JpsPathUtil.isUnder(myExcludedRoots, file);
  }

  public Collection<File> getModuleExcludes(JpsModule module) {
    return myModuleToExcludesMap.get(module);
  }
}
