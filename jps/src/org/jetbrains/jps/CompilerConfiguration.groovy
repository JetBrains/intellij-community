package org.jetbrains.jps

/**
 * @author nik
 */
class CompilerConfiguration {
  List<String> resourceIncludePatterns = "properties,xml,gif,png,jpeg,jpg,jtml,dtd,tld,ftl".split(",").collect {"**/?*.$it"}
  List<String> resourceExcludePatterns = []
  Map<String, String> javacOptions = [:]

}
