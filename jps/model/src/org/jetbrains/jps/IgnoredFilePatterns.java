package org.jetbrains.jps;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author nik
 */
public class IgnoredFilePatterns {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.IgnoredFilePatterns");
  private List<Pattern> myPatterns = new ArrayList<Pattern>();

  public IgnoredFilePatterns() {
    loadFromString("CVS;SCCS;RCS;rcs;.DS_Store;.svn;.pyc;.pyo;*.pyc;*.pyo;.git;*.hprof;_svn;.hg;*.lib;*~;__pycache__;.bundle;vssver.scc;vssver2.scc;*.rbc;");
  }

  public void loadFromString(String patterns) {
    myPatterns.clear();
    StringTokenizer tokenizer = new StringTokenizer(patterns, ";");
    while (tokenizer.hasMoreTokens()) {
      String pattern = tokenizer.nextToken();
      if (!StringUtil.isEmptyOrSpaces(pattern)) {
        try {
          myPatterns.add(Pattern.compile(convertToJavaPattern(pattern)));
        }
        catch (PatternSyntaxException e) {
          LOG.info("Cannot load ignored file pattern " + pattern, e);
        }
      }
    }
  }

  public boolean isIgnored(String fileName) {
    for (Pattern pattern : myPatterns) {
      if (pattern.matcher(fileName).matches()) {
        return true;
      }
    }
    return false;
  }

  public static String convertToJavaPattern(String wildcardPattern) {
    wildcardPattern = StringUtil.replace(wildcardPattern, ".", "\\.");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*?", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?*", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*", ".*");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?", ".");
    return wildcardPattern;
  }
}
