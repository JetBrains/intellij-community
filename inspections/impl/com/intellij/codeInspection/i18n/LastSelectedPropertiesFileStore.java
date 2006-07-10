package com.intellij.codeInspection.i18n;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiFile;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class LastSelectedPropertiesFileStore implements ApplicationComponent, JDOMExternalizable {
  private final Map<String, String> lastSelectedUrls = new THashMap<String, String>();
  private String lastSelectedFileUrl;

  public static LastSelectedPropertiesFileStore getInstance() {
    return ApplicationManager.getApplication().getComponent(LastSelectedPropertiesFileStore.class);
  }

  @NonNls
  public String getComponentName() {
    return "LastSelectedPropertiesFileStore";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public String suggestLastSelectedPropertiesFileUrl(PsiFile context) {
    VirtualFile virtualFile = context.getContainingDirectory().getVirtualFile();

    while (virtualFile != null) {
      String contextUrl = virtualFile.getUrl();
      String url = lastSelectedUrls.get(contextUrl);
      if (url != null) {
        return url;
      }
      virtualFile = virtualFile.getParent();
    }
    if (lastSelectedFileUrl != null) {
      VirtualFile lastFile = VirtualFileManager.getInstance().findFileByUrl(lastSelectedFileUrl);
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(context.getProject()).getFileIndex();
      if (lastFile != null && ModuleUtil.findModuleForPsiElement(context) == fileIndex.getModuleForFile(lastFile)) {
        return lastSelectedFileUrl;
      }
    }
    return null;
  }

  public void saveLastSelectedPropertiesFile(PsiFile context, PropertiesFile file) {
    VirtualFile virtualFile = context.getContainingDirectory().getVirtualFile();
    String contextUrl = virtualFile.getUrl();
    String url = file.getVirtualFile().getUrl();
    lastSelectedUrls.put(contextUrl, url);
    lastSelectedFileUrl = url;
  }

  public void readExternal(@NonNls Element element) throws InvalidDataException {
    lastSelectedUrls.clear();
    List list = element.getChildren("entry");
    for (Object o : list) {
      @NonNls Element child = (Element)o;
      String context = child.getAttributeValue("context");
      String url = child.getAttributeValue("url");
      VirtualFile propFile = VirtualFileManager.getInstance().findFileByUrl(url);
      VirtualFile contextFile = VirtualFileManager.getInstance().findFileByUrl(context);
      if (propFile != null && contextFile != null) {
        lastSelectedUrls.put(context, url);
      }
    }
    lastSelectedFileUrl = element.getAttributeValue("lastSelectedFileUrl");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (Map.Entry<String, String> entry : lastSelectedUrls.entrySet()) {
      String context = entry.getKey();
      String url = entry.getValue();
      @NonNls Element child = new Element("entry");
      child.setAttribute("context", context);
      child.setAttribute("url", url);
      element.addContent(child);
    }
    if (lastSelectedFileUrl != null) {
      element.setAttribute("lastSelectedFileUrl", lastSelectedFileUrl);
    }
  }
}
