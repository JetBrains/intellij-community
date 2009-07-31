package org.jetbrains.jps

/**
 * @author max
 */
class ModuleChunk {
  Set<Module> modules = new LinkedHashSet<Module>()

  Set<ModuleChunk> outs = new LinkedHashSet<ModuleChunk>()
  Set<ModuleChunk> ins = new LinkedHashSet<ModuleChunk>()

  def String getName() {
    if (modules.size() == 1) return modules.iterator().next().getName();

    StringBuilder b = new StringBuilder()

    b << "ModuleChunk("
    modules.eachWithIndex {Module it, int index ->
      if (index > 0) b << ","
      b << it.name
    }
    b << ")"

    b.toString()
  }

  public static ModuleChunk fromModule(Module m) {
    ModuleChunk answer = new ModuleChunk()
    answer.modules << m
    return answer
  }

  public static ModuleChunk join(List<ModuleChunk> chunks) {
    ModuleChunk answer = new ModuleChunk();

    chunks.each { chunk ->
      answer.modules.addAll(chunk.modules)

      chunk.ins.each {
        it.outs.add(answer)
        it.outs.remove(chunk)
        answer.ins.add(it)
      }

      chunk.outs.each {
        it.ins.add(answer)
        it.ins.remove(chunk)
        answer.outs.add(it)
      }
    }

    answer.ins.removeAll(chunks)
    answer.outs.removeAll(chunks)

    answer.ins.remove(answer)
    answer.outs.remove(answer)

    return answer
  }

  def depdendsOn(ModuleChunk chunk) {
    chunk.ins << this
    outs << chunk
  }

  def String toString() {
    return getName();
  }


}
