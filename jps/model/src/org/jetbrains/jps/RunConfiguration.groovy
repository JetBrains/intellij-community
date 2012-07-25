package org.jetbrains.jps

import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.module.JpsModuleReference
/**
 * Represents IntelliJ IDEA run configuration.
 * @author pavel.sher
 */
public class RunConfiguration {
  final Project project;
  final String name;
  final String type;
  final JpsModuleReference moduleRef;
  final String workingDir;
  final Map<String, String> allOptions;
  final Map<String, String> envVars;
  final Node node;
  final MacroExpander macroExpander;

  def RunConfiguration(Project project, MacroExpander macroExpander, Node confTag) {
    this.project = project;
    this.name = confTag.'@name';
    this.type = confTag.'@type';
    this.node = confTag;

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
      this.moduleRef = JpsElementFactory.instance.createModuleReference(moduleNode.'@name');
    } else {
      this.moduleRef = null;
    }

    this.macroExpander = macroExpander;

    def String workDirUrl = this.allOptions['WORKING_DIRECTORY'];
    if (workDirUrl == null) workDirUrl = "";
    if (workDirUrl != '') {
      workDirUrl = this.macroExpander.expandMacros(JpsPathUtil.urlToPath(workDirUrl));
    }

    this.workingDir = workDirUrl == '' ? new File(".").getCanonicalPath() : new File(workDirUrl).getCanonicalPath();

    this.envVars = [:];
    confTag.envs.env.each{ el ->
      this.envVars[el.'@name'] = el.'@value';
    }
  }
}