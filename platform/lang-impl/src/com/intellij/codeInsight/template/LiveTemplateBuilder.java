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

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class LiveTemplateBuilder {
  private final StringBuilder myText = new StringBuilder();
  private final List<Variable> myVariables = new ArrayList<Variable>();
  private final Set<String> myVarNames = new HashSet<String>();
  private final List<VarOccurence> myVariableOccurences = new ArrayList<VarOccurence>();
  private final List<Marker> myMarkers = new ArrayList<Marker>();
  private final Map<String, String> myPredefinedValues = new HashMap<String, String>();

  public CharSequence getText() {
    return myText;
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

  public TemplateImpl buildTemplate() {
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

  public Map<String, String> getPredefinedValues() {
    return myPredefinedValues;
  }

  public void insertText(int offset, String text) {
    int delta = text.length();
    for (VarOccurence occurence : myVariableOccurences) {
      if (occurence.myOffset >= offset) {
        occurence.myOffset += delta;
      }
    }
    myText.insert(offset, text);
    updateMarkers(offset, text);
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

  private String generateUniqueVarName(Set<String> existingNames) {
    String prefix = "VAR";
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
    String text = template.getTemplateText();
    insertText(offset, text);
    Map<String, String> newVarNames = new HashMap<String, String>();
    Set<String> oldVarNames = new HashSet<String>();
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      oldVarNames.add(varName);
    }
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      if (!TemplateImpl.INTERNAL_VARS_SET.contains(varName)) {
        String newVarName;
        if (myVarNames.contains(varName)) {
          oldVarNames.remove(varName);
          newVarName = generateUniqueVarName(oldVarNames);
          newVarNames.put(varName, newVarName);
        }
        else {
          newVarName = varName;
        }
        if (predefinedVarValues != null && predefinedVarValues.containsKey(varName)) {
          myPredefinedValues.put(newVarName, predefinedVarValues.get(varName));
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
        if (newVarNames.containsKey(segmentName)) {
          segmentName = newVarNames.get(segmentName);
        }
        myVariableOccurences.add(new VarOccurence(segmentName, offset + localOffset));
      }
      else if (TemplateImpl.END.equals(segmentName)) {
        end = offset + localOffset;
      }
    }
    return end >= 0 ? end : offset + text.length();
  }

  Marker createMarker(int offset) {
    Marker marker = new Marker(offset, offset);
    myMarkers.add(marker);
    return marker;
  }

  static class Marker {
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
