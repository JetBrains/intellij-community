package org.jetbrains.jps

/**
 * @author nik
 */
class CompilerConfiguration {
  List<String> resourcePatterns = []
  List<String> resourceIncludePatterns = "properties,xml,gif,png,jpeg,jpg,jtml,dtd,tld,ftl".split(",").collect {"**/?*.$it"}
  List<String> resourceExcludePatterns = []
  Map<String, String> javacOptions = [:]
  boolean clearOutputDirectoryOnRebuild = true
  boolean addNotNullAssertions = true
  AnnotationProcessingConfiguration annotationProcessing = new AnnotationProcessingConfiguration()
  BytecodeTargetConfiguration bytecodeTarget = new BytecodeTargetConfiguration()
  CompilerExcludes excludes = new CompilerExcludes()
}

class AnnotationProcessingConfiguration {
  boolean enabled = false
  boolean obtainProcessorsFromClasspath = true
  String processorsPath
  Map<String, String> processorsOptions = [:]
  Map<String, String> processModule = [:]
}

class BytecodeTargetConfiguration {
  String projectBytecodeTarget
  Map<String, String> modulesBytecodeTarget = [:]
}