package org.jetbrains.jps

/**
 * @author max
 */
class ChunkDAG {

  public List<ModuleChunk> build(Project project, Collection<Module> modules) {
    Set<ModuleChunk> workingSet = convertToChunks(modules)
    reduceCycles(workingSet)
    sort(workingSet)
  }

  private Set<ModuleChunk> convertToChunks(Collection<Module> modules) {
    Map<Module, ModuleChunk> mapping = [:]

    Set<ModuleChunk> workingSet = new LinkedHashSet<ModuleChunk>()
    modules.each {
      def chunk = ModuleChunk.fromModule(it)
      workingSet << chunk
      mapping[it] = chunk
    }

    mapping.each {Module module, ModuleChunk chunk ->
      module.classpath.each {entry ->
        if (entry instanceof Module) {
          chunk.depdendsOn(mapping[entry])
        }
      }
    }

    return workingSet
  }

  private List<ModuleChunk> sort(LinkedHashSet<ModuleChunk> set) {
    Set<ModuleChunk> sorted = new LinkedHashSet<ModuleChunk>()
    for (chunk in set) {
      traverse(chunk, sorted)
    }

    sorted.asList()
  }

  private def traverse(ModuleChunk chunk, Set<ModuleChunk> traversed) {
    if (traversed.contains(chunk)) return
    for (out in chunk.outs) {
      traverse(out, traversed)
    }
    traversed.add(chunk)
  }

  private def reduceCycles(LinkedHashSet<ModuleChunk> workingSet) {
    Set<ModuleChunk> cleanSet = new HashSet<ModuleChunk>()
    attempts:
    while (true) {
      Stack<ModuleChunk> stack = new Stack<ModuleChunk>()
      for (chunk in workingSet) {
        List<ModuleChunk> cycle = findCycle(chunk, stack, cleanSet)
        if (cycle != null) {
          workingSet << ModuleChunk.join(cycle)
          workingSet.removeAll(cycle)
          continue attempts
        }
      }

      break;
    }
  }

  private List<ModuleChunk> findCycle(ModuleChunk chunk, Stack<ModuleChunk> stack, Set<ModuleChunk> cleanSet) {
    if (cleanSet.contains(chunk)) return null

    stack.push(chunk)
    for (out in chunk.outs) {
      if (stack.contains(out)) {
        List<ModuleChunk> cycle = []
        while (true) {
          ModuleChunk ch = stack.pop()
          cycle << ch
          if (ch == out) {
            return cycle
          }
        }
      }

      List<ModuleChunk> cycle = findCycle(out, stack, cleanSet)
      if (cycle != null) return cycle
    }

    stack.pop()
    cleanSet << chunk

    return null
  }


  private List<Module> convertToModules(List<ModuleChunk> chunks) {
    
  }
}

