package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * @author cdr
 */
public abstract class EncodingProjectManager extends EncodingManager implements ProjectComponent, PersistentStateComponent<Element> {
  public static EncodingProjectManager getInstance(Project project) {
    return project.getComponent(EncodingProjectManager.class);
  }

  public abstract Map<VirtualFile, Charset> getAllMappings();

  public abstract void setMapping(Map<VirtualFile, Charset> result);

}
