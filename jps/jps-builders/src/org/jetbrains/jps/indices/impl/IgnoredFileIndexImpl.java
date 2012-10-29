package org.jetbrains.jps.indices.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.model.JpsModel;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author nik
 */
public class IgnoredFileIndexImpl implements IgnoredFileIndex {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.IgnoredFilePatterns");
  private List<Pattern> myPatterns = new ArrayList<Pattern>();

  public IgnoredFileIndexImpl(JpsModel model) {
    loadFromString(model.getGlobal().getFileTypesConfiguration().getIgnoredPatternString());
  }

  private void loadFromString(String patterns) {
    myPatterns.clear();
    StringTokenizer tokenizer = new StringTokenizer(patterns, ";");
    while (tokenizer.hasMoreTokens()) {
      String pattern = tokenizer.nextToken();
      if (!StringUtil.isEmptyOrSpaces(pattern)) {
        try {
          myPatterns.add(Pattern.compile(convertToJavaPattern(pattern), SystemInfoRt.isFileSystemCaseSensitive? 0 : Pattern.CASE_INSENSITIVE));
        }
        catch (PatternSyntaxException e) {
          LOG.info("Cannot load ignored file pattern " + pattern, e);
        }
      }
    }
  }

  @Override
  public boolean isIgnored(String fileName) {
    for (Pattern pattern : myPatterns) {
      if (pattern.matcher(fileName).matches()) {
        return true;
      }
    }
    return false;
  }

  private static String convertToJavaPattern(String wildcardPattern) {
    wildcardPattern = StringUtil.replace(wildcardPattern, ".", "\\.");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*?", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?*", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*", ".*");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?", ".");
    return wildcardPattern;
  }
}
