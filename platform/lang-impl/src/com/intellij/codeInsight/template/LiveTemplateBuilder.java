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
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class LiveTemplateBuilder {
  @NonNls private static final String END_PREFIX = "____END";

  private final StringBuilder myText = new StringBuilder();
  private final List<Variable> myVariables = new ArrayList<Variable>();
  private final Set<String> myVarNames = new HashSet<String>();
  private final List<VarOccurence> myVariableOccurences = new ArrayList<VarOccurence>();
  private final List<Marker> myMarkers = new ArrayList<Marker>();
  private String myLastEndVarName;

  public CharSequence getText() {
    return myText;
  }

  public static boolean isEndVariable(@NotNull String name) {
    return name.startsWith(END_PREFIX);
  }

  public void insertVariableSegment(int offset, String name) {
    myVariableOccurences.add(new VarOccurence(name, offset));
  }

  private static class VarOccurence {
    String myName;
    int myOffset;

    private VarOccurence(String name, int offset) {
      myName = name;
      myOffset = offset;
    }
  }

  public boolean findVarOccurence(String name) {
    for (VarOccurence occurence : myVariableOccurences) {
      if (occurence.myName.equals(name)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public TemplateImpl buildTemplate() {
    if (!findVarOccurence(TemplateImpl.END)) {
      if (myLastEndVarName == null) {
        for (Variable variable : myVariables) {
          if (isEndVariable(variable.getName())) {
            myLastEndVarName = variable.getName();
            break;
          }
        }
      }
      if (myLastEndVarName != null) {
        int endOffset = -1;
        Iterator<VarOccurence> it = myVariableOccurences.iterator();
        while (it.hasNext()) {
          VarOccurence occurence = it.next();
          if (occurence.myName.equals(myLastEndVarName)) {
            endOffset = occurence.myOffset;
            break;
          }
        }
        if (endOffset >= 0) {
          for (Iterator<Variable> it1 = myVariables.iterator(); it1.hasNext();) {
            Variable variable = it1.next();
            if (myLastEndVarName.equals(variable.getName()) && variable.isAlwaysStopAt()) {
              it.remove();
              it1.remove();
            }
          }
          myVariableOccurences.add(new VarOccurence(TemplateImpl.END, endOffset));
        }
      }
    }
    TemplateImpl template = new TemplateImpl("", "");
    for (Variable variable : myVariables) {
      template.addVariable(variable.getName(), variable.getExpressionString(), variable.getDefaultValueString(), variable.isAlwaysStopAt());
    }
    Collections.sort(myVariableOccurences, new Comparator<VarOccurence>() {
      public int compare(VarOccurence o1, VarOccurence o2) {
        if (o1.myOffset < o2.myOffset) {
          return -1;
        }
        if (o1.myOffset > o2.myOffset) {
          return 1;
        }
        return 0;
      }
    });
    int last = 0;
    for (VarOccurence occurence : myVariableOccurences) {
      template.addTextSegment(myText.substring(last, occurence.myOffset));
      template.addVariableSegment(occurence.myName);
      last = occurence.myOffset;
    }
    template.addTextSegment(myText.substring(last));
    return template;
  }

  /*private void addEndPlaceholders() {
    int[] endOffsets = myEndOffsets.toArray();
    Arrays.sort(endOffsets);
    for (int i = 0, n = endOffsets.length; i < n; i++) {
      int offset = endOffsets[i];
      if (offset < 0 || myText.length() == 0 || offset == myText.length() - 1 || hasVarAtOffset(offset)) {
        continue;
      }
      if (i < n - 1) {
        String varName = generateUniqueVarName(myVarNames);
        myVarNames.add(varName);
        myVariables.add(new Variable(varName, "", "", true));
        myVariableOccurences.add(new VarOccurence(varName, offset));
      }
      else {
        insertVariableSegment(offset, TemplateImpl.END);
      }
    }
  }*/

  public void insertText(int offset, String text, boolean disableEndVariable) {
    if (disableEndVariable) {
      String varName = null;
      for (VarOccurence occurence : myVariableOccurences) {
        if (!isEndVariable(occurence.myName)) {
          continue;
        }
        if (occurence.myOffset == offset) {
          varName = occurence.myName;
          break;
        }
      }
      if (varName != null) {
        for (Variable variable : myVariables) {
          if (varName.equals(variable.getName())) {
            variable.setAlwaysStopAt(false);
            variable.setDefaultValueString("\"\"");
            break;
          }
        }
      }
    }
    int delta = text.length();
    for (VarOccurence occurence : myVariableOccurences) {
      if (occurence.myOffset > offset || !disableEndVariable && occurence.myOffset == offset) {
        occurence.myOffset += delta;
      }
    }
    myText.insert(offset, text);
    updateMarkers(offset, text);
  }

  public int length() {
    return myText.length();
  }

  private void updateMarkers(int offset, String text) {
    for (Marker marker : myMarkers) {
      if (offset < marker.getStartOffset()) {
        marker.myStartOffset += text.length();
      }
      else if (offset <= marker.getEndOffset()) {
        marker.myEndOffset += text.length();
      }
    }
  }

  private String generateUniqueVarName(Set<String> existingNames, boolean end) {
    String prefix = end ? END_PREFIX : "VAR";
    int i = 0;
    while (myVarNames.contains(prefix + i) || existingNames.contains(prefix + i)) {
      i++;
    }
    return prefix + i;
  }

  /*private static String preslashQuotes(String s) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '"') {
        builder.append('\\');
      }
      builder.append(c);
    }
    return builder.toString();
  }*/

  public int insertTemplate(int offset, TemplateImpl template, Map<String, String> predefinedVarValues) {
    removeEndVarAtOffset(offset);

    String text = template.getTemplateText();
    insertText(offset, text, false);
    Map<String, String> newVarNames = new HashMap<String, String>();
    Set<String> oldVarNames = new HashSet<String>();
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      oldVarNames.add(varName);
    }
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      if (!TemplateImpl.INTERNAL_VARS_SET.contains(varName)) {
        if (predefinedVarValues != null && predefinedVarValues.containsKey(varName)) {
          continue;
        }
        String newVarName;
        if (myVarNames.contains(varName)) {
          oldVarNames.remove(varName);
          newVarName = generateUniqueVarName(oldVarNames, isEndVariable(varName));
          newVarNames.put(varName, newVarName);
          if (varName.equals(myLastEndVarName)) {
            myLastEndVarName = newVarName;
          }
        }
        else {
          newVarName = varName;
        }
        Variable var =
          new Variable(newVarName, template.getExpressionStringAt(i), template.getDefaultValueStringAt(i), template.isAlwaysStopAt(i));
        myVariables.add(var);
        myVarNames.add(newVarName);
      }
    }
    int end = -1;

    for (int i = 0; i < template.getSegmentsCount(); i++) {
      String segmentName = template.getSegmentName(i);
      int localOffset = template.getSegmentOffset(i);
      if (!TemplateImpl.INTERNAL_VARS_SET.contains(segmentName)) {
        if (predefinedVarValues != null && predefinedVarValues.containsKey(segmentName)) {
          String value = predefinedVarValues.get(segmentName);
          insertText(offset + localOffset, value, false);
          offset += value.length();
          continue;
        }
        if (newVarNames.containsKey(segmentName)) {
          segmentName = newVarNames.get(segmentName);
        }
        myVariableOccurences.add(new VarOccurence(segmentName, offset + localOffset));
      }
      else if (TemplateImpl.END.equals(segmentName)) {
        end = offset + localOffset;
      }
    }
    int endOffset = end >= 0 ? end : offset + text.length();
    if (endOffset > 0 &&
        endOffset != offset + text.length() &&
        endOffset < myText.length() &&
        !hasVarAtOffset(endOffset)) {
      myLastEndVarName = generateUniqueVarName(myVarNames, true);
      myVariables.add(new Variable(myLastEndVarName, "", "", true));
      myVarNames.add(myLastEndVarName);
      myVariableOccurences.add(new VarOccurence(myLastEndVarName, endOffset));
    }
    return endOffset;
  }

  private void removeEndVarAtOffset(int offset) {
    for (Iterator<VarOccurence> it = myVariableOccurences.iterator(); it.hasNext();) {
      VarOccurence occurence = it.next();
      if (!isEndVariable(occurence.myName)) {
        continue;
      }
      if (occurence.myOffset == offset) {
        it.remove();
        for (Iterator<Variable> it1 = myVariables.iterator(); it1.hasNext();) {
          Variable variable = it1.next();
          if (occurence.myName.equals(variable.getName())) {
            it1.remove();
          }
        }
      }
    }
  }

  private boolean isInEmptyText(int offset) {
    if (offset >= myText.length()) {
      return false;
    }
    char c = myText.charAt(offset++);
    while (Character.isWhitespace(c) && offset < myText.length()) {
      c = myText.charAt(offset++);
    }
    return c == '<' || c == '"';
  }

  private boolean hasVarAtOffset(int offset) {
    boolean flag = false;
    for (VarOccurence occurence : myVariableOccurences) {
      if (occurence.myOffset == offset) {
        flag = true;
      }
    }
    return flag;
  }

  public Marker createMarker(int offset) {
    Marker marker = new Marker(offset, offset);
    myMarkers.add(marker);
    return marker;
  }

  public static class Marker {
    int myStartOffset;
    int myEndOffset;

    private Marker(int startOffset, int endOffset) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
    }

    public int getStartOffset() {
      return myStartOffset;
    }

    public int getEndOffset() {
      return myEndOffset;
    }
  }
}
