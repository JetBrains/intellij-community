package org.jetbrains.jps;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;

import java.util.*;

/**
 * @author nik
 */
public class ProjectChunks {
  private List<ModuleChunk> chunks;
  private final Map<JpsModule, ModuleChunk> mapping = new HashMap<JpsModule, ModuleChunk>();
  private final JpsJavaClasspathKind classpathKind;
  private final JpsProject project;

  public ProjectChunks(JpsProject project, JpsJavaClasspathKind classpathKind) {
    this.classpathKind = classpathKind;
    this.project = project;
  }

  public List<ModuleChunk> getChunkList() {
    initializeChunks();
    return chunks;
  }

  private void initializeChunks() {
    if (chunks != null) {
      return;
    }

    final GraphGenerator<JpsModule> graph = GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<JpsModule>() {
      @Override
      public Collection<JpsModule> getNodes() {
        return project.getModules();
      }

      @Override
      public Iterator<JpsModule> getIn(JpsModule n) {
        List<JpsModule> deps = new ArrayList<JpsModule>();
        for (JpsDependencyElement element : n.getDependenciesList().getDependencies()) {
          if (element instanceof JpsModuleDependency) {
            final JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getDependencyExtension(element);
            if (extension == null || extension.getScope().isIncludedIn(classpathKind)) {
              ContainerUtil.addIfNotNull(deps, ((JpsModuleDependency)element).getModule());
            }
          }
        }
        return deps.iterator();
      }
    }));
    
    final DFSTBuilder<JpsModule> builder = new DFSTBuilder<JpsModule>(graph);
    final TIntArrayList sccs = builder.getSCCs();

    chunks = new ArrayList<ModuleChunk>(sccs.size());
    sccs.forEach(new TIntProcedure() {
      int myTNumber = 0;
      public boolean execute(int size) {
        final Set<JpsModule> chunkNodes = new LinkedHashSet<JpsModule>();
        for (int j = 0; j < size; j++) {
          final JpsModule node = builder.getNodeByTNumber(myTNumber + j);
          chunkNodes.add(node);
        }
        chunks.add(new ModuleChunk(chunkNodes));

        myTNumber += size;
        return true;
      }
    });

    for (ModuleChunk chunk : chunks) {
      for (JpsModule module : chunk.getModules()) {
        mapping.put(module, chunk);
      }
    }
  }

  public ModuleChunk findChunk(JpsModule module) {
    initializeChunks();
    return mapping.get(module);
  }
}
