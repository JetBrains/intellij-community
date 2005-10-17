package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HighlightingSettingsPerFile implements JDOMExternalizable, ProjectComponent {
  @NonNls private static final String PROFILES_TAG = "profiles";
  @NonNls private static final String SETTING_TAG = "setting";
  @NonNls private static final String ROOT_ATT_PREFIX = "root";
  @NonNls private static final String FILE_ATT = "file";
  @NonNls private static final String PROFILE_NAME_ATT = "profile_name";
  @NonNls private static final String IS_ACTIVE_ATT = "is_active";

  public static HighlightingSettingsPerFile getInstance(Project progect){
    return progect.getComponent(HighlightingSettingsPerFile.class);
  }

  private Map<VirtualFile, FileHighlighingSetting[]> myHighlightSettings = new HashMap<VirtualFile, FileHighlighingSetting[]>();
  private Map<Language, FileHighlighingSetting[]> myHighlightDefaults = new HashMap<Language, FileHighlighingSetting[]>();
  private Map<VirtualFile, Pair<String,Boolean>> myProfileSettings = new HashMap<VirtualFile, Pair<String,Boolean>>();

  public FileHighlighingSetting getHighlightingSettingForRoot(PsiElement root){
    final PsiFile containingFile = root.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    FileHighlighingSetting[] fileHighlighingSettings = myHighlightSettings.get(virtualFile);
    if(fileHighlighingSettings == null){
      fileHighlighingSettings = getDefaults(containingFile.getLanguage());
    }
    return fileHighlighingSettings[PsiUtil.getRootIndex(root)];
  }

  public FileHighlighingSetting[] getDefaults(Language lang){
    if(myHighlightDefaults.containsKey(lang)) return myHighlightDefaults.get(lang);
    final FileHighlighingSetting[] fileHighlighingSettings = new FileHighlighingSetting[PsiUtil.getRootsCount(lang)];
    for (int i = 0; i < fileHighlighingSettings.length; i++) {
      fileHighlighingSettings[i] = FileHighlighingSetting.FORCE_HIGHLIGHTING;
    }
    return fileHighlighingSettings;
  }

  public void setHighlightingSettingForRoot(PsiElement root, FileHighlighingSetting setting){
    final PsiFile containingFile = root.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return;
    FileHighlighingSetting[] defaults = myHighlightSettings.get(virtualFile);
    if(defaults == null) defaults = getDefaults(containingFile.getLanguage()).clone();
    defaults[PsiUtil.getRootIndex(root)] = setting;
    boolean toRemove = true;
    for (FileHighlighingSetting aDefault : defaults) {
      if (aDefault != FileHighlighingSetting.NONE) toRemove = false;
    }
    if(!toRemove) myHighlightSettings.put(virtualFile, defaults);
    else myHighlightSettings.remove(virtualFile);
  }

  public void projectOpened() {}
  public void projectClosed() {}
  public String getComponentName() {
    return "HighlightingSettingsPerFile";
  }

  public void initComponent() {}
  public void disposeComponent() {}

  public void readExternal(Element element) throws InvalidDataException {
    List children = element.getChildren(SETTING_TAG);
    for (final Object aChildren : children) {
      final Element child = (Element)aChildren;
      final String url = child.getAttributeValue(FILE_ATT);
      if (url == null) continue;
      final VirtualFile fileByUrl = VirtualFileManager.getInstance().findFileByUrl(url);
      if (fileByUrl != null) {
        final List<FileHighlighingSetting> settings = new ArrayList<FileHighlighingSetting>();
        int index = 0;
        while (child.getAttributeValue(ROOT_ATT_PREFIX + index) != null) {
          final String attributeValue = child.getAttributeValue(ROOT_ATT_PREFIX + index++);
          settings.add(Enum.valueOf(FileHighlighingSetting.class, attributeValue));
        }
        myHighlightSettings.put(fileByUrl, settings.toArray(new FileHighlighingSetting[settings.size()]));
      }
    }
    children = element.getChildren(PROFILES_TAG);
    for (final Object aChildren : children) {
      final Element child = (Element)aChildren;
      final VirtualFile fileByUrl = VirtualFileManager.getInstance().findFileByUrl(child.getAttributeValue(FILE_ATT));
      if (fileByUrl != null) {
        final String isActive = child.getAttributeValue(IS_ACTIVE_ATT);
        final Boolean second = isActive != null ? Boolean.valueOf(isActive) : Boolean.TRUE;
        myProfileSettings.put(fileByUrl, Pair.create(child.getAttributeValue(PROFILE_NAME_ATT), second));
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (myHighlightSettings.isEmpty()) throw new WriteExternalException();
    for (Map.Entry<VirtualFile, FileHighlighingSetting[]> entry : myHighlightSettings.entrySet()) {
      final Element child = new Element(SETTING_TAG);

      final VirtualFile vFile = entry.getKey();
      if (!vFile.isValid()) continue;
      child.setAttribute(FILE_ATT, vFile.getUrl());
      for (int i = 0; i < entry.getValue().length; i++) {
        final FileHighlighingSetting fileHighlighingSetting = entry.getValue()[i];
        child.setAttribute(ROOT_ATT_PREFIX + i, fileHighlighingSetting.toString());
      }
      element.addContent(child);
    }
    for (Map.Entry<VirtualFile, Pair<String,Boolean>> entry : myProfileSettings.entrySet()) {
      final Pair<String, Boolean> value = entry.getValue();
      if (value != null) {
        final String name = value.first;
        final Element child = new Element(PROFILES_TAG);
        final VirtualFile vFile = entry.getKey();
        if (vFile == null || !vFile.isValid()) continue;
        child.setAttribute(FILE_ATT, vFile.getUrl());
        child.setAttribute(PROFILE_NAME_ATT, name);
        child.setAttribute(IS_ACTIVE_ATT, value.second.toString());
        element.addContent(child);
      }
    }
  }

  @Nullable
  public Pair<String, Boolean> getInspectionProfile(PsiElement psiRoot) {
    final PsiFile containingFile = psiRoot.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    return myProfileSettings.get(virtualFile);
  }

  public void setInspectionProfile(String name, boolean isActive, PsiElement psiRoot){
     if (psiRoot != null){
       final PsiFile file = psiRoot.getContainingFile();
       final VirtualFile vFile = file.getVirtualFile();
       if (vFile != null) {
         myProfileSettings.put(vFile, Pair.create(name, Boolean.valueOf(isActive)));
       }
     }
  }

  public void resetAllFilesToUseGlobalSettings(){
    myProfileSettings.clear();
    myHighlightSettings.clear();
  }
}
