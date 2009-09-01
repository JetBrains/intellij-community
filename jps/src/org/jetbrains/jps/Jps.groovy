package org.jetbrains.jps

import org.codehaus.gant.GantBinding

/**
 * @author max
 */
final class Jps {

  def Jps(GantBinding binding) {
    Project project = new Project(binding)
    binding.setVariable("project", project)
    binding.setVariable("module", {String name, Closure initializer ->
      return project.createModule(name, initializer)
    })

    binding.setVariable("library", {String name, Closure initializer ->
      return project.createLibrary(name, initializer)
    })

    binding.setVariable("moduleTests", {String name ->
      def module = project.modules[name]
      if (module == null) project.error("cannot find module ${name}")
      return project.builder.moduleTestsOutput(module)
    })

    binding.setVariable("layout", {String dir, Closure body ->
      def old = binding.getVariable("module")

      ["module", "jar", "zip", "dir"].each {tag ->
        binding.setVariable(tag, {Object[] args ->
          if (args.length == 1) {
            binding.ant."$tag"(name: args[0])
          }
          else if (args.length == 2) {
            binding.ant."$tag"(name: args[0], args[1])
          }
          else {
            project.error("unexpected number of parameters for $tag")
          }
        })
      }

      try {
        def meta = new Expando()
        body.delegate = meta
        binding.ant.layout(toDir: dir, body)
      } finally {
        binding.setVariable("module", old)
      }

    })

    project.taskdef(name: "layout", classname: "jetbrains.antlayout.tasks.LayoutTask")
  }

  def Jps(GantBinding binding, Map map) {
    this(binding)
  }
}
