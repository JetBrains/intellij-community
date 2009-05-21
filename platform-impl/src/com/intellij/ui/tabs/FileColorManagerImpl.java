package com.intellij.ui.tabs;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FileColorManager;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author spleaner
 */
@State(
  name = "FileColors",
  storages = {@Storage(
    id = "other",
    file = "$WORKSPACE_FILE$")})
public class FileColorManagerImpl extends FileColorManager implements PersistentStateComponent<Element> {
  private boolean myEnabled = false;

  private FileColorsModel myModel;

  private FileColorSharedConfigurationManager mySharedConfigurationManager;

  private Project myProject;
  private boolean myEnabledForTabs;

  private static Map<String, Color> ourDefaultColors;

  static {
    ourDefaultColors = new LinkedHashMap<String, Color>();
    ourDefaultColors.put("Blue", new Color(215, 237, 243));
    //ourDefaultColors.put("Blue 2", new Color(218, 224, 244));
    ourDefaultColors.put("Green", new Color(228, 241, 209));
    //ourDefaultColors.put("Green 2", new Color(223, 235, 226));
    ourDefaultColors.put("Orange", new Color(246, 224, 202));
    ourDefaultColors.put("Rose", new Color(242, 206, 202));
    ourDefaultColors.put("Violet", new Color(222, 213, 241));
    ourDefaultColors.put("Yellow", new Color(247, 241, 203));
  }

  public FileColorManagerImpl(@NotNull final Project project) {
    myProject = project;
    myModel = new FileColorsModel();
  }

  private void initSharedConfigurations() {
    if (mySharedConfigurationManager == null) {
      mySharedConfigurationManager = ServiceManager.getService(myProject, FileColorSharedConfigurationManager.class);
    }
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void setEnabledForTabs(boolean b) {
    myEnabledForTabs = b;
  }

  public boolean isEnabledForTabs() {
    return myEnabledForTabs;
  }

  public void addColoredFile(@NotNull final VirtualFile file, @NotNull final String colorName) {
    myModel.add(file, colorName);
  }

  public void removeColoredFile(@NotNull final VirtualFile file) {
    myModel.remove(file);
  }

  public void setShared(@NotNull final VirtualFile file, final boolean shared) {
    final String colorName = myModel.getColor(file, true);
    if (colorName != null) {
      final Color color = getColor(colorName);
      if (color != null) {
        myModel.setShared(file, shared);
      }
    }
  }

  public Element getState(final boolean shared) {
    Element element = new Element("state");
    if (!shared) {
      element.setAttribute("enabled", Boolean.toString(myEnabled));
      element.setAttribute("enabledForTabs", Boolean.toString(myEnabledForTabs));
    }

    myModel.save(element, shared);

    return element;
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  @Nullable
  public Color getColor(@NotNull final String name) {
    return ourDefaultColors.get(name);
  }

  public static String toString(final Color color) {
    return Integer.toHexString((color.getRGB() & 0xffffff) | 0x1000000).substring(1);
  }

  public static Color fromString(final String hex) throws NumberFormatException {
    return Color.decode(String.format("#%s", hex));
  }

  public Element getState() {
    initSharedConfigurations();
    return getState(false);
  }

  @SuppressWarnings({"AutoUnboxing"})
  void loadState(Element state, final boolean shared) {
    if (!shared) {
      final String enabled = state.getAttributeValue("enabled");
      myEnabled = enabled == null ? true : Boolean.valueOf(enabled);

      final String enabledForTabs = state.getAttributeValue("enabledForTabs");
      myEnabledForTabs = enabledForTabs == null ? false : Boolean.valueOf(enabledForTabs);
    }

    myModel.load(state, shared);
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  public Collection<String> getColorNames() {
    final Set<String> names = ourDefaultColors.keySet();
    final List<String> sorted = new ArrayList<String>(names);
    Collections.sort(sorted);
    return sorted;
  }

  @SuppressWarnings({"AutoUnboxing"})
  public void loadState(Element state) {
    initSharedConfigurations();
    loadState(state, false);
  }

  @Nullable
  public Color getFileColor(@NotNull final VirtualFile file, final boolean strict) {
    final String colorName = myModel.getColor(file, strict);
    return colorName == null ? null : ourDefaultColors.get(colorName);
  }

  @Nullable
  public Color getFileColor(@NotNull final VirtualFile file) {
    final String colorName = getColorName(file);
    return colorName == null ? null : ourDefaultColors.get(colorName);
  }

  @Nullable
  public String getColorName(VirtualFile file) {
    return myModel.getColor(file, false);
  }

  public boolean isShared(@NotNull VirtualFile virtualFile) {
    return myModel.isShared(virtualFile);
  }

  FileColorsModel getModel() {
    return myModel;
  }

  boolean isShared(FileColorConfiguration configuration) {
    return myModel.isShared(configuration);
  }

  public Project getProject() {
    return myProject;
  }
}
