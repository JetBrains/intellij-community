package com.intellij.compiler.ant;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.compiler.Chunk;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;

import javax.naming.OperationNotSupportedException;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 25, 2004
 */
public class GenerationOptions {
  public final boolean generateSingleFile;
  public final boolean enableFormCompiler;
  public final boolean backupPreviouslyGeneratedFiles;
  public final boolean forceTargetJdk;
  private final ReplacePathToMacroMap myMacroReplacementMap; // from absolute path to macro substitutions
  private final Map<String, String> myOutputUrlToPropertyRefMap; // from absolute path to macro substitutions
  private final ModuleChunk[] myModuleChunks;
  private final Project myProject;

  public GenerationOptions(Project project,
                           boolean generateSingleFile,
                           boolean enableFormCompiler,
                           boolean backupPreviouslyGeneratedFiles, boolean forceTargetJdk, String[] representativeModuleNames) {
    myProject = project;
    this.generateSingleFile = generateSingleFile;
    this.enableFormCompiler = enableFormCompiler;
    this.backupPreviouslyGeneratedFiles = backupPreviouslyGeneratedFiles;
    this.forceTargetJdk = forceTargetJdk;
    myMacroReplacementMap = createReplacementMap();
    myModuleChunks = createModuleChunks(representativeModuleNames);
    myOutputUrlToPropertyRefMap = createOutputUrlToPropertyRefMap(myModuleChunks);
  }

  public String subsitutePathWithMacros(String path) {
    if (myMacroReplacementMap.size() == 0) {
      return path; // optimization
    }
    return myMacroReplacementMap.substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  public String getPropertyRefForUrl(String url) {
    return myOutputUrlToPropertyRefMap.get(url);
  }

  private static ReplacePathToMacroMap createReplacementMap() {
    final PathMacrosImpl pathMacros = PathMacrosImpl.getInstanceEx();
    final Set<String> macroNames = pathMacros.getUserMacroNames();
    final ReplacePathToMacroMap map = new ReplacePathToMacroMap();
    for (final String macroName : macroNames) {
      map.put(pathMacros.getValue(macroName), BuildProperties.propertyRef(BuildProperties.getPathMacroProperty(macroName)));
    }
    return map;
  }

  private static Map<String, String> createOutputUrlToPropertyRefMap(ModuleChunk[] chunks) {
    final Map<String, String> map = new HashMap<String, String>();

    for (final ModuleChunk chunk : chunks) {
      final String outputPathRef = BuildProperties.propertyRef(BuildProperties.getOutputPathProperty(chunk.getName()));
      final String testsOutputPathRef = BuildProperties.propertyRef(BuildProperties.getOutputPathForTestsProperty(chunk.getName()));

      final Module[] modules = chunk.getModules();
      for (final Module module : modules) {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        final String outputPathUrl = rootManager.getCompilerOutputPathUrl();
        if (outputPathUrl != null) {
          map.put(outputPathUrl, outputPathRef);
        }
        final String outputPathForTestsUrl = rootManager.getCompilerOutputPathForTestsUrl();
        if (outputPathForTestsUrl != null) {
          if (outputPathUrl == null || !outputPathForTestsUrl.equals(outputPathUrl)) {
            map.put(outputPathForTestsUrl, testsOutputPathRef);
          }
        }
      }
    }
    return map;
  }

  public ModuleChunk[] getModuleChunks() {
    return myModuleChunks;
  }

  protected ModuleChunk[] createModuleChunks(String[] representativeModuleNames) {
    final Set<String> mainModuleNames = new HashSet<String>(Arrays.asList(representativeModuleNames));
    final Graph<Chunk<Module>> chunkGraph = ModuleCompilerUtil.toChunkGraph(ModuleManager.getInstance(myProject).moduleGraph());
    final Map<Chunk<Module>, ModuleChunk> map = new HashMap<Chunk<Module>, ModuleChunk>();
    final Map<ModuleChunk, Chunk<Module>> reverseMap = new HashMap<ModuleChunk, Chunk<Module>>();
    for (final Chunk<Module> chunk : chunkGraph.getNodes()) {
      final Set<Module> modules = chunk.getNodes();
      final ModuleChunk moduleChunk = new ModuleChunk(modules.toArray(new Module[modules.size()]));
      for (final Module module : modules) {
        if (mainModuleNames.contains(module.getName())) {
          moduleChunk.setMainModule(module);
          break;
        }
      }
      map.put(chunk, moduleChunk);
      reverseMap.put(moduleChunk, chunk);
    }

    final Graph<ModuleChunk> moduleChunkGraph = GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<ModuleChunk>() {
      public Collection<ModuleChunk> getNodes() {
        return map.values();
      }

      public Iterator<ModuleChunk> getIn(ModuleChunk n) {
        final Chunk<Module> chunk = reverseMap.get(n);
        final Iterator<Chunk<Module>> in = chunkGraph.getIn(chunk);
        return new Iterator<ModuleChunk>() {
          public boolean hasNext() {
            return in.hasNext();
          }
          public ModuleChunk next() {
            return map.get(in.next());
          }
          public void remove() {
            new OperationNotSupportedException();
          }
        };
      }
    }));
    final Collection<ModuleChunk> nodes = moduleChunkGraph.getNodes();
    final ModuleChunk[] moduleChunks = nodes.toArray(new ModuleChunk[nodes.size()]);
    for (ModuleChunk moduleChunk : moduleChunks) {
      final Iterator<ModuleChunk> depsIterator = moduleChunkGraph.getIn(moduleChunk);
      List<ModuleChunk> deps = new ArrayList<ModuleChunk>();
      while (depsIterator.hasNext()) {
        deps.add(depsIterator.next());
      }
      moduleChunk.setDependentChunks(deps.toArray(new ModuleChunk[deps.size()]));
    }
    Arrays.sort(moduleChunks, new DFSTBuilder<ModuleChunk>(moduleChunkGraph).comparator());
    if (generateSingleFile) {
      final File baseDir = BuildProperties.getProjectBaseDir(myProject);
      for (ModuleChunk chunk : moduleChunks) {
        chunk.setBaseDir(baseDir);
      }
    }
    return moduleChunks;
  }
}
