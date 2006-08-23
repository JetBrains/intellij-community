package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.SystemProperties;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashMap;

/**
 * User: anna
 * Date: Dec 20, 2004
 */
public class InspectionProfileConvertor {
  private HashMap<String, HighlightDisplayLevel> myDisplayLevelMap = new HashMap<String, HighlightDisplayLevel>();
  public static final @NonNls String OLD_HIGHTLIGHTING_SETTINGS_PROFILE = "EditorHighlightingSettings";
  public static final @NonNls String OLD_DEFAUL_PROFILE = "OldDefaultProfile";

  private String myAdditionalJavadocTags;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettingsConvertor");

  @NonNls private static final String INSPECTIONS_TAG = "inspections";
  @NonNls private static final String NAME_ATT = "name";
  @NonNls private static final String INSP_TOOL_TAG = "inspection_tool";
  @NonNls private static final String CLASS_ATT = "class";
  @NonNls private static final String VERSION_ATT = "version";
  @NonNls private static final String PROFILE_NAME_ATT = "profile_name";
  @NonNls private static final String OPTION_TAG = "option";
  @NonNls private static final String DISPLAY_LEVEL_MAP_OPTION = "DISPLAY_LEVEL_MAP";
  @NonNls private static final String VALUE_ATT = "value";
  @NonNls private static final String ADDITONAL_JAVADOC_TAGS_OPTION = "ADDITIONAL_JAVADOC_TAGS";
  @NonNls private static final String DEFAULT_XML = "Default.xml";
  @NonNls private static final String XML_EXTENSION = ".xml";
  @NonNls public static final String LEVEL_ATT = "level";
  private InspectionProfileManager myManager;

  public InspectionProfileConvertor(InspectionProfileManager manager) {
    myManager = manager;
    renameOldDefaultsProfile();
  }

  private boolean retrieveOldSettings(Element element) {
    boolean hasOldSettings = false;
    for (final Object obj : element.getChildren(OPTION_TAG)) {
      Element option = (Element)obj;
      final String name = option.getAttributeValue(NAME_ATT);
      if (name != null) {
        if (name.equals(DISPLAY_LEVEL_MAP_OPTION)) {
          final Element levelMap = option.getChild(VALUE_ATT);
          for (final Object o : levelMap.getChildren()) {
            Element e = (Element)o;
            String key = e.getName();
            String levelName = e.getAttributeValue(LEVEL_ATT);
            HighlightDisplayLevel level = HighlightDisplayLevel.find(levelName);
            if (level == null) continue;
            myDisplayLevelMap.put(key, level);
          }
          hasOldSettings = true;
        }
        else {
          if (name.equals(ADDITONAL_JAVADOC_TAGS_OPTION)) {
            myAdditionalJavadocTags = option.getAttributeValue(VALUE_ATT);
            hasOldSettings = true;
          }
        }
      }
    }
    return hasOldSettings;
  }

  public void storeEditorHighlightingProfile(Element element) {
    if (retrieveOldSettings(element)) {
      final InspectionProfileImpl editorProfile = new InspectionProfileImpl(OLD_HIGHTLIGHTING_SETTINGS_PROFILE);

      final ModifiableModel editorProfileModel = editorProfile.getModifiableModel();

      fillErrorLevels(editorProfileModel);
      editorProfileModel.commit(myManager);
    }
  }

  public static Element convertToNewFormat(File profileFile, InspectionProfile profile) throws IOException, JDOMException {
    Element rootElement = new Element(INSPECTIONS_TAG);
    rootElement.setAttribute(NAME_ATT, profile.getName());
    final InspectionProfileEntry[] tools = profile.getInspectionTools();
    final Document document = JDOMUtil.loadDocument(profileFile);
    for (final Object o : document.getRootElement().getChildren(INSP_TOOL_TAG)) {
      Element toolElement = (Element)((Element)o).clone();
      String toolClassName = toolElement.getAttributeValue(CLASS_ATT);
      final String shortName = convertToShortName(toolClassName, tools);
      if (shortName == null) {
        continue;
      }
      toolElement.setAttribute(CLASS_ATT, shortName);
      rootElement.addContent(toolElement);
    }
    return rootElement;
  }

  private void renameOldDefaultsProfile() {
    final File profileDirectory = myManager.getProfileDirectory();
    final File[] files = profileDirectory.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        return pathname.getPath().endsWith(File.separator + DEFAULT_XML);
      }
    });
    if (files == null || files.length != 1) {
      return;
    }
    final File dest = new File(profileDirectory, OLD_DEFAUL_PROFILE + XML_EXTENSION);
    try {
      Document doc = JDOMUtil.loadDocument(files[0]);
      Element root = doc.getRootElement();
      if (root.getAttributeValue(VERSION_ATT) == null){
        root.setAttribute(PROFILE_NAME_ATT, OLD_DEFAUL_PROFILE);
        JDOMUtil.writeDocument(doc, dest, SystemProperties.getLineSeparator());
        files[0].delete();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (JDOMException e) {
      LOG.error(e);
    }
  }

  private void fillErrorLevels(final ModifiableModel profile) {
    InspectionProfileEntry[] tools = profile.getInspectionTools();
    LOG.assertTrue(tools != null, "Profile was not correctly init");
    //fill error levels
    for (final String shortName : myDisplayLevelMap.keySet()) {
      //key <-> short name
      HighlightDisplayLevel level = myDisplayLevelMap.get(shortName);

      HighlightDisplayKey key = HighlightDisplayKey.find(shortName);

      if (key == null) continue;

      //set up tools for default profile
      if (level != HighlightDisplayLevel.DO_NOT_SHOW) {
        profile.enableTool(shortName);
      }

      if (level == null || level == HighlightDisplayLevel.DO_NOT_SHOW) {
        level = HighlightDisplayLevel.WARNING;
      }
      profile.setErrorLevel(key, level);
    }
    //javadoc attributes
    final InspectionProfileEntry inspectionTool = profile.getInspectionTool(JavaDocLocalInspection.SHORT_NAME);
    JavaDocLocalInspection inspection = (JavaDocLocalInspection)((LocalInspectionToolWrapper)inspectionTool).getTool();
    inspection.myAdditionalJavadocTags = myAdditionalJavadocTags;
  }


  @Nullable
  private static String convertToShortName(String displayName, InspectionProfileEntry[] tools) {
    if (displayName == null) return null;
    for (InspectionProfileEntry tool : tools) {
      if (displayName.equals(tool.getDisplayName())) {
        return tool.getShortName();
      }
    }
    return null;
  }

}
