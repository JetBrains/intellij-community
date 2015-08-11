/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.extractor.ui;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.extractor.values.FValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Roman.Shein
 * @since 03.08.2015.
 */
public class FCodeStyleSettingsNameProvider implements CodeStyleSettingsCustomizable {

  private Map<Pair<String, String>, String> groupAndFieldToName = new LinkedHashMap<Pair<String, String>, String>();
  private Map<Pair<String, String>, CustomValueToNameContainer> groupAndFieldToNameCustom =
      new LinkedHashMap<Pair<String, String>, CustomValueToNameContainer>();
  private Map<String, List<String>> groupToFields = new LinkedHashMap<String, List<String>>();
  private Map<String, String> renamedStandardOptions = new HashMap<String, String>();

  private final CodeStyleSettings mySettings;
  private final FCodeStyleSpacesNameProvider mySpacesProvider;
  private final FCodeStyleWrapNameProvider myWrapNameProvider;
  private final FCodeStyleBlankLinesProvider myBlankLinesProvider;

  public FCodeStyleSettingsNameProvider(CodeStyleSettings settings) {
    mySettings = settings;
    mySpacesProvider = new FCodeStyleSpacesNameProvider();
    myWrapNameProvider = new FCodeStyleWrapNameProvider();
    myBlankLinesProvider = new FCodeStyleBlankLinesProvider();

  }

  @Override
  public void showAllStandardOptions() {
    //nothing to do here, all standard options are already in some map
  }

  @Override
  public void showStandardOptions(String... optionNames) {
    //nothing to do here, all standard options are already in some map
  }

  @Override
  public void showCustomOption(Class<? extends CustomCodeStyleSettings> settingsClass, String fieldName, String title, String groupName, Object... options) {
    if (options.length == 2) {
      String[] valueNames = (String[]) options[0];
      int[] values = (int[]) options[1];
      addToGroupsMap(groupAndFieldToNameCustom, groupToFields, fieldName, new CustomValueToNameContainer(title, values, valueNames), groupName);
    } else {
      addToGroupsMap(groupAndFieldToName, groupToFields, fieldName, title, groupName);
    }
  }

  @Override
  public void showCustomOption(Class<? extends CustomCodeStyleSettings> settingsClass, String fieldName, String title, String groupName, OptionAnchor anchor, String anchorFieldName, Object... options) {
    //TODO for now, ignore the anchor; it should be accounted for in actual implementation
    showCustomOption(settingsClass, fieldName, title, groupName, options);
  }

  @Override
  public void renameStandardOption(String fieldName, String newTitle) {
    renamedStandardOptions.put(fieldName, newTitle);
  }

  @Override
  public void moveStandardOption(String fieldName, String newGroup) {
    //TODO implement me
  }

  private String getSettingsTypeName(LanguageCodeStyleSettingsProvider.SettingsType settingsType) {
    switch (settingsType) {
      case BLANK_LINES_SETTINGS: return ApplicationBundle.message("title.blank.lines");
      case SPACING_SETTINGS: return ApplicationBundle.message("title.spaces");
      case WRAPPING_AND_BRACES_SETTINGS: return ApplicationBundle.message("wrapping.and.braces");
      case INDENT_SETTINGS: return ApplicationBundle.message("title.tabs.and.indents");
      case LANGUAGE_SPECIFIC: return "Language-specific"; //TODO should load from ApplciationBundle here
      default: throw new IllegalArgumentException("Unknown settings type: " + settingsType);
    }
  }

  private boolean addFields(StringBuilder builder, String groupName, List<String> fields, List<FValue> values, Map<Pair<String, String>, String> map,
                         Map<Pair<String, String>, CustomValueToNameContainer> mapCustom, String groupPrefix, String typePrefix, boolean typePrefixPrinted) {
    boolean groupPrefixAppended = false;
    for (String field : fields) {
      for (FValue value: values) {
        if (value.state == FValue.STATE.SELECTED && value.name.equals(field)) {
          String name = null;
          String valueName = null;
          Pair<String, String> myPair = Pair.create(groupName, field);
          if (map != null) {
            name = map.get(myPair);
            if (name != null) {
              valueName = value.value.toString();
            }
          }
          if (name == null && mapCustom != null) {
            CustomValueToNameContainer container = mapCustom.get(myPair);
            if (container != null) {
              name = container.myName;
              valueName = container.getNameForValue((Integer) value.value);
            }
          }
          String renamed = renamedStandardOptions.get(field);
          if (renamed != null) {
            name = renamed;
          }
          if (name != null && valueName != null) {
            if (!typePrefixPrinted) {
              builder.append(typePrefix);
              typePrefixPrinted = true;
            }
            if (groupName != null && groupPrefix != null && !groupPrefixAppended) {
              builder.append(groupPrefix);
              groupPrefixAppended = true;
            }
            String postNameSign = name.endsWith(":") ?  " " : ": ";
            builder.append("<br>").append(groupName == null ? "<b>" + name + "</b>" : name).append(postNameSign).append(valueName);
          } else {
            System.err.println("Failed to deduce name for field " + field + " in group " + groupName); //TODO switch to more robust logging
          }
        }
      }
    }
    return typePrefixPrinted;
  }

