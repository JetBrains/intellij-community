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

    binding.setVariable("globalLibrary", {String name, Closure initializer ->
      return project.createGlobalLibrary(name, initializer)
    })

    binding.setVariable("jdk", {Object[] args ->
      if (!(args.length in [2,3])) {
        project.error("expected 2 to 3 parameters for jdk() but ${args.length} found")
      }
      Closure initializer = args.length > 2 ? args[2] : {}
      return project.createJavaSdk((String)args[0], (String)args[1], initializer)
    })

    binding.setVariable("moduleTests", {String name ->
      def module = project.modules[name]
      if (module == null) project.error("cannot find module ${name}")
      return project.builder.moduleTestsOutput(module)
    })

    binding.setVariable("layout", {String dir, Closure body ->
      def old = binding.getVariable("module")

      ["module", "zip", "dir"].each {tag ->
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
      binding.setVariable("jar", {Object[] args ->
        if (args.length == 2) {
          binding.ant.jar(name: args[0], compress: project.builder.compressJars, args[1])
        }
        else {
          project.error("unexpected number of parameters for 'jar' task: $args.length")
        }
      })

      binding.setVariable("renamedFile", {Object[] args ->
        if (args.length != 2) {
          project.error("unexpected number of parameters for renamedFile")
        }
        binding.ant."renamedFile"(filePath: args[0], newName: args[1])
      })
      binding.setVariable("extractedDir", {Object[] args ->
        if (args.length != 2) {
          project.error("unexpected number of parameters for extractedDir")
        }
        binding.ant."extractedDir"(jarPath: args[0], pathInJar: args[1])
      })

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
