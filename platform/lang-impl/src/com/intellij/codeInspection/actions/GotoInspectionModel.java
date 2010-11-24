/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.actions;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.gotoByName.SimpleChooseByNameModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.ArrayUtil;
import org.apache.oro.text.regex.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class GotoInspectionModel extends SimpleChooseByNameModel {//implements CustomMatcherModel {
  private String myPattern;

  private Pattern myCompiledPattern;
  private final PatternMatcher myMatcher = new Perl5Matcher();
  private final Map<String, InspectionProfileEntry> myToolNames = new HashMap<String, InspectionProfileEntry>();
  private final Map<String, Set<InspectionProfileEntry>> myGroupNames = new HashMap<String, Set<InspectionProfileEntry>>();
  private final Map<String, InspectionProfileEntry> myToolShortNames = new HashMap<String, InspectionProfileEntry>();
  private String[] myNames;
  private final ListCellRenderer myListCellRenderer = new InspectionListCellRenderer();


  public GotoInspectionModel(Project project) {
    super(project, IdeBundle.message("prompt.goto.inspection.enter.name"), null); //TODO help ID
    final InspectionProfileImpl rootProfile = (InspectionProfileImpl)InspectionProfileManager.getInstance().getRootProfile();
    for (ScopeToolState state : rootProfile.getAllTools()) {
      final InspectionProfileEntry tool = state.getTool();
      if (tool instanceof LocalInspectionToolWrapper && ((LocalInspectionToolWrapper)tool).isUnfair()) {
        continue;
      }
      myToolNames.put(tool.getDisplayName(), tool);
      final String groupName = tool.getGroupDisplayName();
      Set<InspectionProfileEntry> toolsInGroup = myGroupNames.get(groupName);
      if (toolsInGroup == null) {
        toolsInGroup = new HashSet<InspectionProfileEntry>();
        myGroupNames.put(groupName, toolsInGroup);
      }
      toolsInGroup.add(tool);
      myToolShortNames.put(tool.getShortName(), tool);
    }

    final Set<String> nameIds = new HashSet<String>();
    nameIds.addAll(myToolNames.keySet());
    nameIds.addAll(myGroupNames.keySet());
    //nameIds.addAll(myToolShortNames.keySet());
    myNames = ArrayUtil.toStringArray(nameIds);
  }

  public ListCellRenderer getListCellRenderer() {
    return myListCellRenderer;
  }

  public String[] getNames() {
    return myNames;
  }

  public Object[] getElementsByName(final String id, final String pattern) {
    final Set<InspectionProfileEntry> result = new HashSet<InspectionProfileEntry>();
    InspectionProfileEntry e = myToolNames.get(id);
    if (e != null) {
      result.add(e);
    }
    e = myToolShortNames.get(id);
    if (e != null) {
      result.add(e);
    }
    final Set<InspectionProfileEntry> entries = myGroupNames.get(id);
    if (entries != null) {
      result.addAll(entries);
    }
    return result.toArray(new InspectionProfileEntry[result.size()]);
  }

  public String getElementName(final Object element) {
    if (element instanceof InspectionProfileEntry) {
      final InspectionProfileEntry entry = (InspectionProfileEntry)element;
      return entry.getDisplayName() + " " + entry.getGroupDisplayName();
    }
    return null;
  }

  private List<InspectionProfileEntry> findEntries(String name) {
    final List<InspectionProfileEntry> result = new ArrayList<InspectionProfileEntry>();
    InspectionProfileEntry e = myToolNames.get(name);
    if (e != null) {
      result.add(e);
    }
    //e = myToolShortNames.get(name);
    //if (e != null) {
    //  result.add(e);
    //}
    final Set<InspectionProfileEntry> entrySet = myGroupNames.get(name);
    if (entrySet != null) {
      result.addAll(entrySet);
    }
    return result;
  }

  public boolean matches(@NotNull final String name, @NotNull final String pattern) {
    for (InspectionProfileEntry entry : findEntries(name)) {
      final String toolName = entry.getDisplayName();
      final String id = entry.getShortName();
      final String group = entry.getGroupDisplayName();
      final Pattern compiledPattern = getPattern(pattern);
      if (myMatcher.matches(toolName, compiledPattern)
          || myMatcher.matches(id, compiledPattern)
          || myMatcher.matches(group, compiledPattern)) {
        return true;
      }
    }

    return false;
  }

  @NotNull
  private Pattern getPattern(String pattern) {
    if (!Comparing.strEqual(pattern, myPattern)) {
      myCompiledPattern = null;
      myPattern = pattern;
    }
    if (myCompiledPattern == null) {
      boolean allowToLower = true;
      final int eol = pattern.indexOf('\n');
      if (eol != -1) {
        pattern = pattern.substring(0, eol);
      }
      if (pattern.length() >= 80) {
        pattern = pattern.substring(0, 80);
      }

      final @NonNls StringBuffer buffer = new StringBuffer();

      if (containsOnlyUppercaseLetters(pattern)) {
        allowToLower = false;
      }

      if (allowToLower) {
        buffer.append(".*");
      }

      boolean firstIdentifierLetter = true;
      for (int i = 0; i < pattern.length(); i++) {
        final char c = pattern.charAt(i);
        if (Character.isLetterOrDigit(c)) {
          // This logic allows to use uppercase letters only to catch the name like PDM for PsiDocumentManager
          if (Character.isUpperCase(c) || Character.isDigit(c)) {

            if (!firstIdentifierLetter) {
              buffer.append("[^A-Z]*");
            }

            buffer.append("[");
            buffer.append(c);
            if (allowToLower || i == 0) {
              buffer.append('|');
              buffer.append(Character.toLowerCase(c));
            }
            buffer.append("]");
          }
          else if (Character.isLowerCase(c)) {
            buffer.append('[');
            buffer.append(c);
            buffer.append('|');
            buffer.append(Character.toUpperCase(c));
            buffer.append(']');
          }
          else {
            buffer.append(c);
          }

          firstIdentifierLetter = false;
        }
        else if (c == '*') {
          buffer.append(".*");
          firstIdentifierLetter = true;
        }
        else if (c == '.') {
          buffer.append("\\.");
          firstIdentifierLetter = true;
        }
        else if (c == ' ') {
          buffer.append("[^A-Z]*\\ ");
          firstIdentifierLetter = true;
        }
        else {
          firstIdentifierLetter = true;
          // for standard RegExp engine
          // buffer.append("\\u");
          // buffer.append(Integer.toHexString(c + 0x20000).substring(1));

          // for OROMATCHER RegExp engine
          buffer.append("\\x");
          buffer.append(Integer.toHexString(c + 0x20000).substring(3));
        }
      }

      buffer.append(".*");


      try {
        myCompiledPattern = new Perl5Compiler().compile(buffer.toString());
      }
      catch (MalformedPatternException e) {
        //do nothing
      }
    }

    return myCompiledPattern;
  }

  private static boolean containsOnlyUppercaseLetters(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c != '*' && c != ' ' && !Character.isUpperCase(c)) return false;
    }
    return true;
  }
}
