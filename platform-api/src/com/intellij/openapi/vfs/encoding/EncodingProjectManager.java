/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 17, 2007
 * Time: 3:20:51 PM
 */
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@State(
  name = "Encoding",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$"),
    @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/encodings.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class EncodingProjectManager extends EncodingManager implements ProjectComponent, PersistentStateComponent<Element> {
  private final Project myProject;

  public EncodingProjectManager(Project project) {
    myProject = project;
  }

  public static EncodingProjectManager getInstance(Project project) {
    return project.getComponent(EncodingProjectManager.class);
  }

  //null key means project
  private final Map<VirtualFile, Charset> myMapping = new HashMap<VirtualFile, Charset>();

  public Element getState() {
    Element element = new Element("x");
    for (VirtualFile file : myMapping.keySet()) {
      Charset charset = myMapping.get(file);
      Element child = new Element("file");
      element.addContent(child);
      child.setAttribute("url", file == null ? "PROJECT" : file.getUrl());
      child.setAttribute("charset", charset.name());
    }
    return element;
  }

  public void loadState(Element state) {
    List<Element> files = state.getChildren("file");
    for (Element fileElement : files) {
      String url = fileElement.getAttributeValue("url");
      String charsetName = fileElement.getAttributeValue("charset");
      Charset charset = CharsetToolkit.forName(charsetName);
      VirtualFile file = url.equals("PROJECT") ? null : VirtualFileManager.getInstance().findFileByUrl(url);
      if (file != null || url.equals("PROJECT")) {
        myMapping.put(file, charset);
      }
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "EncodingProjectManager";
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  @Nullable
  public Charset getEncoding(@NotNull VirtualFile virtualFile, boolean useParentDefaults) {
    VirtualFile parent = virtualFile;
    while (true) {
      Charset charset = myMapping.get(parent);
      if (charset != null || !useParentDefaults) return charset;
      if (parent == null) break;
      parent = parent.getParent();
    }
    return null;
  }

  public void setEncoding(@Nullable VirtualFile virtualFileOrDir, @Nullable Charset charset) {
    if (charset == null) {
      myMapping.remove(virtualFileOrDir);
    }
    else {
      myMapping.put(virtualFileOrDir, charset);
    }
    if (virtualFileOrDir != null && !virtualFileOrDir.isDirectory()) {
      virtualFileOrDir.setCharset(charset);
      virtualFileOrDir.setBOM(null);
      FileDocumentManager documentManager = FileDocumentManager.getInstance();
      if (documentManager.isFileModified(virtualFileOrDir)) {
        Document document = documentManager.getDocument(virtualFileOrDir);
        if (document != null) {
          documentManager.saveDocument(document);
        }
      }
      else {
        ((VirtualFileListener)documentManager).contentsChanged(new VirtualFileEvent(null, virtualFileOrDir, virtualFileOrDir.getName(), virtualFileOrDir.getParent()));
      }
    }
  }

  @NotNull
  public Collection<Charset> getFavorites() {
    Set<Charset> result = new THashSet<Charset>();
    result.addAll(myMapping.values());
    result.add(CharsetToolkit.UTF8_CHARSET);
    result.add(CharsetToolkit.getDefaultSystemCharset());
    result.add(CharsetToolkit.getIDEOptionsCharset());
    return result;
  }

  public Map<VirtualFile, Charset> getAllMappings() {
    return myMapping;
  }

  public void setMapping(final Map<VirtualFile, Charset> result) {
    Map<VirtualFile, Charset> map = new HashMap<VirtualFile, Charset>(result);
    //todo return it back as soon as FileIndex get to the platfrom
    //ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    //for (VirtualFile file : result.keySet()) {
    //  if (file != null && !fileIndex.isInContent(file)) {
    //    map.remove(file);
    //  }
    //}
    myMapping.clear();
    myMapping.putAll(map);
  }
}