  private void addSettings(List<FValue> values, StringBuilder builder, Map<Pair<String, String>, String> map,
                           Map<Pair<String, String>, CustomValueToNameContainer> mapCustom, Map<String,
                           List<String>> defaultGroupToFields, String typePrefix) {
    assert(defaultGroupToFields != groupToFields);
    boolean typePrefixPrinted = false;
    //process default groups
    for (String group: defaultGroupToFields.keySet()) {
      //TODO the order gets messed up because of lack of decent API; ignore it for now
      typePrefixPrinted = addFields(builder, group, defaultGroupToFields.get(group), values, map, mapCustom,
          "<br><b>" + group + "</b>", typePrefix, typePrefixPrinted) | typePrefixPrinted;
      //now, process custom settings for the group
      if (groupToFields.containsKey(group)) {
        typePrefixPrinted = addFields(builder, group, groupToFields.get(group), values, groupAndFieldToName,
            groupAndFieldToNameCustom, null, typePrefix, typePrefixPrinted) | typePrefixPrinted;
        //remove the processed group from custom groups to avoid duplication
        groupToFields.remove(group);
      }
    }
    //process custom groups
    for (String group: groupToFields.keySet()) {
      builder.append("<br><b>").append(group).append("</b>");
      typePrefixPrinted = addFields(builder, group, groupToFields.get(group), values, groupAndFieldToName,
          groupAndFieldToNameCustom,  "<br><b>" + group + "</b>", typePrefix, typePrefixPrinted) | typePrefixPrinted;
    }
  }

  public void addSettings(StringBuilder stringBuilder, List<FValue> values, LanguageCodeStyleSettingsProvider provider) {
    for (LanguageCodeStyleSettingsProvider.SettingsType settingsType : LanguageCodeStyleSettingsProvider.SettingsType.values()) {
      groupAndFieldToName.clear();
      groupAndFieldToNameCustom.clear();
      groupToFields.clear();

      String typePrefix = "<br><b><u>" + getSettingsTypeName(settingsType) + "</u></b>";
//      stringBuilder.append("<br><b><u>").append(getSettingsTypeName(settingsType)).append("</u></b>");

      provider.customizeSettings(this, settingsType);
      //TODO there is a lot of duplication that should go away once there is a stable API in the IDEA core for field name and value extraction
      switch (settingsType) {
        case SPACING_SETTINGS:
          addSettings(values, stringBuilder, mySpacesProvider.getGroupAndFieldToName(), null, mySpacesProvider.getGroupToFields(), typePrefix);
          break;
        case BLANK_LINES_SETTINGS:
          addSettings(values, stringBuilder, myBlankLinesProvider.getGroupAndFieldToName(), null, myBlankLinesProvider.getGroupToFields(), typePrefix);
          break;
        case WRAPPING_AND_BRACES_SETTINGS:
          addSettings(values, stringBuilder, myWrapNameProvider.getGroupAndFieldToName(), myWrapNameProvider.getGroupAndFieldToNameCustom(), myWrapNameProvider.getGroupToFields(), typePrefix);
          break;
        case INDENT_SETTINGS:
          //TODO probably this should be unified somehow
          Map<Pair<String, String>, String> map = new LinkedHashMap<Pair<String, String>, String>();
          Map<String, List<String>> groupMap = new LinkedHashMap<String, List<String>>();
          String groupName = null;//getSettingsTypeName(settingsType);
          map.put(Pair.create(groupName, "INDENT_SIZE"), ApplicationBundle.message("editbox.indent.indent"));
          map.put(Pair.create(groupName, "CONTINUATION_INDENT_SIZE"), ApplicationBundle.message("editbox.indent.continuation.indent"));
          map.put(Pair.create(groupName, "TAB_SIZE"), ApplicationBundle.message("editbox.indent.tab.size"));
          List<String> fields = new LinkedList<String>();
          Collections.addAll(fields, "INDENT_SIZE", "CONTINUATION_INDENT_SIZE", "TAB_SIZE");
          groupMap.put(groupName, fields);
          addSettings(values, stringBuilder, map, null, groupMap, typePrefix);
          break;
        default:
          addSettings(values, stringBuilder, null, null, Collections.EMPTY_MAP, typePrefix);
          break;
      }
    }
  }

  public static <T> void addToGroupsMap(@NotNull Map<Pair<String, String>, T> groupToFieldMap,
                                        @NotNull Map<String, List<String>> groupToFields,
                                        @NonNls String fieldName, T title, String groupName) {
    if (!groupToFields.keySet().contains(groupName)) {
      groupToFields.put(groupName, new LinkedList<String>());
    }
    groupToFields.get(groupName).add(fieldName);
    groupToFieldMap.put(Pair.create(groupName, fieldName), title);
  }

  public static class CustomValueToNameContainer {
    public final String myName;
    private final int[] myValues;
    private final String[] myValueNames;

    public CustomValueToNameContainer(String name, int[] values, String[] valueNames) {
      myName = name;
      myValues = values;
      myValueNames = valueNames;
    }

    public String getNameForValue(int value) {
      for (int i = 0; i < myValues.length; ++i) {
        if (myValues[i] == value) {
          return myValueNames[i];
        }
      }
      throw new IllegalArgumentException("Unexpected value " + value);
    }
  }
}
