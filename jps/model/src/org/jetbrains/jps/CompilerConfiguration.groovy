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
  AnnotationProcessingProfile defaultAnnotationProcessingProfile = new AnnotationProcessingProfile()
  Collection<AnnotationProcessingProfile> moduleAnnotationProcessingProfiles = []
  BytecodeTargetConfiguration bytecodeTarget = new BytecodeTargetConfiguration()
  CompilerExcludes excludes = new CompilerExcludes()
}

class AnnotationProcessingProfile {
  String name = ""
  boolean enabled = false
  boolean obtainProcessorsFromClasspath = true
  String processorsPath
  List<String> processors = []
  Map<String, String> processorsOptions = [:]
  String generatedSourcesDirName = ""
  List<String> processModule = []
}

class BytecodeTargetConfiguration {
  String projectBytecodeTarget
  Map<String, String> modulesBytecodeTarget = [:]
}