package org.jetbrains.jps

/**
 * @author nik
 */
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
