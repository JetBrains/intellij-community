package org.jetbrains.jps

import org.jetbrains.jps.idea.IdeaProjectLoadingUtil
import org.jetbrains.jps.idea.ModuleMacroExpander
import org.jetbrains.jps.idea.OwnServiceLoader
import org.jetbrains.jps.runConf.RunConfigurationLauncherService

/**
 * Represents IntelliJ IDEA run configuration.
 * @author pavel.sher
 */
public class RunConfiguration {
  final Project project;
  final String name;
  final String type;
  final Module module;
  final String workingDir;
  final Map<String, String> allOptions;
  final Map<String, String> envVars;
  final List<String> classPatterns;

  def RunConfiguration(Project project, MacroExpander macroExpander, Node confTag) {
    this.project = project;
    this.name = confTag.'@name';
    this.type = confTag.'@type';

    this.allOptions = [:];
    confTag.option.each{ opt ->
      def value = opt.'@value';
      if (value == null) {
        value = opt.value ? opt.value[0].'@defaultName' : null;
      }
      this.allOptions[opt.'@name'] = value;
    }

    def moduleNode = confTag.module[0];
    if (moduleNode != null && !"wholeProject".equals(this.allOptions['TEST_SEARCH_SCOPE'])) {
      this.module = project.modules[moduleNode.'@name'];
    } else {
      this.module = null;
    }

    if (this.module != null) {
      macroExpander = new ModuleMacroExpander(macroExpander, this.module.basePath);
    }

    def String workDirUrl = this.allOptions['WORKING_DIRECTORY'];
    if (workDirUrl == null) workDirUrl = "";
    if (workDirUrl != '') {
      workDirUrl = macroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(workDirUrl));
    }

    this.workingDir = workDirUrl == '' ? new File(".").getCanonicalPath() : new File(workDirUrl).getCanonicalPath();

    this.envVars = [:];
    confTag.envs.env.each{ el ->
      this.envVars[el.'@name'] = el.'@value';
    }

    this.classPatterns = [];
    confTag.patterns?.pattern.each{ el ->
      this.classPatterns.add(el.'@testClass');
    }
  }

  private static OwnServiceLoader<RunConfigurationLauncherService> runConfLauncherServices = OwnServiceLoader.load(RunConfigurationLauncherService.class)

  def start() {
    for (RunConfigurationLauncherService service: runConfLauncherServices.iterator()) {
      if (service.typeId == type) {
        service.startRunConfiguration(this);
        return;
      }
    }

    throw new RuntimeException("Run configuration \"$name\" of type \"$type\" is not supported.");
  }

  // for Java based run configurations returns runtime classpath, required to launch this configuration in JVM
/*
  void makeDependencies() {
    if (this.module != null) {
      this.module.make();
      this.module.makeTests();
    } else {
      this.project.makeAll();
    }
  }
*/
}