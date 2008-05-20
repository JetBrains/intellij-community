/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 17, 2007
 * Time: 3:20:51 PM
 */
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.CharsetToolkit;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class EncodingManagerImpl extends EncodingManager {
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  @NonNls
  @NotNull
  public String getComponentName() {
    return "EncodingManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  @NotNull
  public Collection<Charset> getFavorites() {
    Set<Charset> result = new THashSet<Charset>();
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      result.addAll(EncodingProjectManager.getInstance(project).getFavorites());
    }
    return result;
  }

  public void setMapping(final Map<VirtualFile, Charset> map) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(project)).setMapping(map);
    }
  }

  @Nullable
  public Charset getEncoding(@Nullable VirtualFile virtualFile, boolean useParentDefaults) {
    Project project = guessProject(virtualFile);
    if (project == null) return null;
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(project);
    if (encodingManager == null) return null; //tests
    return encodingManager.getEncoding(virtualFile, useParentDefaults);
  }

  private static Project guessProject(final VirtualFile virtualFile) {
    Project project = ProjectLocator.getInstance().guessProjectForFile(virtualFile);
    if (project == null) {
      ProjectManager projectManager = ProjectManager.getInstance();
      if (projectManager == null) return null; //tests
      project = projectManager.getDefaultProject();
    }
    return project;
  }

  public void setEncoding(@Nullable VirtualFile virtualFileOrDir, @Nullable Charset charset) {
    Project project = guessProject(virtualFileOrDir);
    EncodingProjectManager.getInstance(project).setEncoding(virtualFileOrDir, charset);
  }

  public boolean isUseUTFGuessing(final VirtualFile virtualFile) {
    Project project = guessProject(virtualFile);
    return project != null && EncodingProjectManager.getInstance(project).isUseUTFGuessing(virtualFile);
  }

  public void setUseUTFGuessing(final VirtualFile virtualFile, final boolean useUTFGuessing) {
    Project project = guessProject(virtualFile);
    if (project == null) return;
    EncodingProjectManager.getInstance(project).setUseUTFGuessing(virtualFile, useUTFGuessing);
  }

  public boolean isNative2AsciiForPropertiesFiles(final VirtualFile virtualFile) {
    Project project = guessProject(virtualFile);
    return project != null && EncodingProjectManager.getInstance(project).isNative2AsciiForPropertiesFiles(virtualFile);
  }

  public void setNative2AsciiForPropertiesFiles(final VirtualFile virtualFile, final boolean native2Ascii) {
    Project project = guessProject(virtualFile);
    if (project == null) return;
    EncodingProjectManager.getInstance(project).setNative2AsciiForPropertiesFiles(virtualFile, native2Ascii);
  }

  public Charset getDefaultCharset() {
    Charset result = CharsetToolkit.getDefaultSystemCharset();
    // see SCR #5288
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      ProjectManager projectManager = ProjectManager.getInstance();
      EncodingProjectManager encodingManager = projectManager == null ? null : EncodingProjectManager.getInstance(projectManager.getDefaultProject());
      if (encodingManager != null) {
        result = encodingManager.getDefaultCharset();
      }
    }

    return result;
  }

  public Charset getDefaultCharsetForPropertiesFiles(@Nullable final VirtualFile virtualFile) {
    Project project = guessProject(virtualFile);
    if (project == null) return null;
    return EncodingProjectManager.getInstance(project).getDefaultCharsetForPropertiesFiles(virtualFile);
  }

  public void setDefaultCharsetForPropertiesFiles(@Nullable final VirtualFile virtualFile, final Charset charset) {
    Project project = guessProject(virtualFile);
    if (project == null) return;
    EncodingProjectManager.getInstance(project).setDefaultCharsetForPropertiesFiles(virtualFile, charset);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener){
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener){
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }
  void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
    myPropertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }
}
